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

import com.vinkius.connect.core.Json;
import com.vinkius.connect.error.ConnectorNotConnectedException;
import com.vinkius.connect.helpers.FakeHttpSender;
import com.vinkius.connect.helpers.FakeHttpSender.MockResponse;
import com.vinkius.connect.helpers.FakeHttpSender.RecordedCall;
import com.vinkius.connect.helpers.FakeHttpSender.Route;
import com.vinkius.connect.helpers.Fixtures;
import com.vinkius.connect.helpers.MockSdk;
import com.vinkius.connect.model.Connection;
import com.vinkius.connect.model.ConnectorStatus;
import com.vinkius.connect.model.RequestOptions;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ConnectorTest {

  private static Route listRoute(List<Map<String, Object>> connections) {
    return Route.get(
        "^/apps/vk_app_test/users/customer-123/mcps$",
        call -> MockResponse.ok(Map.of("data", connections)));
  }

  private static Route createRoute() {
    return Route.post(
        "^/apps/vk_app_test/users/customer-123/mcps$",
        call -> MockResponse.ok(Map.of("data", Fixtures.connection("conn_1", "github"))));
  }

  @Test
  void connectProvisionsRuntimeTokenAndReturnsRuntimeUrl() {
    FakeHttpSender sender = FakeHttpSender.of(createRoute(), Fixtures.tokenRoute());
    Vinkius vinkius = MockSdk.make(sender);
    Connector github = vinkius.user(Fixtures.USER).connector("github");

    Connection result = github.connect();

    assertThat(result.id()).isEqualTo("conn_1");
    assertThat(result.slug()).isEqualTo("github");
    // The connection now carries its runtime endpoint (embeds the vk_live_* token).
    assertThat(result.runtimeUrl()).isEqualTo(Fixtures.RUNTIME_MCP_URL);
    // Two calls: create the connection, then mint its data-plane token.
    assertThat(sender.calls())
        .extracting(RecordedCall::path)
        .containsExactly(
            "/apps/vk_app_test/users/customer-123/mcps",
            "/apps/vk_app_test/users/customer-123/mcps/conn_1/tokens");
  }

  @Test
  void derivesStatusFromConnectionReady() {
    Vinkius ready =
        MockSdk.make(
            FakeHttpSender.of(
                listRoute(List.of(Fixtures.connection("conn_1", "github", true, "active")))));
    assertThat(ready.user(Fixtures.USER).connector("github").status())
        .isEqualTo(ConnectorStatus.READY);

    Vinkius needs =
        MockSdk.make(
            FakeHttpSender.of(
                listRoute(List.of(Fixtures.connection("conn_1", "github", false, "active")))));
    assertThat(needs.user(Fixtures.USER).connector("github").status())
        .isEqualTo(ConnectorStatus.NEEDS_CREDENTIALS);

    Vinkius disabled =
        MockSdk.make(
            FakeHttpSender.of(
                listRoute(List.of(Fixtures.connection("conn_1", "github", true, "suspended")))));
    assertThat(disabled.user(Fixtures.USER).connector("github").status())
        .isEqualTo(ConnectorStatus.DISABLED);

    Vinkius missing = MockSdk.make(FakeHttpSender.of(listRoute(List.of())));
    assertThat(missing.user(Fixtures.USER).connector("slack").status())
        .isEqualTo(ConnectorStatus.NOT_CONNECTED);
  }

  @Test
  void clearsMemoizedConnectionIdWhenStatusNoLongerFindsConnection() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            createRoute(), Fixtures.tokenRoute(), listRoute(List.of()), listRoute(List.of()));
    Vinkius vinkius = MockSdk.make(sender);
    Connector github = vinkius.user(Fixtures.USER).connector("github");

    github.connect();

    assertThat(github.status()).isEqualTo(ConnectorStatus.NOT_CONNECTED);
    assertThatThrownBy(github::capabilities).isInstanceOf(ConnectorNotConnectedException.class);
  }

  @Test
  void listsCapabilitiesFromRuntimeAfterResolvingConnection() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            listRoute(List.of(Fixtures.connection("conn_1", "github"))),
            Fixtures.tokenRoute(),
            Fixtures.runtimeRoute());
    Vinkius vinkius = MockSdk.make(sender);

    CapabilitySet capabilities = vinkius.user(Fixtures.USER).connector("github").capabilities();

    assertThat(capabilities.size()).isEqualTo(1);
    Capability first = capabilities.get(0);
    assertThat(first.connector()).isEqualTo("github");
    assertThat(first.connectionId()).isEqualTo("conn_1");
    assertThat(first.rawName()).isEqualTo("create_issue");
    assertThat(first.name()).isEqualTo("github__create_issue");
    // Resolve the connection (API), mint the token (API), then list tools (runtime).
    assertThat(sender.calls())
        .extracting(RecordedCall::path)
        .containsExactly(
            "/apps/vk_app_test/users/customer-123/mcps",
            "/apps/vk_app_test/users/customer-123/mcps/conn_1/tokens",
            "/vk_live_test/mcp");
  }

  @Test
  void throwsNotConnectedWhenListingCapabilitiesOfUnconnectedConnector() {
    FakeHttpSender sender = FakeHttpSender.of(listRoute(List.of()));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.user(Fixtures.USER).connector("github").capabilities())
        .isInstanceOf(ConnectorNotConnectedException.class);
  }

  @Test
  void sharesSingleTokenMintAcrossConcurrentCapabilityResolutions() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            listRoute(List.of(Fixtures.connection("conn_1", "github"))),
            Fixtures.tokenRoute(),
            Fixtures.runtimeRoute());
    Vinkius vinkius = MockSdk.make(sender);
    Connector github = vinkius.user(Fixtures.USER).connector("github");

    CapabilitySet a = github.capabilitiesAsync(RequestOptions.EMPTY).join();
    CapabilitySet b = github.capabilitiesAsync(RequestOptions.EMPTY).join();

    assertThat(a.size()).isEqualTo(1);
    assertThat(b.size()).isEqualTo(1);
    // Parallel callers share the in-flight provisioning: exactly one mint.
    long tokenCalls =
        sender.calls().stream().filter(call -> call.path().endsWith("/tokens")).count();
    assertThat(tokenCalls).isEqualTo(1);
  }

  @Test
  void unwrapsJsonRpcResultsFromSseFrames() {
    Route sseRuntime =
        Route.post(
            Fixtures.RUNTIME_MCP_PATH,
            call -> {
              Map<String, Object> rpc = castBody(call.body());
              Object rpcId = rpc.getOrDefault("id", -1);
              String keepAlive = "data: {\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{\"tools\":[]}}";
              String frame =
                  Json.write(
                      Map.of(
                          "jsonrpc",
                          "2.0",
                          "id",
                          rpcId,
                          "result",
                          Map.of("tools", List.of(Fixtures.runtimeTool()))));
              // The spec allows `data:` with OR without the space after the colon.
              return MockResponse.ok(keepAlive + "\n\ndata:" + frame + "\n\n")
                  .withHeader("content-type", "text/event-stream");
            });
    FakeHttpSender sender =
        FakeHttpSender.of(
            listRoute(List.of(Fixtures.connection("conn_1", "github"))),
            Fixtures.tokenRoute(),
            sseRuntime);
    Vinkius vinkius = MockSdk.make(sender);

    CapabilitySet capabilities = vinkius.user(Fixtures.USER).connector("github").capabilities();

    assertThat(capabilities.size()).isEqualTo(1);
    assertThat(capabilities.get(0).rawName()).isEqualTo("create_issue");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castBody(@Nullable Object body) {
    Objects.requireNonNull(body, "body");
    return (Map<String, Object>) body;
  }
}
