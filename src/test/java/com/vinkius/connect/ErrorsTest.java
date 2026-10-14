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

import com.vinkius.connect.error.AuthException;
import com.vinkius.connect.error.NotFoundException;
import com.vinkius.connect.error.OverageException;
import com.vinkius.connect.error.QuotaException;
import com.vinkius.connect.error.RateLimitException;
import com.vinkius.connect.error.ValidationException;
import com.vinkius.connect.error.VinkiusException;
import com.vinkius.connect.fluent.Vinkius;
import com.vinkius.connect.helpers.FakeHttpSender;
import com.vinkius.connect.helpers.FakeHttpSender.MockResponse;
import com.vinkius.connect.helpers.FakeHttpSender.Route;
import com.vinkius.connect.helpers.MockSdk;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Error mapping contract (parity with {@code test/errors.test.ts}). */
class ErrorsTest {

  private static Route getUserWith(int status, Object body) {
    return Route.get("^/apps/vk_app_test/users/someuser$", call -> MockResponse.of(status, body));
  }

  private static Route getUserWith(int status, Object body, Map<String, String> headers) {
    return Route.get(
        "^/apps/vk_app_test/users/someuser$",
        call -> MockResponse.of(status, body).withHeaders(headers));
  }

  @Test
  void maps401ToAuthExceptionReadingTheErrorKey() {
    FakeHttpSender sender =
        FakeHttpSender.of(getUserWith(401, Map.of("error", "Unauthenticated.")));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.users().get("someuser"))
        .isInstanceOf(AuthException.class)
        .satisfies(
            error -> {
              AuthException auth = (AuthException) error;
              assertThat(auth.status()).isEqualTo(401);
              assertThat(auth.code().value()).isEqualTo("auth_error");
              assertThat(auth).hasMessage("Unauthenticated.");
            });
  }

  @Test
  void maps404ToNotFoundException() {
    FakeHttpSender sender = FakeHttpSender.of(getUserWith(404, Map.of("message", "Not found.")));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.users().get("someuser")).isInstanceOf(NotFoundException.class);
  }

  @Test
  void maps422ToValidationExceptionWithFieldErrors() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            getUserWith(
                422,
                Map.of(
                    "message", "Invalid", "errors", Map.of("external_id", List.of("required")))));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.users().get("someuser"))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            error -> {
              ValidationException validation = (ValidationException) error;
              assertThat(validation.errors()).containsEntry("external_id", List.of("required"));
            });
  }

  @Test
  void maps429McpShapedBodiesToQuotaExceptionWithUpgradeUrl() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            getUserWith(
                429,
                Map.of(
                    "content",
                    List.of(Map.of("type", "text", "text", "quota")),
                    "isError",
                    true,
                    "upgrade_url",
                    "https://u")));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.users().get("someuser"))
        .isInstanceOf(QuotaException.class)
        .satisfies(
            error -> assertThat(((QuotaException) error).upgradeUrl()).isEqualTo("https://u"));
  }

  @Test
  void mapsPlain429ToRateLimitException() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            getUserWith(429, Map.of("message", "slow down"), Map.of("retry-after", "1")));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.users().get("someuser"))
        .isInstanceOf(RateLimitException.class)
        .satisfies(
            error -> {
              RateLimitException rateLimit = (RateLimitException) error;
              assertThat(rateLimit.retryAfterMs()).isEqualTo(1000);
            });
  }

  @Test
  void maps402ToOverageException() {
    FakeHttpSender sender =
        FakeHttpSender.of(getUserWith(402, Map.of("isError", true, "upgrade_url", "https://u")));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.users().get("someuser"))
        .isInstanceOf(OverageException.class)
        .satisfies(
            error -> assertThat(((OverageException) error).upgradeUrl()).isEqualTo("https://u"));
  }

  @Test
  void maps500ToGenericApiErrorCarryingTheMessage() {
    FakeHttpSender sender = FakeHttpSender.of(getUserWith(500, Map.of("message", "boom")));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.users().get("someuser"))
        .isExactlyInstanceOf(VinkiusException.class)
        .satisfies(
            error -> {
              VinkiusException failure = (VinkiusException) error;
              assertThat(failure.code().value()).isEqualTo("api_error");
              assertThat(failure).hasMessage("boom");
            });
  }

  @Test
  void maps403ToAuthException() {
    FakeHttpSender sender = FakeHttpSender.of(getUserWith(403, Map.of("error", "Forbidden.")));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.users().get("someuser"))
        .isInstanceOf(AuthException.class)
        .satisfies(error -> assertThat(((AuthException) error).status()).isEqualTo(403));
  }

  @Test
  void maps429WithUpgradeUrlOnlyToQuotaException() {
    FakeHttpSender sender = FakeHttpSender.of(getUserWith(429, Map.of("upgrade_url", "https://u")));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.users().get("someuser"))
        .isInstanceOf(QuotaException.class)
        .satisfies(
            error -> assertThat(((QuotaException) error).upgradeUrl()).isEqualTo("https://u"));
  }

  @Test
  void maps429WithErrorFlagOnlyToQuotaExceptionWithoutUpgradeUrl() {
    FakeHttpSender sender = FakeHttpSender.of(getUserWith(429, Map.of("isError", true)));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.users().get("someuser"))
        .isInstanceOf(QuotaException.class)
        .satisfies(error -> assertThat(((QuotaException) error).upgradeUrl()).isNull());
  }

  @Test
  void mapsPlainStringErrorBodyToItsOwnText() {
    FakeHttpSender sender = FakeHttpSender.of(getUserWith(500, "socket hang up"));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.users().get("someuser"))
        .isInstanceOf(VinkiusException.class)
        .hasMessage("socket hang up");
  }

  @Test
  void fallsBackToTheStatusWhenTheBodyCarriesNoMessage() {
    FakeHttpSender sender = FakeHttpSender.of(getUserWith(500, Map.of("message", "", "error", "")));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.users().get("someuser"))
        .isInstanceOf(VinkiusException.class)
        .hasMessage("Request failed with status 500");
  }

  @Test
  void mapsNonListValidationErrorsToEmptyMessageList() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            getUserWith(422, Map.of("message", "Invalid", "errors", Map.of("name", "required"))));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.users().get("someuser"))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            error -> {
              ValidationException validation = (ValidationException) error;
              assertThat(validation.errors()).containsEntry("name", List.of());
            });
  }
}
