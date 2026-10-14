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

import com.vinkius.connect.error.AuthException;
import com.vinkius.connect.error.ConfigException;
import com.vinkius.connect.error.NotFoundException;
import com.vinkius.connect.error.OverageException;
import com.vinkius.connect.error.ProtocolException;
import com.vinkius.connect.error.QuotaException;
import com.vinkius.connect.error.RateLimitException;
import com.vinkius.connect.error.VinkiusErrorCode;
import com.vinkius.connect.error.VinkiusException;
import com.vinkius.connect.model.CapabilityData;
import com.vinkius.connect.model.CapabilityResult;
import com.vinkius.connect.model.ContentItem;
import com.vinkius.connect.model.Hooks;
import com.vinkius.connect.model.RequestOptions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * RuntimeClient — the ONLY surface for tool listing and execution.
 *
 * <p>Vinkius meters, bills, and kill-switches every tool call at the runtime, keyed by the {@code
 * vk_live_*} token embedded in the connection's {@code mcp_url} path. The API is NEVER an execution
 * surface. This client talks JSON-RPC 2.0 over Streamable HTTP directly to {@code
 * {RUNTIME}/{token}/mcp}, statelessly (one POST, no {@code initialize} handshake, no session id).
 *
 * <p>Responsibilities: {@code tools/list} (free, un-metered) and {@code tools/call} (metered) as
 * single POSTs; retries for the idempotent {@code tools/list} only ({@code tools/call} retries
 * solely when the caller supplies an idempotency key); runtime error mapping (revoked token 403 →
 * AuthError, unknown 404 → NotFoundError, transient 5xx → connection error, JSON-RPC errors →
 * ProtocolError); Vinkius-gate blocks ({@code vinkius_block} sentinel → typed Quota/Overage/Rate
 * limits); redacted observability hooks (the {@code vk_live_*} path segment is masked).
 */
public final class RuntimeClient {

  /** JSON-RPC protocol version advertised to the runtime (2026-era stateless). */
  private static final String MCP_PROTOCOL_VERSION = "2026-07-28";

  private final String mcpUrl;
  private final String userAgent;
  private final Transport transport;
  private final AtomicInteger nextId = new AtomicInteger(1);

  /**
   * Immutable runtime (data-plane) client configuration — no API auth: the URL embeds the token.
   */
  public record Config(
      long timeoutMs,
      RetryPolicy retry,
      HttpSender sender,
      String userAgent,
      @Nullable Hooks hooks) {}

  public RuntimeClient(String mcpUrl, Config config) {
    this.mcpUrl = mcpUrl;
    this.userAgent = config.userAgent();
    this.transport =
        new Transport(
            new Transport.Config(
                config.timeoutMs(),
                config.retry(),
                config.sender(),
                config.userAgent(),
                config.hooks()));
  }

  /** List the tools this connection exposes. Free (un-metered) at the runtime. */
  public List<CapabilityData> listTools(RequestOptions options) {
    return Futures.join(listToolsAsync(options));
  }

  /** Async {@link #listTools}. */
  public CompletableFuture<List<CapabilityData>> listToolsAsync(RequestOptions options) {
    return rpcAsync("tools/list", Map.of(), options, true)
        .thenApply(
            result -> {
              Map<String, ?> record = Json.asMap(result);
              Object tools = record == null ? null : record.get("tools");
              if (!(tools instanceof List<?> list)) {
                throw new ProtocolException("Runtime tools/list returned no tools array.", result);
              }
              List<CapabilityData> out = new ArrayList<>(list.size());
              for (Object tool : list) {
                out.add(normalizeToolDefinition(tool));
              }
              return out;
            });
  }

  /** Execute a tool. Metered against the connection's token. */
  public CapabilityResult callTool(
      String name, @Nullable Map<String, Object> args, RequestOptions options) {
    return Futures.join(callToolAsync(name, args, options));
  }

