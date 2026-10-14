/*
 * Copyright (c) Vinkius
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vinkius.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vinkius.connect.adapters.JsonSchemaAdapter;
import com.vinkius.connect.core.CancellationToken;
import com.vinkius.connect.core.Futures;
import com.vinkius.connect.core.HttpClient;
import com.vinkius.connect.core.HttpSender;
import com.vinkius.connect.core.RetryPolicy;
import com.vinkius.connect.error.ConfigException;
import com.vinkius.connect.error.ConnectionException;
import com.vinkius.connect.error.NotFoundException;
import com.vinkius.connect.error.VinkiusErrorCode;
import com.vinkius.connect.error.VinkiusException;
import com.vinkius.connect.fluent.Vinkius;
import com.vinkius.connect.fluent.VinkiusOptions;
import com.vinkius.connect.helpers.FakeHttpSender;
import com.vinkius.connect.helpers.FakeHttpSender.MockResponse;
import com.vinkius.connect.helpers.FakeHttpSender.Route;
import com.vinkius.connect.helpers.Fixtures;
import com.vinkius.connect.helpers.MockSdk;
import com.vinkius.connect.model.AppUser;
import com.vinkius.connect.model.Hooks;
import com.vinkius.connect.model.RequestOptions;
import com.vinkius.connect.resources.ConnectionTokensClient;
import com.vinkius.connect.resources.CreateAppUserInput;
import com.vinkius.connect.resources.IssueTokenInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Transport contract (parity with {@code test/http.test.ts}). */
class HttpTest {

  // ── config validation ─────────────────────────────────────────────────────

  @Test
  void rejectsNonPublicAppId() {
    assertThatThrownBy(
            () -> new Vinkius(VinkiusOptions.builder("vk_app_sk_x", "vk_app_sk_x").build()))
        .isInstanceOf(ConfigException.class);
  }

  @Test
  void rejectsNonSecretApiKey() {
    assertThatThrownBy(() -> new Vinkius(VinkiusOptions.builder("vk_app_x", "nope").build()))
        .isInstanceOf(ConfigException.class);
  }

  @Test
  void rejectsVinkiusInternalUserIdPassedAsExternalId() {
    Vinkius vinkius = new Vinkius(VinkiusOptions.builder("vk_app_x", "vk_app_sk_x").build());
    assertThatThrownBy(() -> vinkius.user("vk_app_user_abc123"))
        .isInstanceOf(ConfigException.class);
  }

  // ── HttpClient behavior ───────────────────────────────────────────────────

