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
package com.vinkius.connect.helpers;

import com.vinkius.connect.helpers.FakeHttpSender.MockResponse;
import com.vinkius.connect.helpers.FakeHttpSender.Route;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Wire fixtures (parity with the JS {@code mock-fetch} fixture helpers). */
public final class Fixtures {

  /** The external id used by most test users. */
  public static final String USER = "customer-123";

  /** A distinct host so runtime calls are trivially routable in the mock. */
  public static final String RUNTIME_MCP_URL = "http://localhost:9090/vk_live_test/mcp";

  /** Path regex of the runtime JSON-RPC endpoint. */
  public static final String RUNTIME_MCP_PATH = "^/vk_live_test/mcp$";

  private Fixtures() {}

  /** An app-user wire payload. */
  public static Map<String, Object> appUser(String id, String externalId) {
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("id", id);
    user.put("external_id", externalId);
    user.put("status", "active");
    user.put("metadata", null);
    user.put("application_id", MockSdk.APP_ID);
    user.put("created_at", "2026-01-01T00:00:00+00:00");
    user.put("updated_at", "2026-01-01T00:00:00+00:00");
    return user;
  }

  /** A ready connection wire payload. */
  public static Map<String, Object> connection(String id, String slug) {
    return connection(id, slug, true, "active");
  }

  /** A connection wire payload with explicit readiness/status. */
  public static Map<String, Object> connection(
      String id, String slug, boolean ready, @Nullable String status) {
    Map<String, Object> connection = new LinkedHashMap<>();
    connection.put("id", id);
    connection.put("slug", slug);
    connection.put("name", slug);
    connection.put("description", null);
    connection.put("status", status != null ? status : "active");
    connection.put("ready", ready);
    connection.put("created_at", "2026-01-01T00:00:00+00:00");
    return connection;
  }

  /** A capability wire payload (aggregated endpoint shape, snake_case input_schema). */
  public static Map<String, Object> capabilityData() {
    return capabilityData("create_issue", "github", "conn_1");
  }

  /** A capability wire payload with explicit coordinates. */
  public static Map<String, Object> capabilityData(
      String name, String connector, String connectionId) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", name);
    data.put("title", "Create Issue");
    data.put("description", "Create a GitHub issue");
    data.put(
        "input_schema",
        Map.of("type", "object", "properties", Map.of("title", Map.of("type", "string"))));
    data.put("connector", connector);
    data.put("connection_id", connectionId);
    return data;
  }

  /** The one-time token/mcp_url the API returns from POST .../mcps/{id}/tokens. */
  public static Map<String, Object> issuedToken() {
    return issuedToken(RUNTIME_MCP_URL);
  }

  /** The one-time token payload with an explicit mcp_url. */
  public static Map<String, Object> issuedToken(String mcpUrl) {
    Map<String, Object> token = new LinkedHashMap<>();
    token.put("id", "tok_1");
    token.put("name", "Execution Plane token");
    token.put("is_enabled", true);
    token.put("token", "vk_live_test");
    token.put("mcp_url", mcpUrl);
    token.put("created_at", "2026-01-01T00:00:00+00:00");
    return token;
  }

  /** Route: mint a token for {@code conn_1} (returns mcp_url pointing at the runtime). */
  public static Route tokenRoute() {
    return tokenRoute("conn_1", RUNTIME_MCP_URL);
  }

  /** Route: mint a token for one connection. */
  public static Route tokenRoute(String connectionId) {
    return tokenRoute(connectionId, RUNTIME_MCP_URL);
  }

  /** Route: mint a token for one connection with an explicit mcp_url. */
  public static Route tokenRoute(String connectionId, String mcpUrl) {
    return tokenRoute(USER, connectionId, mcpUrl);
  }

  /** Route: mint a token for a specific user + connection. */
  public static Route tokenRoute(String externalId, String connectionId, String mcpUrl) {
    String path =
        "^/apps/" + MockSdk.APP_ID + "/users/" + externalId + "/mcps/" + connectionId + "/tokens$";
    return Route.post(path, call -> MockResponse.ok(Map.of("data", issuedToken(mcpUrl))));
  }

  /** A runtime tool definition (camelCase inputSchema, as the runtime emits). */
  public static Map<String, Object> runtimeTool() {
    return runtimeTool("create_issue");
  }

  /** A runtime tool definition with an explicit name. */
  public static Map<String, Object> runtimeTool(String name) {
    Map<String, Object> tool = new LinkedHashMap<>();
    tool.put("name", name);
    tool.put("title", "Create Issue");
    tool.put("description", "Create a GitHub issue");
    tool.put(
        "inputSchema",
        Map.of("type", "object", "properties", Map.of("title", Map.of("type", "string"))));
    return tool;
  }

  /** Handler configuration for {@link #runtimeRoute}. */
  public static final class RuntimeHandlers {

    /** Tools returned by {@code tools/list} (default: one {@code create_issue} tool). */
    public List<Map<String, Object>> tools = List.of(runtimeTool());

    /** Full RPC body → result payload for {@code tools/call}; {@code null} = default result. */
    public @Nullable Function<Map<String, Object>, Map<String, Object>> call;

    /** Simulated latency for {@code tools/call} (honors cancellation). */
    public long callDelayMs;
  }

  /** Route: the runtime JSON-RPC endpoint with default handlers. */
  public static Route runtimeRoute() {
    return runtimeRoute(new RuntimeHandlers());
  }

  /** Route: the runtime JSON-RPC endpoint handling tools/list and tools/call. */
  public static Route runtimeRoute(RuntimeHandlers handlers) {
    return Route.post(
        RUNTIME_MCP_PATH,
        rpcCall -> {
          Map<String, Object> rpc =
              rpcCall.body() instanceof Map ? asMap(rpcCall.body()) : Map.of();
          @Nullable Object idValue = rpc.getOrDefault("id", -1);
          @Nullable Object methodValue = rpc.get("method");
          if ("tools/list".equals(methodValue)) {
            return MockResponse.ok(
                Map.of("jsonrpc", "2.0", "id", idValue, "result", Map.of("tools", handlers.tools)));
          }
          if ("tools/call".equals(methodValue)) {
            Map<String, Object> result =
                handlers.call != null
                    ? handlers.call.apply(rpc)
                    : Map.of(
                        "content",
                        List.of(Map.of("type", "text", "text", "done")),
                        "isError",
                        false);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("jsonrpc", "2.0");
            body.put("id", idValue);
            body.put("result", result);
            MockResponse response = MockResponse.ok(body);
            return handlers.callDelayMs > 0 ? response.withDelay(handlers.callDelayMs) : response;
          }
          return MockResponse.ok(
              Map.of(
                  "jsonrpc",
                  "2.0",
                  "id",
                  idValue,
                  "error",
                  Map.of("code", -32601, "message", "Method not found")));
        });
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return (Map<String, Object>) value;
  }
}
