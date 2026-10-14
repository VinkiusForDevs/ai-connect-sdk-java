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
package com.vinkius.connect.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vinkius.connect.error.AuthException;
import com.vinkius.connect.error.ConfigException;
import com.vinkius.connect.error.NotFoundException;
import com.vinkius.connect.error.OverageException;
import com.vinkius.connect.error.ProtocolException;
import com.vinkius.connect.error.QuotaException;
import com.vinkius.connect.error.RateLimitException;
import com.vinkius.connect.error.VinkiusException;
import com.vinkius.connect.helpers.FakeHttpSender;
import com.vinkius.connect.helpers.FakeHttpSender.MockResponse;
import com.vinkius.connect.helpers.FakeHttpSender.Route;
import com.vinkius.connect.helpers.Fixtures;
import com.vinkius.connect.model.CapabilityData;
import com.vinkius.connect.model.CapabilityResult;
import com.vinkius.connect.model.RequestOptions;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class RuntimeTest {

  /** A RuntimeClient whose route answers {@code tools/call} with a fixed JSON-RPC result. */
  private static RuntimeClient clientForResult(Map<String, Object> result) {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.post(
                Fixtures.RUNTIME_MCP_PATH,
                call -> MockResponse.ok(Map.of("jsonrpc", "2.0", "id", 1, "result", result))));
    return new RuntimeClient(
        Fixtures.RUNTIME_MCP_URL,
        new RuntimeClient.Config(5000, new RetryPolicy(0, 250, 4000), sender, "test", null));
  }

  /** A RuntimeClient whose route answers every call with a fixed response. */
  private static RuntimeClient clientRespondingWith(MockResponse response) {
    FakeHttpSender sender =
        FakeHttpSender.of(Route.post(Fixtures.RUNTIME_MCP_PATH, call -> response));
    return clientFor(sender);
  }

  /** A RuntimeClient over an inspectable sender. */
  private static RuntimeClient clientFor(FakeHttpSender sender) {
    return new RuntimeClient(
        Fixtures.RUNTIME_MCP_URL,
        new RuntimeClient.Config(5000, new RetryPolicy(0, 250, 4000), sender, "test", null));
  }

  /** A JSON-RPC envelope wrapping {@code result}. */
  private static Map<String, Object> envelopeWith(Object result) {
    return Map.of("jsonrpc", "2.0", "id", 1, "result", result);
  }

  /** A runtime gate block, as the tool pipeline emits it. */
  private static Map<String, Object> blockResult(String kind, String text) {
    return Map.of(
        "content", List.of(Map.of("type", "text", "text", text)),
        "isError", true,
        "structuredContent", Map.of("vinkius_block", kind));
  }

  private static Map<String, Object> blockResult(String kind, String text, String upgradeUrl) {
    return Map.of(
        "content",
        List.of(Map.of("type", "text", "text", text)),
        "isError",
        true,
        "structuredContent",
        Map.of("vinkius_block", kind, "upgrade_url", upgradeUrl));
  }

  @Test
  void mapsPlanQuotaBlockToQuotaExceptionCarryingUpgradeUrl() {
    RuntimeClient client =
        clientForResult(
            blockResult(
                "quota",
                "VINKIUS SUBSCRIPTION REQUIRED\u2026",
                "https://cloud.vinkius.com/settings/upgrade"));

    QuotaException error =
        assertThrows(
            QuotaException.class, () -> client.callTool("t", Map.of(), RequestOptions.EMPTY));

    assertThat(error.code().value()).isEqualTo("quota_exceeded");
    assertThat(error.upgradeUrl()).isEqualTo("https://cloud.vinkius.com/settings/upgrade");
    assertThat(error.getMessage()).contains("VINKIUS SUBSCRIPTION REQUIRED");
  }

  @Test
  void mapsOverageBlockToOverageException() {
    RuntimeClient client =
        clientForResult(
            blockResult(
                "overage",
                "PAYMENT REQUIRED \u2014 OVERAGE CHARGE DECLINED",
                "https://cloud.vinkius.com/billing"));

    OverageException error =
        assertThrows(
            OverageException.class, () -> client.callTool("t", Map.of(), RequestOptions.EMPTY));

    assertThat(error.code().value()).isEqualTo("overage_blocked");
    assertThat(error.upgradeUrl()).isEqualTo("https://cloud.vinkius.com/billing");
  }

  @Test
  void mapsCircuitBreakerBlockToRateLimitException() {
    RuntimeClient client =
        clientForResult(
            blockResult("circuit_breaker", "[SYSTEM] CRITICAL: budget ceiling exceeded"));

    assertThrows(
        RateLimitException.class, () -> client.callTool("t", Map.of(), RequestOptions.EMPTY));
  }

  @Test
  void leavesReadOnlyPreviewBlockInBand() {
    RuntimeClient client =
        clientForResult(blockResult("preview", "Tool execution requires a production token."));

    CapabilityResult result = client.callTool("t", Map.of(), RequestOptions.EMPTY);

    assertThat(result.isError()).isTrue();
    Map<String, ?> structured =
        Objects.requireNonNull(Json.asMap(result.structuredContent()), "structuredContent");
    assertThat(structured.get("vinkius_block")).isEqualTo("preview");
  }

  @Test
  void leavesOrdinaryUpstreamToolFailureInBand() {
    RuntimeClient client =
        clientForResult(
            Map.of(
                "content",
                List.of(Map.of("type", "text", "text", "upstream returned 500")),
                "isError",
                true));

    CapabilityResult result = client.callTool("t", Map.of(), RequestOptions.EMPTY);

    assertThat(result.isError()).isTrue();
  }

  // ----------------------------------------------------------- runtime HTTP error mapping

  @Test
  void mapsRuntimeHttp401ToAuthExceptionReadingTheErrorKey() {
    RuntimeClient client =
        clientRespondingWith(MockResponse.of(401, Map.of("error", "token expired")));

    AuthException error =
        assertThrows(
            AuthException.class, () -> client.callTool("t", Map.of(), RequestOptions.EMPTY));

    assertThat(error).hasMessage("token expired");
    assertThat(error.status()).isEqualTo(401);
  }

  @Test
  void mapsRuntimeHttp403ToAuthExceptionWithFallbackMessage() {
    RuntimeClient client = clientRespondingWith(MockResponse.of(403, null));

    AuthException error =
        assertThrows(
            AuthException.class, () -> client.callTool("t", Map.of(), RequestOptions.EMPTY));

    assertThat(error).hasMessage("Runtime request failed with status 403");
  }

  @Test
  void mapsRuntimeHttp404ToNotFoundExceptionReadingTheMessageKey() {
    RuntimeClient client =
        clientRespondingWith(MockResponse.of(404, Map.of("message", "unknown tool")));

    NotFoundException error =
        assertThrows(
            NotFoundException.class, () -> client.callTool("t", Map.of(), RequestOptions.EMPTY));

    assertThat(error).hasMessage("unknown tool");
  }

  @Test
  void mapsRuntimeHttp500ToPlainVinkiusExceptionWithNestedErrorMessage() {
    RuntimeClient client =
        clientRespondingWith(
            MockResponse.of(500, Map.of("error", Map.of("message", "upstream exploded"))));

    VinkiusException error =
        assertThrows(
            VinkiusException.class, () -> client.callTool("t", Map.of(), RequestOptions.EMPTY));

    assertThat(error).isExactlyInstanceOf(VinkiusException.class);
    assertThat(error.code().value()).isEqualTo("connection_error");
    assertThat(error).hasMessage("upstream exploded");
  }

  @Test
  void mapsRuntimeHttp500PickingTopLevelMessageField() {
    RuntimeClient client =
        clientRespondingWith(MockResponse.of(500, Map.of("message", "maintenance window")));

    VinkiusException error =
        assertThrows(
            VinkiusException.class, () -> client.callTool("t", Map.of(), RequestOptions.EMPTY));

    assertThat(error).hasMessage("maintenance window");
  }

  @Test
  void mapsRuntimeHttp500WithPlainStringBody() {
    RuntimeClient client = clientRespondingWith(MockResponse.of(500, "socket hang up"));

    VinkiusException error =
        assertThrows(
            VinkiusException.class, () -> client.callTool("t", Map.of(), RequestOptions.EMPTY));

    assertThat(error).hasMessage("socket hang up");
  }

  // --------------------------------------------------------------------- idempotency

  @Test
  void rejectsBlankRuntimeIdempotencyKey() {
    RuntimeClient client = clientRespondingWith(MockResponse.ok(envelopeWith(Map.of())));
    RequestOptions options = RequestOptions.builder().idempotencyKey("   ").build();

    assertThrows(ConfigException.class, () -> client.callTool("t", Map.of(), options));
  }

  @Test
  void sendsIdempotencyKeyHeaderWhenProvided() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.post(
                Fixtures.RUNTIME_MCP_PATH,
                call -> MockResponse.ok(envelopeWith(Map.of("content", List.of())))));
    RuntimeClient client = clientFor(sender);

    client.callTool("t", Map.of(), RequestOptions.builder().idempotencyKey("key-1").build());

    assertThat(sender.call(0).header("idempotency-key")).isEqualTo("key-1");
  }

  @Test
  void passesEmptyArgumentsWhenArgsAreNull() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.post(
                Fixtures.RUNTIME_MCP_PATH,
                call -> MockResponse.ok(envelopeWith(Map.of("content", List.of())))));
    RuntimeClient client = clientFor(sender);

    client.callTool("t", null, RequestOptions.EMPTY);

    Object rawBody = Objects.requireNonNull(sender.call(0).body(), "request body");
    assertThat(rawBody).isInstanceOf(Map.class);
    Map<?, ?> body = (Map<?, ?>) rawBody;
    Map<?, ?> params = (Map<?, ?>) Objects.requireNonNull(body.get("params"), "params");
    assertThat(params.get("arguments")).isEqualTo(Map.of());
  }

  // ----------------------------------------------------------------------- tools/list

  @Test
  void listToolsNormalizesToolDefinitions() {
    Map<String, Object> full =
        Map.of(
            "name",
            "gh__create",
            "title",
            "Create",
            "description",
            "Makes issues",
            "inputSchema",
            Map.of("type", "object"),
            "annotations",
            Map.of("readOnlyHint", true));
    Map<String, Object> snakeCase =
        Map.of("name", "gh__list", "input_schema", Map.of("type", "object"));
    RuntimeClient client =
        clientRespondingWith(
            MockResponse.ok(envelopeWith(Map.of("tools", List.of(full, snakeCase)))));

    List<CapabilityData> tools = client.listTools(RequestOptions.EMPTY);

    assertThat(tools).hasSize(2);
    CapabilityData first = tools.get(0);
    assertThat(first.name()).isEqualTo("gh__create");
    assertThat(first.title()).isEqualTo("Create");
    assertThat(first.description()).isEqualTo("Makes issues");
    assertThat(first.inputSchema()).containsEntry("type", "object");
    assertThat(first.annotations()).isEqualTo(Map.of("readOnlyHint", true));
    assertThat(first.connector()).isNull();
    CapabilityData second = tools.get(1);
    assertThat(second.name()).isEqualTo("gh__list");
    assertThat(second.inputSchema()).containsEntry("type", "object");
    assertThat(second.title()).isNull();
    assertThat(second.description()).isNull();
  }

  @Test
  void listToolsRejectsResultWithoutToolsArray() {
    RuntimeClient client = clientRespondingWith(MockResponse.ok(envelopeWith(Map.of())));

    ProtocolException error =
        assertThrows(ProtocolException.class, () -> client.listTools(RequestOptions.EMPTY));

    assertThat(error).hasMessageContaining("no tools array");
  }

  @Test
  void listToolsRejectsNonMapToolDefinition() {
    RuntimeClient client =
        clientRespondingWith(MockResponse.ok(envelopeWith(Map.of("tools", List.of("scalar")))));

    ProtocolException error =
        assertThrows(ProtocolException.class, () -> client.listTools(RequestOptions.EMPTY));

    assertThat(error).hasMessageContaining("missing a name");
  }

  @Test
  void listToolsRejectsToolDefinitionWithBlankName() {
    RuntimeClient client =
        clientRespondingWith(
            MockResponse.ok(envelopeWith(Map.of("tools", List.of(Map.of("name", "   "))))));

    ProtocolException error =
        assertThrows(ProtocolException.class, () -> client.listTools(RequestOptions.EMPTY));

    assertThat(error).hasMessageContaining("missing a name");
  }

  // ------------------------------------------------------------------------ envelopes

  @Test
  void mapsJsonRpcErrorObjectToProtocolExceptionWithItsMessage() {
    RuntimeClient client =
        clientRespondingWith(
            MockResponse.ok(
                Map.of(
                    "jsonrpc",
                    "2.0",
                    "id",
                    1,
                    "error",
                    Map.of("code", -32601, "message", "Method not found"))));

    ProtocolException error =
        assertThrows(
            ProtocolException.class, () -> client.callTool("t", Map.of(), RequestOptions.EMPTY));

    assertThat(error).hasMessage("Method not found");
  }

  @Test
  void mapsJsonRpcStringErrorToProtocolExceptionWithDefaultMessage() {
    RuntimeClient client =
        clientRespondingWith(MockResponse.ok(Map.of("jsonrpc", "2.0", "id", 1, "error", "boom")));

    ProtocolException error =
        assertThrows(
            ProtocolException.class, () -> client.callTool("t", Map.of(), RequestOptions.EMPTY));

    assertThat(error).hasMessage("Runtime returned a JSON-RPC error.");
  }

  @Test
  void rejectsEnvelopeWithoutResult() {
    RuntimeClient client = clientRespondingWith(MockResponse.ok(Map.of("jsonrpc", "2.0", "id", 1)));

    ProtocolException error =
        assertThrows(
            ProtocolException.class, () -> client.callTool("t", Map.of(), RequestOptions.EMPTY));

    assertThat(error).hasMessageContaining("no result");
  }

  @Test
  void rejectsPlainStringEnvelope() {
    RuntimeClient client = clientRespondingWith(MockResponse.ok("just text"));

    ProtocolException error =
        assertThrows(
            ProtocolException.class, () -> client.callTool("t", Map.of(), RequestOptions.EMPTY));

    assertThat(error).hasMessageContaining("non-JSON-RPC");
  }

  // ------------------------------------------------------------------------------- SSE

  @Test
  void extractsEnvelopeFromSseWithoutSpaceAfterDataColon() {
    String sse =
        "event: tools/list\n"
            + "data:"
            + Json.write(
                Map.of(
                    "jsonrpc",
                    "2.0",
                    "id",
                    1,
                    "result",
                    Map.of("content", List.of(Map.of("type", "text", "text", "hi")))));
    RuntimeClient client = clientRespondingWith(MockResponse.ok(sse));

    CapabilityResult result = client.callTool("t", Map.of(), RequestOptions.EMPTY);

    assertThat(result.content()).hasSize(1);
    assertThat(result.content().get(0).text()).isEqualTo("hi");
    assertThat(result.isError()).isFalse();
  }

  @Test
  void sseProcessingKeepsOnlyTheLastDataPayload() {
    String first =
        "data: "
            + Json.write(
                Map.of(
                    "jsonrpc",
                    "2.0",
                    "id",
                    1,
                    "result",
                    Map.of("content", List.of(Map.of("type", "text", "text", "first")))));
    String second =
        "data: "
            + Json.write(
                Map.of(
                    "jsonrpc",
                    "2.0",
                    "id",
                    2,
                    "result",
                    Map.of("content", List.of(Map.of("type", "text", "text", "second")))));
    RuntimeClient client = clientRespondingWith(MockResponse.ok(first + "\n" + second));

    CapabilityResult result = client.callTool("t", Map.of(), RequestOptions.EMPTY);

    assertThat(result.content()).hasSize(1);
    assertThat(result.content().get(0).text()).isEqualTo("second");
  }

  @Test
  void sseWithOnlyNonObjectFramesFallsBackToRawBody() {
    RuntimeClient client = clientRespondingWith(MockResponse.ok("data: [1, 2]\nretry: 3s\n"));

    ProtocolException error =
        assertThrows(
            ProtocolException.class, () -> client.callTool("t", Map.of(), RequestOptions.EMPTY));

    assertThat(error).hasMessageContaining("non-JSON-RPC");
  }

  // --------------------------------------------------------------------- call results

  @Test
  void normalizesCallResultContentEdgeCases() {
    RuntimeClient client =
        clientRespondingWith(
            MockResponse.ok(
                envelopeWith(
                    Map.of(
                        "content",
                        List.of(
                            "raw entry",
                            Map.of("text", "no type marker"),
                            Map.of("type", "output"))))));

    CapabilityResult result = client.callTool("t", Map.of(), RequestOptions.EMPTY);

    assertThat(result.content()).hasSize(2);
    assertThat(result.content().get(0).type()).isEqualTo("text");
    assertThat(result.content().get(0).text()).isEqualTo("no type marker");
    assertThat(result.content().get(1).type()).isEqualTo("output");
    assertThat(result.content().get(1).text()).isEqualTo("{\"type\":\"output\"}");
    assertThat(result.isError()).isFalse();
  }

  @Test
  void treatsScalarCallResultAsEmptySuccess() {
    RuntimeClient client = clientRespondingWith(MockResponse.ok(envelopeWith("scalar")));

    CapabilityResult result = client.callTool("t", Map.of(), RequestOptions.EMPTY);

    assertThat(result.content()).isEmpty();
    assertThat(result.isError()).isFalse();
    assertThat(result.structuredContent()).isNull();
  }

  @Test
  void gateBlockWithNoContentUsesFallbackMessage() {
    RuntimeClient client =
        clientRespondingWith(
            MockResponse.ok(
                envelopeWith(
                    Map.of(
                        "isError", true, "structuredContent", Map.of("vinkius_block", "quota")))));

    QuotaException error =
        assertThrows(
            QuotaException.class, () -> client.callTool("t", Map.of(), RequestOptions.EMPTY));

    assertThat(error).hasMessage("Request blocked by the Vinkius runtime.");
  }
}