  @Test
  void retriesIdempotentRequestsOn503ThenSucceeds() {
    AtomicInteger attempts = new AtomicInteger();
    Route route =
        Route.get(
            "^/apps/vk_app_test/users/someuser$",
            call -> {
              int attempt = attempts.incrementAndGet();
              return attempt < 2
                  ? MockResponse.of(503, Map.of("message", "unavailable"))
                  : MockResponse.ok(Map.of("data", Map.of("id", "x")));
            });
    Vinkius vinkius = MockSdk.make(FakeHttpSender.of(route), b -> b.maxRetries(2));

    AppUser user = vinkius.users().get("someuser");

    assertThat(user.id()).isEqualTo("x");
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void timesOutSlowRequestsAsConnectionError() {
    Route route =
        Route.get(
            "^/apps/vk_app_test/users/someuser$",
            call -> MockResponse.ok(Map.of("data", Map.of("id", "x"))).withDelay(200));
    Vinkius vinkius = MockSdk.make(FakeHttpSender.of(route), b -> b.timeoutMs(20).maxRetries(0));

    assertThatThrownBy(() -> vinkius.users().get("someuser"))
        .isInstanceOf(VinkiusException.class)
        .satisfies(
            error ->
                assertThat(((VinkiusException) error).code())
                    .isEqualTo(VinkiusErrorCode.CONNECTION_ERROR));
  }

  @Test
  void redactsAuthorizationHeaderBeforeItReachesHooks() {
    List<Map<String, String>> seen = new ArrayList<>();
    Hooks hooks =
        new Hooks() {
          @Override
          public void onRequest(RequestInfo info) {
            seen.add(info.headers());
          }
        };
    Route route =
        Route.get(
            "^/apps/vk_app_test/users/someuser$",
            call -> MockResponse.ok(Map.of("data", Map.of("id", "x"))));
    Vinkius vinkius = MockSdk.make(FakeHttpSender.of(route), b -> b.hooks(hooks));

    vinkius.users().get("someuser");

    assertThat(seen).isNotEmpty();
    assertThat(headerValue(seen.get(0), "authorization")).isEqualTo("[REDACTED]");
  }

  @Test
  void reportsZeroBasedAttemptToHooksWhenRequestIsRetried() {
    List<Integer> attempts = new ArrayList<>();
    AtomicInteger count = new AtomicInteger();
    Hooks hooks =
        new Hooks() {
          @Override
          public void onResponse(ResponseInfo info) {
            attempts.add(info.attempt() != null ? info.attempt() : -1);
          }
        };
    Route route =
        Route.get(
            "^/apps/vk_app_test/users/someuser$",
            call -> {
              int attempt = count.incrementAndGet();
              return attempt < 2
                  ? MockResponse.of(503, Map.of())
                  : MockResponse.ok(Map.of("data", Map.of("id", "x")));
            });
    Vinkius vinkius = MockSdk.make(FakeHttpSender.of(route), b -> b.maxRetries(2).hooks(hooks));

    vinkius.users().get("someuser");

    assertThat(attempts).containsExactly(0, 1);
  }

  @Test
  void timesOutAStalledSendAsConnectionError() {
    // Sender that accepts the request and never completes — the transport deadline is the
    // backstop (the Java counterpart of a fetch whose body never closes).
    HttpSender stalled = request -> new CompletableFuture<>();
    Vinkius vinkius =
        new Vinkius(
            VinkiusOptions.builder(MockSdk.APP_ID, MockSdk.API_KEY)
                .maxRetries(0)
                .sender(stalled)
                .baseUrl(MockSdk.BASE_URL)
                .timeoutMs(50)
                .build());

    assertThatThrownBy(() -> vinkius.users().get("someuser"))
        .isInstanceOf(VinkiusException.class)
        .satisfies(
            error ->
                assertThat(((VinkiusException) error).code())
                    .isEqualTo(VinkiusErrorCode.CONNECTION_ERROR));
  }

  @Test
  void abortsWhenTheCallerSignalFires() {
    Route route =
        Route.get(
            "^/apps/vk_app_test/users/someuser$", call -> MockResponse.ok(Map.of()).withDelay(500));
    Vinkius vinkius = MockSdk.make(FakeHttpSender.of(route));
    CancellationToken token = new CancellationToken();

    CompletableFuture<AppUser> pending =
        vinkius.users().getAsync("someuser", RequestOptions.builder().cancellation(token).build());
    token.cancel();

    assertThatThrownBy(() -> Futures.join(pending)).isInstanceOf(ConnectionException.class);
  }

  @Test
  void retriesIdempotentCreateCarryingIdempotencyKey() {
    AtomicInteger attempts = new AtomicInteger();
    Route route =
        Route.post(
            "^/apps/vk_app_test/users$",
            call -> {
              int attempt = attempts.incrementAndGet();
              if (attempt < 2) {
                return MockResponse.of(503, Map.of("message", "unavailable"));
              }
              Object maybeBody = call.body();
              @SuppressWarnings("unchecked")
              Map<String, Object> requestBody =
                  maybeBody instanceof Map ? (Map<String, Object>) maybeBody : Map.of();
              return MockResponse.ok(
                  Map.of("data", Map.of("id", "x", "external_id", requestBody.get("external_id"))));
            });
    FakeHttpSender sender = FakeHttpSender.of(route);
    Vinkius vinkius = MockSdk.make(sender, b -> b.maxRetries(2));

    AppUser result =
        vinkius
            .users()
            .create(
                CreateAppUserInput.of("usr_1"),
                RequestOptions.builder().idempotencyKey("key-123").build());

    assertThat(result.id()).isEqualTo("x");
    assertThat(attempts.get()).isEqualTo(2);
    assertThat(sender.calls())
        .allSatisfy(call -> assertThat(call.header("idempotency-key")).isEqualTo("key-123"));
  }

  // ── Idempotency-Key safety ────────────────────────────────────────────────

  @Test
  void rejectsEmptyIdempotencyKey() {
    Vinkius vinkius =
        MockSdk.make(
            FakeHttpSender.of(
                Route.get(
                    "^/apps/vk_app_test/users/someuser$",
                    call -> MockResponse.ok(Map.of("data", Map.of("id", "x"))))));

    assertThatThrownBy(
            () ->
                vinkius
                    .users()
                    .get("someuser", RequestOptions.builder().idempotencyKey("").build()))
        .isInstanceOf(ConfigException.class);
  }

  @Test
  void rejectsWhitespaceOnlyIdempotencyKey() {
    Vinkius vinkius =
        MockSdk.make(
            FakeHttpSender.of(
                Route.get(
                    "^/apps/vk_app_test/users/someuser$",
                    call -> MockResponse.ok(Map.of("data", Map.of("id", "x"))))));

    assertThatThrownBy(
            () ->
                vinkius
                    .users()
                    .get("someuser", RequestOptions.builder().idempotencyKey("   ").build()))
        .isInstanceOf(ConfigException.class);
  }

  @Test
  void neverAutoRetriesANonIdempotentTokenIssueEvenWithKey() {
    AtomicInteger attempts = new AtomicInteger();
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.post(
                "^/apps/vk_app_test/users/customer-123/mcps/conn_1/tokens$",
                call -> {
                  attempts.incrementAndGet();
                  // Transient 503 would be retried for an idempotent request.
                  return MockResponse.of(503, Map.of("message", "unavailable"));
                }));
    HttpClient http =
        new HttpClient(
            new HttpClient.Config(
                MockSdk.BASE_URL,
                "vk_app_sk_test",
                MockSdk.APP_ID,
                5000L,
                new RetryPolicy(3, 1, 2),
                sender,
                "test",
                null));
    ConnectionTokensClient tokens =
        new ConnectionTokensClient(http, MockSdk.APP_ID, Fixtures.USER, "conn_1");

    // `tokens.issue` is non-idempotent: supplying a key must NOT arm a transport retry that
    // would mint a duplicate token.
    assertThatThrownBy(
            () ->
                tokens.issue(
                    IssueTokenInput.unnamed(),
                    RequestOptions.builder().idempotencyKey("tok-key").build()))
        .isInstanceOf(VinkiusException.class)
        .satisfies(
            error ->
                assertThat(((VinkiusException) error).code())
                    .isEqualTo(VinkiusErrorCode.API_ERROR));
    assertThat(attempts.get()).isEqualTo(1);
    assertThat(sender.call(0).header("idempotency-key")).isEqualTo("tok-key");
  }

  // ── adapter dispatch ──────────────────────────────────────────────────────

  @Test
  void throwsTypedNotFoundForUnknownCapability() {
    assertThatThrownBy(() -> JsonSchemaAdapter.executeByName(List.of(), "nope", null))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Unknown capability: nope");
  }

  private static @Nullable String headerValue(Map<String, String> headers, String name) {
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if (entry.getKey().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
        return entry.getValue();
      }
    }
    return null;
  }
}