  /** Async {@link #callTool}. */
  public CompletableFuture<CapabilityResult> callToolAsync(
      String name, @Nullable Map<String, Object> args, RequestOptions options) {
    String key = options.idempotencyKey();
    if (key != null && key.isBlank()) {
      return CompletableFuture.failedFuture(
          new ConfigException("Idempotency-Key must be a non-empty, non-whitespace string."));
    }
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("name", name);
    params.put("arguments", args == null ? Map.of() : args);
    // Retries are armed only when the caller opts in with a key (side-effect safety).
    return rpcAsync("tools/call", params, options, key != null)
        .thenApply(RuntimeClient::normalizeCallResult);
  }

  /** Issue one stateless JSON-RPC request, mapping transport + protocol errors. */
  private CompletableFuture<Object> rpcAsync(
      String method, Map<String, Object> params, RequestOptions options, boolean retryable) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("jsonrpc", "2.0");
    payload.put("id", nextId.getAndIncrement());
    payload.put("method", method);
    payload.put("params", params);

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("content-type", "application/json");
    headers.put("accept", "application/json, text/event-stream");
    headers.put("mcp-protocol-version", MCP_PROTOCOL_VERSION);
    headers.put("user-agent", userAgent);
    String key = options.idempotencyKey();
    if (key != null && !key.isEmpty()) {
      headers.put("idempotency-key", key);
    }

    TransportRequest request =
        new TransportRequest(
            mcpUrl,
            "POST",
            headers,
            Json.write(payload),
            retryable,
            "Runtime request",
            options.timeoutMs(),
            options.cancellation());

