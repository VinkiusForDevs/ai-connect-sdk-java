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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vinkius.connect.adapters.AdapterSupport;
import com.vinkius.connect.adapters.JsonSchemaAdapter;
import com.vinkius.connect.core.Json;
import com.vinkius.connect.error.ConfigException;
import com.vinkius.connect.error.ConnectionException;
import com.vinkius.connect.error.VinkiusException;
import com.vinkius.connect.fluent.Capability;
import com.vinkius.connect.fluent.CapabilitySet;
import com.vinkius.connect.fluent.Vinkius;
import com.vinkius.connect.helpers.FakeHttpSender;
import com.vinkius.connect.helpers.FakeHttpSender.MockResponse;
import com.vinkius.connect.helpers.FakeHttpSender.Route;
import com.vinkius.connect.helpers.Fixtures;
import com.vinkius.connect.helpers.MockSdk;
import com.vinkius.connect.model.CapabilityQuery;
import com.vinkius.connect.model.CapabilityResult;
import com.vinkius.connect.model.ExecuteOptions;
import com.vinkius.connect.model.RequestOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SdkFeaturesTest {

  private static Route connectionsRoute(List<Map<String, Object>> connections) {
    return Route.get(
        "^/apps/vk_app_test/users/customer-123/mcps$",
        call -> MockResponse.ok(Map.of("data", connections)));
  }

  private static Capability find(CapabilitySet capabilities) {
    return Objects.requireNonNull(
        capabilities.findCapability("github__create_issue"), "github__create_issue");
  }

  // ---------------------------------------------------------------- onConnectorError observability

  @Test
  void invokesOnConnectorErrorForIsolatedFanOutFailureButKeepsTheRest() {
    List<String> failedSlugs = Collections.synchronizedList(new ArrayList<>());
    List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
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
    CapabilityQuery query =
        CapabilityQuery.builder()
            .onConnectorError(
                (slug, error) -> {
                  failedSlugs.add(slug);
                  failures.add(error);
                })
            .build();

    CapabilitySet capabilities = vinkius.user(Fixtures.USER).capabilities(query);

    assertThat(capabilities.asList()).extracting(Capability::connector).containsExactly("github");
    assertThat(failedSlugs).containsExactly("slack");
    assertThat(failures).hasSize(1);
    assertThat(failures.get(0)).isInstanceOf(VinkiusException.class);
  }

  @Test
  void doesNotInvokeOnConnectorErrorWhenEveryConnectorSucceeds() {
    List<String> failedSlugs = Collections.synchronizedList(new ArrayList<>());
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(List.of(Fixtures.connection("conn_1", "github", true, "active"))),
            Fixtures.tokenRoute("conn_1"),
            Fixtures.runtimeRoute());
    Vinkius vinkius = MockSdk.make(sender);
    CapabilityQuery query =
        CapabilityQuery.builder().onConnectorError((slug, error) -> failedSlugs.add(slug)).build();

    vinkius.user(Fixtures.USER).capabilities(query);

    assertThat(failedSlugs).isEmpty();
  }

  // ------------------------------------------------------- runtime structuredContent passthrough

  @Test
  void surfacesStructuredContentOnCapabilityResult() {
    Fixtures.RuntimeHandlers handlers = new Fixtures.RuntimeHandlers();
    handlers.call =
        rpc ->
            Map.of(
                "content", List.of(Map.of("type", "text", "text", "plain")),
                "structuredContent", Map.of("issueNumber", 42),
                "isError", false);
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(List.of(Fixtures.connection("conn_1", "github", true, "active"))),
            Fixtures.tokenRoute("conn_1"),
            Fixtures.runtimeRoute(handlers));
    Vinkius vinkius = MockSdk.make(sender);

    CapabilityResult result = find(vinkius.user(Fixtures.USER).capabilities()).execute(Map.of());

    assertThat(result.content()).hasSize(1);
    assertThat(result.content().get(0).type()).isEqualTo("text");
    assertThat(result.content().get(0).text()).isEqualTo("plain");
    Map<String, ?> structured =
        Objects.requireNonNull(Json.asMap(result.structuredContent()), "structuredContent");
    assertThat(structured.get("issueNumber")).isEqualTo(42);
    assertThat(result.isError()).isFalse();
  }

  // ------------------------------------------------------------------- per-call timeoutMs

  @Test
  void timesOutLongRunningToolWhenTimeoutMsSmallerThanLatency() {
    Fixtures.RuntimeHandlers handlers = new Fixtures.RuntimeHandlers();
    handlers.call =
        rpc -> Map.of("content", List.of(Map.of("type", "text", "text", "slow")), "isError", false);
    handlers.callDelayMs = 500;
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(List.of(Fixtures.connection("conn_1", "github", true, "active"))),
            Fixtures.tokenRoute("conn_1"),
            Fixtures.runtimeRoute(handlers));
    Vinkius vinkius = MockSdk.make(sender);
    Capability capability = find(vinkius.user(Fixtures.USER).capabilities());

    ConnectionException error =
        assertThrows(
            ConnectionException.class,
            () -> capability.execute(Map.of(), ExecuteOptions.builder().timeoutMs(30).build()));

    assertThat(error.code().value()).isEqualTo("connection_error");
  }

  @Test
  void completesWhenTimeoutMsComfortablyExceedsLatency() {
    Fixtures.RuntimeHandlers handlers = new Fixtures.RuntimeHandlers();
    handlers.call =
        rpc -> Map.of("content", List.of(Map.of("type", "text", "text", "ok")), "isError", false);
    handlers.callDelayMs = 500;
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(List.of(Fixtures.connection("conn_1", "github", true, "active"))),
            Fixtures.tokenRoute("conn_1"),
            Fixtures.runtimeRoute(handlers));
    Vinkius vinkius = MockSdk.make(sender, options -> options.timeoutMs(5000));
    Capability capability = find(vinkius.user(Fixtures.USER).capabilities());

    CapabilityResult result =
        capability.execute(Map.of(), ExecuteOptions.builder().timeoutMs(5000).build());

    assertThat(result.isError()).isFalse();
  }

  // --------------------------------------------- executeByName propagates ExecuteOptions

  @Test
  void executeByNameForwardsTimeoutMs() {
    Fixtures.RuntimeHandlers handlers = new Fixtures.RuntimeHandlers();
    handlers.call =
        rpc -> Map.of("content", List.of(Map.of("type", "text", "text", "slow")), "isError", false);
    handlers.callDelayMs = 500;
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(List.of(Fixtures.connection("conn_1", "github", true, "active"))),
            Fixtures.tokenRoute("conn_1"),
            Fixtures.runtimeRoute(handlers));
    Vinkius vinkius = MockSdk.make(sender);
    List<Capability> capabilities = vinkius.user(Fixtures.USER).capabilities().asList();

    ConnectionException error =
        assertThrows(
            ConnectionException.class,
            () ->
                JsonSchemaAdapter.executeByName(
                    capabilities,
                    "github__create_issue",
                    Map.of(),
                    ExecuteOptions.builder().timeoutMs(30).build()));

    assertThat(error.code().value()).isEqualTo("connection_error");
  }

  @Test
  void executeByNameRunsNormallyWithoutExecuteOptions() {
    Fixtures.RuntimeHandlers handlers = new Fixtures.RuntimeHandlers();
    handlers.call =
        rpc -> Map.of("content", List.of(Map.of("type", "text", "text", "done")), "isError", false);
    handlers.callDelayMs = 500;
    FakeHttpSender sender =
        FakeHttpSender.of(
            connectionsRoute(List.of(Fixtures.connection("conn_1", "github", true, "active"))),
            Fixtures.tokenRoute("conn_1"),
            Fixtures.runtimeRoute(handlers));
    Vinkius vinkius = MockSdk.make(sender);
    List<Capability> capabilities = vinkius.user(Fixtures.USER).capabilities().asList();

    CapabilityResult result =
        JsonSchemaAdapter.executeByName(capabilities, "github__create_issue", Map.of("title", "x"));

    assertThat(result.isError()).isFalse();
  }

  // -------------------------------------------------- tool-name validation (shared helper)

  @Test
  void rejectsToolNameLongerThan64Chars() {
    Pattern openAiName = Pattern.compile("[a-zA-Z0-9_-]+");

    assertThatCode(() -> AdapterSupport.validateToolName("x".repeat(65), "OpenAI", openAiName, 64))
        .isInstanceOf(ConfigException.class);
  }

  @Test
  void rejectsToolNameWithUnsupportedCharacters() {
    Pattern openAiName = Pattern.compile("[a-zA-Z0-9_-]+");

    assertThatCode(
            () -> AdapterSupport.validateToolName("github.create_issue", "OpenAI", openAiName, 64))
        .isInstanceOf(ConfigException.class);
  }

  @Test
  void acceptsValidToolName() {
    Pattern openAiName = Pattern.compile("[a-zA-Z0-9_-]+");

    assertThatCode(
            () -> AdapterSupport.validateToolName("github__create_issue", "OpenAI", openAiName, 64))
        .doesNotThrowAnyException();
  }

  // ----------------------------------------------------------------- auto-pagination iterate()

  @Test
  void catalogIterateWalksEveryPage() {
    Map<String, Object> meta = meta(1, 2);
    Map<String, Object> meta2 = meta(2, 2);
    Map<String, Object> pages =
        Map.of(
            "1", Map.of("data", List.of(catalogConn("github")), "meta", meta, "links", Map.of()),
            "2", Map.of("data", List.of(catalogConn("slack")), "meta", meta2, "links", Map.of()));
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/catalog/mcps$",
                call -> {
                  String page = call.queryParam("page") != null ? call.queryParam("page") : "1";
                  return MockResponse.ok(pages.get(page));
                }));
    Vinkius vinkius = MockSdk.make(sender);

    Iterator<com.vinkius.connect.model.CatalogConnector> iterator =
        vinkius.catalog().iterate(null, RequestOptions.EMPTY);
    List<String> seen = new ArrayList<>();
    while (iterator.hasNext()) {
      seen.add(iterator.next().slug());
    }

    assertThat(seen).containsExactly("github", "slack");
  }

  @Test
  void usersIterateWalksEveryPage() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/apps/vk_app_test/users$",
                call -> {
                  String page = call.queryParam("page") != null ? call.queryParam("page") : "1";
                  if ("1".equals(page)) {
                    return MockResponse.ok(
                        Map.of(
                            "data", List.of(appUserWire("u1", "a")),
                            "meta", meta(1, 2),
                            "links", Map.of()));
                  }
                  return MockResponse.ok(
                      Map.of(
                          "data", List.of(appUserWire("u2", "b")),
                          "meta", meta(2, 2),
                          "links", Map.of()));
                }));
    Vinkius vinkius = MockSdk.make(sender);

    Iterator<com.vinkius.connect.model.AppUser> iterator =
        vinkius.users().iterate(null, RequestOptions.EMPTY);
    List<String> ids = new ArrayList<>();
    while (iterator.hasNext()) {
      ids.add(iterator.next().id());
    }

    assertThat(ids).containsExactly("u1", "u2");
  }

  private static Map<String, Object> meta(int current, int last) {
    Map<String, Object> pageMeta = new LinkedHashMap<>();
    pageMeta.put("current_page", current);
    pageMeta.put("from", 1);
    pageMeta.put("last_page", last);
    pageMeta.put("path", "http://localhost:8080/catalog/mcps");
    pageMeta.put("per_page", 50);
    pageMeta.put("to", 1);
    pageMeta.put("total", last);
    return pageMeta;
  }

  private static Map<String, Object> catalogConn(String slug) {
    Map<String, Object> connector = new LinkedHashMap<>();
    connector.put("id", slug);
    connector.put("slug", slug);
    connector.put("title", slug);
    connector.put("short_description", null);
    connector.put("publisher_type", "first_party");
    connector.put("listing_type", "");
    connector.put("requires_buyer_auth", false);
    connector.put("tools_count", 1);
    return connector;
  }

  private static Map<String, Object> appUserWire(String id, String externalId) {
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("id", id);
    user.put("external_id", externalId);
    user.put("status", "active");
    user.put("metadata", null);
    user.put("created_at", "x");
    user.put("updated_at", "x");
    return user;
  }
}
