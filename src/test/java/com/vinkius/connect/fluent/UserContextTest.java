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
package com.vinkius.connect.fluent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vinkius.connect.error.ConfigException;
import com.vinkius.connect.helpers.FakeHttpSender;
import com.vinkius.connect.helpers.FakeHttpSender.MockResponse;
import com.vinkius.connect.helpers.FakeHttpSender.RecordedCall;
import com.vinkius.connect.helpers.FakeHttpSender.Route;
import com.vinkius.connect.helpers.Fixtures;
import com.vinkius.connect.helpers.MockSdk;
import com.vinkius.connect.model.Connection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class UserContextTest {

  @Test
  void makesNoRequestWhenCreatingUserHandle() {
    FakeHttpSender sender = FakeHttpSender.of();
    Vinkius vinkius = MockSdk.make(sender);

    vinkius.user("customer-123");

    assertThat(sender.callCount()).isZero();
  }

  @Test
  void ensureUpsertsByExternalIdAndForwardsMetadata() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.post(
                "^/apps/vk_app_test/users$",
                call -> {
                  Map<String, Object> body = castBody(call.body());
                  return MockResponse.ok(
                      Map.of(
                          "data",
                          Fixtures.appUser(
                              "vk_app_user_1", String.valueOf(body.get("external_id")))));
                }));
    Vinkius vinkius = MockSdk.make(sender);

    vinkius.user("usr_42").ensure(Map.of("plan", "pro"));

    assertThat(sender.call(0).body())
        .isEqualTo(Map.of("external_id", "usr_42", "metadata", Map.of("plan", "pro")));
  }

  @Test
  void connectsConnectorByExternalIdThenProvisionsRuntimeToken() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.post(
                "^/apps/vk_app_test/users/customer-123/mcps$",
                call -> MockResponse.ok(Map.of("data", Fixtures.connection("conn_1", "github")))),
            Fixtures.tokenRoute());
    Vinkius vinkius = MockSdk.make(sender);

    Connection result = vinkius.user("customer-123").connector("github").connect();

    assertThat(result.id()).isEqualTo("conn_1");
    assertThat(result.runtimeUrl()).isEqualTo(Fixtures.RUNTIME_MCP_URL);
    // Two requests: create the connection (external_id goes straight into the
    // path), then mint the connection's data-plane token.
    assertThat(sender.calls())
        .extracting(RecordedCall::path)
        .containsExactly(
            "/apps/vk_app_test/users/customer-123/mcps",
            "/apps/vk_app_test/users/customer-123/mcps/conn_1/tokens");
    // The wire body maps the connector slug to the API field.
    assertThat(sender.call(0).body()).isEqualTo(Map.of("catalog_mcp_id", "github"));
  }

  @Test
  void sendsAuthorizationAndAppIdHeaderOnEveryRequest() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/apps/vk_app_test/users/customer-123/mcps$",
                call -> MockResponse.ok(Map.of("data", List.of()))));
    Vinkius vinkius = MockSdk.make(sender);

    vinkius.user("customer-123").connectors();

    RecordedCall call = sender.call(0);
    assertThat(call.header("authorization")).isEqualTo("Bearer vk_app_sk_test");
    assertThat(call.header("x-vinkius-app-id")).isEqualTo("vk_app_test");
  }

  @Test
  void rejectsExternalIdContainingSlash() {
    FakeHttpSender sender = FakeHttpSender.of();
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.user("acme/alice"))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("externalId");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castBody(@Nullable Object body) {
    Objects.requireNonNull(body, "body");
    return (Map<String, Object>) body;
  }
}
