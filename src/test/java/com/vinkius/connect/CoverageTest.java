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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vinkius.connect.adapters.AdapterSupport;
import com.vinkius.connect.core.CancellationToken;
import com.vinkius.connect.core.Json;
import com.vinkius.connect.error.ConfigException;
import com.vinkius.connect.error.ConnectionException;
import com.vinkius.connect.error.NotImplementedException;
import com.vinkius.connect.error.ProtocolException;
import com.vinkius.connect.error.ValidationException;
import com.vinkius.connect.error.VinkiusException;
import com.vinkius.connect.fluent.Capability;
import com.vinkius.connect.fluent.CapabilitySet;
import com.vinkius.connect.fluent.Vinkius;
import com.vinkius.connect.helpers.FakeHttpSender;
import com.vinkius.connect.helpers.FakeHttpSender.MockResponse;
import com.vinkius.connect.helpers.FakeHttpSender.RecordedCall;
import com.vinkius.connect.helpers.FakeHttpSender.Route;
import com.vinkius.connect.helpers.Fixtures;
import com.vinkius.connect.helpers.MockSdk;
import com.vinkius.connect.model.CapabilityData;
import com.vinkius.connect.model.CapabilityResult;
import com.vinkius.connect.model.CatalogSearchResult;
import com.vinkius.connect.model.Connection;
import com.vinkius.connect.model.RequestOptions;
import com.vinkius.connect.resources.IssueTokenInput;
import com.vinkius.connect.resources.UpdateAppUserInput;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CoverageTest {

  private static Route connectionsRoute(List<Map<String, Object>> connections) {
    return Route.get(
        "^/apps/vk_app_test/users/customer-123/mcps$",
        call -> MockResponse.ok(Map.of("data", connections)));
  }

  private static Capability.CapabilityExecutor emptyExecutor() {
    return (rawName, args, options) ->
        CompletableFuture.completedFuture(CapabilityResult.of(List.of(), false));
  }

  private static Capability.BuildCapabilityContext buildContext(
      String connector, String connectionId) {
    return new Capability.BuildCapabilityContext(
        connector, connectionId, (c, n) -> c + "__" + n, emptyExecutor());
  }

  // ------------------------------------------------------------------ config validation

  @Test
  void rejectsInvalidBaseUrlOverride() {
    FakeHttpSender sender = FakeHttpSender.of();

    assertThatThrownBy(() -> MockSdk.make(sender, options -> options.baseUrl("not a url")))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Invalid baseUrl");
  }

  @Test
  void warnsOnInsecureHttpBaseUrlForNonLocalHosts() {
    Route route =
        Route.get(
            "^/apps/vk_app_test/users/someuser$",
            call -> MockResponse.ok(Map.of("data", Fixtures.appUser("u1", "someuser"))));
    FakeHttpSender sender = FakeHttpSender.of(route);
    PrintStream originalErr = System.err;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    try {
      System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
      Vinkius vinkius = MockSdk.make(sender, options -> options.baseUrl("http://api.example.com"));

      vinkius.users().get("someuser");
    } finally {
      System.setErr(originalErr);
    }

    assertThat(captured.toString(StandardCharsets.UTF_8)).contains("insecure");
  }

  @Test
  void normalizesTrailingSlashesInBaseUrlBeforeRouting() {
    Route route =
        Route.get(
            "^/apps/vk_app_test/users/someuser$",
            call -> MockResponse.ok(Map.of("data", Fixtures.appUser("u1", "someuser"))));
    FakeHttpSender sender = FakeHttpSender.of(route);
    Vinkius vinkius = MockSdk.make(sender, options -> options.baseUrl("http://localhost:8080///"));

    vinkius.users().get("someuser");

    RecordedCall call = sender.call(0);
    assertThat(call.path()).startsWith("/apps/").doesNotStartWith("//");
  }

  @Test
  void appendsCustomUserAgentToOutgoingRequests() {
    Route route =
        Route.get(
            "^/apps/vk_app_test/users/someuser$",
            call -> MockResponse.ok(Map.of("data", Fixtures.appUser("u1", "someuser"))));
    FakeHttpSender sender = FakeHttpSender.of(route);
    Vinkius vinkius = MockSdk.make(sender, options -> options.userAgent("acme-ai/1.0"));

    vinkius.users().get("someuser");

    assertThat(sender.call(0).header("user-agent")).contains("acme-ai/1.0");
  }

  // ------------------------------------------------------------- buildCapability guards

  @Test
  void rejectsCapabilityPayloadWithoutUsableName() {
    CapabilityData missingName = Json.convert(Map.of(), CapabilityData.class);
    CapabilityData blankName = new CapabilityData("   ", null, null, null, null, null, null);

    assertThatThrownBy(() -> Capability.build(missingName, buildContext("github", "conn_1")))
        .isInstanceOf(ProtocolException.class);
    assertThatThrownBy(() -> Capability.build(blankName, buildContext("github", "conn_1")))
        .isInstanceOf(ProtocolException.class);
  }

  @Test
  void rejectsCapabilityPayloadWithoutRoutingMetadata() {
    CapabilityData data = new CapabilityData("ok", null, null, null, null, null, null);

    assertThatThrownBy(() -> Capability.build(data, buildContext("", "conn_1")))
        .isInstanceOf(ProtocolException.class);
    assertThatThrownBy(() -> Capability.build(data, buildContext("github", "")))
        .isInstanceOf(ProtocolException.class);
  }

  @Test
  void fallsBackToEmptySchemaAndNormalizeParamsFillsEnvelope() {
    Capability capability =
        Capability.build(
            new CapabilityData("no_schema", null, null, null, null, null, null),
            buildContext("github", "conn_1"));

    assertThat(capability.inputSchema()).isEmpty();
    assertThat(AdapterSupport.normalizeParams(null))
        .isEqualTo(Map.of("type", "object", "properties", Map.of()));
  }

  // ---------------------------------------------------------------- resource methods

  @Test
  void updatesAndDeletesUser() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.patch(
                "^/apps/vk_app_test/users/u1$",
                call -> MockResponse.ok(Map.of("data", Fixtures.appUser("u1", "u1")))),
            Route.delete("^/apps/vk_app_test/users/u1$", call -> MockResponse.ok(Map.of())));
    Vinkius vinkius = MockSdk.make(sender);

    var updated = vinkius.users().update("u1", new UpdateAppUserInput("disabled", null));
    vinkius.users().delete("u1");

    assertThat(updated.id()).isEqualTo("u1");
    assertThat(sender.calls()).extracting(RecordedCall::method).containsExactly("PATCH", "DELETE");
  }

  @Test
  void getsAndDeletesConnection() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/apps/vk_app_test/users/customer-123/mcps/conn_9$",
                call -> MockResponse.ok(Map.of("data", Fixtures.connection("conn_9", "github")))),
            Route.delete(
                "^/apps/vk_app_test/users/customer-123/mcps/conn_9$",
                call -> MockResponse.ok(Map.of())));
    Vinkius vinkius = MockSdk.make(sender);

    Connection connection = vinkius.users().connections(Fixtures.USER).get("conn_9");
    vinkius.users().connections(Fixtures.USER).delete("conn_9");

    assertThat(connection.id()).isEqualTo("conn_9");
    assertThat(sender.calls()).extracting(RecordedCall::method).containsExactly("GET", "DELETE");
  }

  @Test
  void readsAndSetsCredentialsThroughFluentHandle() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(List.of(Fixtures.connection("conn_1", "github", true, "active"))),
            Route.get(
                "^/apps/vk_app_test/users/customer-123/mcps/conn_1/credentials$",
                call ->
                    MockResponse.ok(
                        Map.of(
                            "schema", Map.of("api_key", Map.of("type", "api_key")),
                            "configured", Map.of("api_key", true)))),
            Route.put(
                "^/apps/vk_app_test/users/customer-123/mcps/conn_1/credentials$",
                call ->
                    MockResponse.ok(
                        Map.of("schema", Map.of(), "configured", Map.of("api_key", true)))));
    Vinkius vinkius = MockSdk.make(sender);
    var handle = vinkius.user(Fixtures.USER).connector("github").credentials();

    var status = handle.status();
    var setStatus = handle.set(Map.of("api_key", "secret"));

    assertThat(status.configured()).containsEntry("api_key", true);
    assertThat(setStatus.configured()).containsEntry("api_key", true);
    RecordedCall put =
        sender.calls().stream()
            .filter(call -> "PUT".equals(call.method()))
            .findFirst()
            .orElseThrow();
    assertThat(put.body()).isEqualTo(Map.of("credentials", Map.of("api_key", "secret")));
  }

  @Test
  void issuesTokenWithExplicitName() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.post(
                "^/apps/vk_app_test/users/customer-123/mcps/conn_1/tokens$",
                call ->
                    MockResponse.ok(
                        Map.of(
                            "data",
                            Map.of(
                                "id", "tok_1",
                                "name", "agent",
                                "is_enabled", true,
                                "created_at", "x")))));
    Vinkius vinkius = MockSdk.make(sender);

    var issued =
        vinkius
            .users()
            .connections(Fixtures.USER)
            .tokens("conn_1")
            .issue(new IssueTokenInput("agent"));

    assertThat(issued.id()).isEqualTo("tok_1");
    assertThat(sender.call(0).body()).isEqualTo(Map.of("name", "agent"));
  }

  @Test
  void fetchesUnderlyingUserResource() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/apps/vk_app_test/users/customer-123$",
                call -> MockResponse.ok(Map.of("data", Fixtures.appUser("u1", "customer-123")))));
    Vinkius vinkius = MockSdk.make(sender);

    var user = vinkius.user(Fixtures.USER).get();

    assertThat(user.id()).isEqualTo("u1");
  }

  @Test
  void normalizesMarketplaceSearchIntoPagedResult() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/marketplace/search$",
                call -> {
                  if ("none".equals(call.queryParam("q"))) {
                    return MockResponse.ok(
                        Map.of("results", List.of(), "has_more", false, "page", 1));
                  }
                  return MockResponse.ok(
                      Map.of(
                          "results",
                          List.of(
                              Map.of(
                                  "id", "slack",
                                  "slug", "slack",
                                  "title", "Slack",
                                  "publisher_type", "first_party",
                                  "listing_type", "mcp",
                                  "server_type", "mcp",
                                  "requires_auth", true)),
                          "has_more",
                          false,
                          "page",
                          1));
                }));
    Vinkius vinkius = MockSdk.make(sender);

    CatalogSearchResult empty = vinkius.catalog().search("none");
    assertThat(empty.items()).isEmpty();
    assertThat(empty.hasMore()).isFalse();
    assertThat(empty.page()).isEqualTo(1);

    CatalogSearchResult found = vinkius.catalog().search("slack");
    assertThat(found.items()).hasSize(1);
    assertThat(found.items().get(0).listingType()).isEqualTo("mcp");
    assertThat(found.items().get(0).requiresBuyerAuth()).isTrue();
  }

  // ------------------------------------------------------------------ connector handle

  @Test
  void disconnectsByDeletingResolvedConnection() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(List.of(Fixtures.connection("conn_1", "github", true, "active"))),
            Route.delete(
                "^/apps/vk_app_test/users/customer-123/mcps/conn_1$",
                call -> MockResponse.ok(Map.of())));
    Vinkius vinkius = MockSdk.make(sender);

    vinkius.user(Fixtures.USER).connector("github").disconnect();

    assertThat(sender.calls())
        .anyMatch(call -> "DELETE".equals(call.method()) && call.path().endsWith("/mcps/conn_1"));
  }

  @Test
  void mintsAtMostOneTokenPerHandleAcrossRepeatedListings() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(List.of(Fixtures.connection("conn_1", "github", true, "active"))),
            Fixtures.tokenRoute("conn_1"),
            Fixtures.runtimeRoute());
    Vinkius vinkius = MockSdk.make(sender);
    var handle = vinkius.user(Fixtures.USER).connector("github");

    handle.capabilities();
    handle.capabilities();

    long tokenCalls =
        sender.calls().stream().filter(call -> call.path().endsWith("/tokens")).count();
    assertThat(tokenCalls).isEqualTo(1);
  }

  @Test
  void throwsProtocolErrorWhenTokenMintingReturnsNoMcpUrl() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(List.of(Fixtures.connection("conn_1", "github", true, "active"))),
            Route.post(
                "^/apps/vk_app_test/users/customer-123/mcps/conn_1/tokens$",
                call ->
                    MockResponse.ok(
                        Map.of(
                            "data",
                            Map.of(
                                "id", "tok_1",
                                "name", "x",
                                "is_enabled", true,
                                "created_at", "x")))));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.user(Fixtures.USER).connector("github").capabilities())
        .isInstanceOf(ProtocolException.class)
        .hasMessageContaining("mcp_url");
  }

  // ------------------------------------------------------------------ transport failures

  @Test
  void retriesNetworkFailureAndSucceedsOnNextAttempt() {
    AtomicInteger attempts = new AtomicInteger();
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/apps/vk_app_test/users/someuser$",
                call -> {
                  if (attempts.getAndIncrement() == 0) {
                    throw new IllegalStateException("ECONNRESET");
                  }
                  return MockResponse.ok(Map.of("data", Fixtures.appUser("x", "someuser")));
                }));
    Vinkius vinkius = MockSdk.make(sender, options -> options.maxRetries(2));

    var user = vinkius.users().get("someuser");

    assertThat(user.id()).isEqualTo("x");
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void mapsNetworkFailureWithoutRetriesToConnectionException() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/apps/vk_app_test/users/someuser$",
                call -> {
                  throw new IllegalStateException("ECONNRESET");
                }));
    Vinkius vinkius = MockSdk.make(sender, options -> options.maxRetries(0));

    assertThrows(ConnectionException.class, () -> vinkius.users().get("someuser"));
  }

  @Test
  void reportsCallerAbortThatFiresDuringBodyRead() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/apps/vk_app_test/users/someuser$",
                call ->
                    MockResponse.ok(Map.of("data", Fixtures.appUser("x", "someuser")))
                        .withDelay(500)));
    Vinkius vinkius = MockSdk.make(sender, options -> options.maxRetries(0));
    CancellationToken token = new CancellationToken();
    Thread canceller =
        new Thread(
            () -> {
              try {
                Thread.sleep(20);
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
              }
              token.cancel();
            },
            "test-canceller");
    canceller.setDaemon(true);
    canceller.start();
    RequestOptions options = RequestOptions.builder().cancellation(token).build();

    ConnectionException error =
        assertThrows(ConnectionException.class, () -> vinkius.users().get("someuser", options));

    assertThat(error.getMessage()).contains("aborted by caller");
  }

  // ------------------------------------------------------------------- error mapping gaps

  @Test
  void exposesNotImplementedWithStableCode() {
    NotImplementedException error = new NotImplementedException("coming soon");

    assertThat(error.code().value()).isEqualTo("not_implemented");
    assertThat(error.status()).isEqualTo(0);
  }

  @Test
  void maps422WithoutErrorsFieldToEmptyErrorsRecord() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/apps/vk_app_test/users/someuser$",
                call -> MockResponse.of(422, Map.of("message", "Invalid"))));
    Vinkius vinkius = MockSdk.make(sender);

    ValidationException error =
        assertThrows(ValidationException.class, () -> vinkius.users().get("someuser"));

    assertThat(error.errors()).isEmpty();
  }

  @Test
  void picksMessageFromMcpContentArrayWhenAbsent() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/apps/vk_app_test/users/someuser$",
                call ->
                    MockResponse.of(
                        400,
                        Map.of(
                            "content", List.of(Map.of("type", "text", "text", "from content"))))));
    Vinkius vinkius = MockSdk.make(sender);

    VinkiusException error =
        assertThrows(VinkiusException.class, () -> vinkius.users().get("someuser"));

    assertThat(error.getMessage()).isEqualTo("from content");
  }

  // ------------------------------------------------------------------ CapabilitySet helpers

  @Test
  void capabilitySetForConnectorFiltersBySlug() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(List.of(Fixtures.connection("conn_1", "github", true, "active"))),
            Fixtures.tokenRoute("conn_1"),
            Fixtures.runtimeRoute());
    Vinkius vinkius = MockSdk.make(sender);
    CapabilitySet capabilities = vinkius.user(Fixtures.USER).capabilities();

    assertThat(capabilities.forConnector("github")).hasSize(capabilities.size());
    assertThat(capabilities.forConnector("nope")).isEmpty();
  }
}