    return transport
        .send(request)
        .thenApply(
            result -> {
              if (result.status() < 200 || result.status() >= 300) {
                throw mapRuntimeHttpError(result.status(), result.body());
              }
              return unwrapEnvelope(result.body());
            });
  }

  /** Extract the JSON-RPC {@code result}, or map a JSON-RPC {@code error} object. */
  private static Object unwrapEnvelope(@Nullable Object body) {
    // The runtime may answer as an SSE stream; take the last data frame.
    Object envelope = extractEnvelope(body);
    Map<String, ?> record = Json.asMap(envelope);
    if (record == null) {
      throw new ProtocolException("Runtime returned a non-JSON-RPC response.", body);
    }
    Object error = record.get("error");
    if (error != null) {
      Map<String, ?> errorRecord = Json.asMap(error);
      String message =
          errorRecord != null && errorRecord.get("message") instanceof String text
              ? text
              : "Runtime returned a JSON-RPC error.";
      throw new ProtocolException(message, error);
    }
    Object result = record.get("result");
    if (result == null) {
      throw new ProtocolException("Runtime JSON-RPC response has no result.", body);
    }
    return result;
  }

  /** Map a raw runtime tool definition (camelCase) to the SDK's {@link CapabilityData}. */
  private static CapabilityData normalizeToolDefinition(@Nullable Object tool) {
    Map<String, ?> t = Json.asMap(tool);
    Object name = t == null ? null : t.get("name");
    if (!(name instanceof String text) || text.trim().isEmpty()) {
      throw new ProtocolException("Runtime tool definition is missing a name.", tool);
    }
    @Nullable Object inputSchema = null;
    if (t != null) {
      if (t.get("inputSchema") != null) {
        inputSchema = t.get("inputSchema");
      } else if (t.get("input_schema") != null) {
        inputSchema = t.get("input_schema");
      }
    }
    if (inputSchema == null) {
      inputSchema = Map.of();
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> schema = (Map<String, Object>) inputSchema;
    @Nullable Object annotations = t != null ? t.get("annotations") : null;
    return new CapabilityData(
        text,
        t != null && t.get("title") instanceof String title ? title : null,
        t != null && t.get("description") instanceof String description ? description : null,
        schema,
        annotations,
        null,
        null);
  }

  /** Map a runtime {@code tools/call} JSON-RPC result to the SDK's {@link CapabilityResult}. */
  private static CapabilityResult normalizeCallResult(@Nullable Object result) {
    Map<String, ?> r = Json.asMap(result);
    Object rawContent = r != null ? r.get("content") : null;
    List<ContentItem> content = new ArrayList<>();
    if (rawContent instanceof List<?> list) {
      for (Object entry : list) {
        Map<String, ?> item = Json.asMap(entry);
        if (item == null) {
          continue;
        }
        String type = item.get("type") instanceof String t ? t : "text";
        String text = item.get("text") instanceof String t ? t : Json.write(entry);
        content.add(new ContentItem(type, text));
      }
    }
    boolean isError = r != null && Boolean.TRUE.equals(r.get("isError"));
    @Nullable Object structuredContent =
        r != null && r.containsKey("structuredContent") ? r.get("structuredContent") : null;

    // Vinkius-gate blocks (plan quota, overage-blocked payment, tripped circuit breaker) carry a
    // machine-readable `vinkius_block` sentinel in `structuredContent`. Map those to the typed
    // errors so a caller can react (e.g. surface the upgrade URL) instead of parsing the
    // natural-language wall text. A read-only preview block and ordinary upstream tool failures
    // stay in-band as `{ isError: true }`.
    if (isError && Json.isObject(structuredContent)) {
      Map<String, ?> sentinel = Json.asMap(structuredContent);
      if (sentinel != null) {
        Object kind = sentinel.get("vinkius_block");
        String upgradeUrl = sentinel.get("upgrade_url") instanceof String url ? url : null;
        String text =
            content.isEmpty() ? "Request blocked by the Vinkius runtime." : content.get(0).text();
        if ("quota".equals(kind)) {
          throw new QuotaException(text, 0, null, sentinel, upgradeUrl);
        }
        if ("overage".equals(kind)) {
          throw new OverageException(text, 0, null, sentinel, upgradeUrl);
        }
        if ("circuit_breaker".equals(kind)) {
          throw new RateLimitException(text, 0, null, sentinel, null);
        }
      }
    }

    return new CapabilityResult(List.copyOf(content), isError, structuredContent);
  }

  /** Runtime transport errors are plain {@code { error }} bodies (see runtime routes). */
  private static VinkiusException mapRuntimeHttpError(int status, @Nullable Object body) {
    String message = pickRuntimeMessage(body, "Runtime request failed with status " + status);
    if (status == 401 || status == 403) {
      return new AuthException(message, status, null, body);
    }
    if (status == 404) {
      return new NotFoundException(message, status, null, body);
    }
    return new VinkiusException(
        message, VinkiusErrorCode.CONNECTION_ERROR, status, null, body, null);
  }

  private static String pickRuntimeMessage(@Nullable Object body, String fallback) {
    Map<String, ?> record = Json.asMap(body);
    if (record != null) {
      if (record.get("error") instanceof String error && !error.isEmpty()) {
        return error;
      }
      Map<String, ?> err = Json.asMap(record.get("error"));
      if (err != null && err.get("message") instanceof String message && !message.isEmpty()) {
        return message;
      }
      if (record.get("message") instanceof String message && !message.isEmpty()) {
        return message;
      }
    }
    return body instanceof String s && !s.isEmpty() ? s : fallback;
  }

  /** Accept either a JSON object or an SSE stream string; return the JSON-RPC envelope. */
  private static @Nullable Object extractEnvelope(@Nullable Object body) {
    if (!(body instanceof String text)) {
      return body;
    }
    @Nullable Object last = null;
    for (String line : text.split("\n", -1)) {
      String trimmed = line.trim();
      // The SSE spec allows `data:` with OR without a following space.
      if (trimmed.startsWith("data:")) {
        String payloadText = trimmed.substring(5);
        int start = 0;
        while (start < payloadText.length() && payloadText.charAt(start) == ' ') {
          start++;
        }
        @Nullable Object payload = Json.parse(payloadText.substring(start));
        if (Json.isObject(payload)) {
          last = payload;
        }
      }
    }
    return last != null ? last : body;
  }
}
