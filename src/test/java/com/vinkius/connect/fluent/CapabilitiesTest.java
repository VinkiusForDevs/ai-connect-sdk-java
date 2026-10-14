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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vinkius.connect.core.Json;
import com.vinkius.connect.error.VinkiusException;
import com.vinkius.connect.helpers.FakeHttpSender;
import com.vinkius.connect.helpers.FakeHttpSender.MockResponse;
import com.vinkius.connect.helpers.FakeHttpSender.RecordedCall;
import com.vinkius.connect.helpers.FakeHttpSender.Route;
import com.vinkius.connect.helpers.Fixtures;
import com.vinkius.connect.helpers.MockSdk;
import com.vinkius.connect.model.CapabilityQuery;
import com.vinkius.connect.model.CapabilityResult;
import com.vinkius.connect.model.Hooks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class CapabilitiesTest {

  private static Route connectionsRoute(List<Map<String, Object>> connections) {
    return Route.get(
        "^/apps/vk_app_test/users/customer-123/mcps$",
        call -> MockResponse.ok(Map.of("data", connections)));
  }

  @Test
  void aggregatesReadyConnectorsAndExecutesOnRuntime() {
    Fixtures.RuntimeHandlers handlers = new Fixtures.RuntimeHandlers();
    handlers.call =
        rpc -> {
          Map<String, Object> params = castBody(rpc.get("params"));
          return Map.of(
              "content",
              List.of(Map.of("type", "text", "text", "ok:" + Json.write(params))),
              "isError",
              false);
        };
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(List.of(Fixtures.connection("conn_1", "github", true, "active"))),
            Fixtures.tokenRoute("conn_1"),
            Fixtures.runtimeRoute(handlers));
    Vinkius vinkius = MockSdk.make(sender);

    CapabilitySet capabilities = vinkius.user(Fixtures.USER).capabilities();

    assertThat(capabilities.size()).isEqualTo(1);
    Capability capability =
        Objects.requireNonNull(
            capabilities.findCapability("github__create_issue"), "github__create_issue");
    assertThat(capability.rawName()).isEqualTo("create_issue");
    assertThat(capability.connector()).isEqualTo("github");
    assertThat(capability.connectionId()).isEqualTo("conn_1");

    CapabilityResult result = capability.execute(Map.of("title", "Hello Vinkius"));
    assertThat(result.isError()).isFalse();

    // Execution hits the runtime with a JSON-RPC tools/call, never the API.
    RecordedCall execCall =
        sender.calls().stream()
            .filter(call -> call.path().equals("/vk_live_test/mcp") && isToolCall(call))
            .findFirst()
            .orElseThrow();
    Map<String, Object> body = castBody(execCall.body());
    assertThat(body.get("method")).isEqualTo("tools/call");
    Map<String, Object> params = castBody(body.get("params"));
    assertThat(params.get("name")).isEqualTo("create_issue");
    assertThat(params.get("arguments")).isEqualTo(Map.of("title", "Hello Vinkius"));
  }

  @Test
  void includesOnlyRequestedConnectors() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(
                List.of(
                    Fixtures.connection("conn_1", "github", true, "active"),
                    Fixtures.connection("conn_2", "slack", true, "active"))),
            Fixtures.tokenRoute("conn_1"),
            Fixtures.runtimeRoute());
    Vinkius vinkius = MockSdk.make(sender);

    CapabilitySet capabilities =
        vinkius
            .user(Fixtures.USER)
            .capabilities(CapabilityQuery.builder().include("github").build());

    assertThat(capabilities.asList()).extracting(Capability::connector).containsExactly("github");
    // Only github's token was minted — slack was filtered out before any call.
    assertThat(sender.calls())
        .extracting(RecordedCall::path)
        .contains("/apps/vk_app_test/users/customer-123/mcps/conn_1/tokens")
        .noneMatch(path -> path.endsWith("/conn_2/tokens"));
  }

  @Test
  void appliesExcludeBeforeTouchingRuntime() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(
                List.of(
                    Fixtures.connection("conn_1", "github", true, "active"),
                    Fixtures.connection("conn_2", "slack", true, "active"))),
            Fixtures.tokenRoute("conn_1"),
            Fixtures.runtimeRoute());
    Vinkius vinkius = MockSdk.make(sender);

    CapabilitySet capabilities =
        vinkius
            .user(Fixtures.USER)
            .capabilities(CapabilityQuery.builder().exclude("slack").build());

    assertThat(capabilities.asList()).extracting(Capability::connector).containsExactly("github");
    assertThat(sender.calls()).noneMatch(call -> call.path().endsWith("/conn_2/tokens"));
  }

  @Test
  void skipsConnectorsThatAreNotReady() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(
                List.of(
                    Fixtures.connection("conn_1", "github", true, "active"),
                    Fixtures.connection("conn_2", "slack", false, "active"))),
            Fixtures.tokenRoute("conn_1"),
            Fixtures.runtimeRoute());
    Vinkius vinkius = MockSdk.make(sender);

    CapabilitySet capabilities = vinkius.user(Fixtures.USER).capabilities();

    assertThat(capabilities.asList()).extracting(Capability::connector).containsExactly("github");
  }

  @Test
  void resolvesConnectionListOnceForWholeFanOut() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(
                List.of(
                    Fixtures.connection("conn_1", "github", true, "active"),
                    Fixtures.connection("conn_2", "slack", true, "active"))),
            Fixtures.tokenRoute("conn_1"),
            Fixtures.tokenRoute("conn_2"),
            Fixtures.runtimeRoute());
    Vinkius vinkius = MockSdk.make(sender);

    CapabilitySet capabilities = vinkius.user(Fixtures.USER).capabilities();

    assertThat(capabilities.asList())
        .extracting(Capability::connector)
        .containsExactlyInAnyOrder("github", "slack");
    long listCalls =
        sender.calls().stream()
            .filter(
                call ->
                    "GET".equals(call.method())
                        && call.path().equals("/apps/vk_app_test/users/customer-123/mcps"))
            .count();
    assertThat(listCalls).isEqualTo(1);
  }

  @Test
  void emitsRedactedHooksForRuntimeTraffic() {
    List<String> urls = Collections.synchronizedList(new ArrayList<>());
    Hooks hooks =
        new Hooks() {
          @Override
          public void onRequest(RequestInfo info) {
            urls.add(info.url());
          }

          @Override
          public void onResponse(ResponseInfo info) {
            urls.add(info.url());
          }
        };
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(List.of(Fixtures.connection("conn_1", "github", true, "active"))),
            Fixtures.tokenRoute("conn_1"),
            Fixtures.runtimeRoute());
    Vinkius vinkius = MockSdk.make(sender, options -> options.hooks(hooks));

    vinkius.user(Fixtures.USER).capabilities();

    assertThat(urls).contains("http://localhost:9090/[REDACTED]/mcp");
    assertThat(urls).allMatch(url -> !url.contains("vk_live_test"));
  }

  @Test
  void keepsPartialResultsWhenOneConnectorFailsInFanOut() {
    // github's token mint succeeds; slack's fails → github's capabilities survive.
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(
                List.of(
                    Fixtures.connection("conn_1", "github", true, "active"),
                    Fixtures.connection("conn_2", "slack", true, "active"))),
            Fixtures.tokenRoute("conn_1"),
            Route.post(
                "^/apps/vk_app_test/users/customer-123/mcps/conn_2/tokens$",
                call -> MockResponse.of(500, Map.of("message", "boom"))),
            Fixtures.runtimeRoute());
    Vinkius vinkius = MockSdk.make(sender);

    CapabilitySet capabilities = vinkius.user(Fixtures.USER).capabilities();

    assertThat(capabilities.asList()).extracting(Capability::connector).containsExactly("github");
  }

  @Test
  void throwsWhenAllConnectorsFailInFanOut() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(List.of(Fixtures.connection("conn_1", "github", true, "active"))),
            Route.post(
                "^/apps/vk_app_test/users/customer-123/mcps/conn_1/tokens$",
                call -> MockResponse.of(500, Map.of("message", "boom"))));
    Vinkius vinkius = MockSdk.make(sender);

    VinkiusException error =
        assertThrows(VinkiusException.class, () -> vinkius.user(Fixtures.USER).capabilities());

    assertThat(error.status()).isEqualTo(500);
  }

  @Test
  void emptyCapabilitySetBasics() {
    CapabilitySet set = new CapabilitySet();

    assertThat(set.isEmpty()).isTrue();
    assertThat(set.size()).isZero();
    assertThat(set.asList()).isEmpty();
    assertThat(set.findCapability("x")).isNull();
    assertThat(set.forConnector("x").isEmpty()).isTrue();
    assertThat(set.toString()).isEqualTo("CapabilitySet[]");
    assertThat(set.stream().count()).isZero();
    assertThat(set.iterator().hasNext()).isFalse();
    assertThrows(IndexOutOfBoundsException.class, () -> set.get(0));
  }

  private static boolean isToolCall(RecordedCall call) {
    if (!(call.body() instanceof Map<?, ?> body)) {
      return false;
    }
    return "tools/call".equals(body.get("method"));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castBody(@Nullable Object body) {
    Objects.requireNonNull(body, "body");
    return (Map<String, Object>) body;
  }
}
