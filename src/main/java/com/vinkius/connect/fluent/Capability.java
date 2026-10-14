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

import com.vinkius.connect.core.Futures;
import com.vinkius.connect.error.ProtocolException;
import com.vinkius.connect.model.CapabilityData;
import com.vinkius.connect.model.CapabilityResult;
import com.vinkius.connect.model.ExecuteOptions;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

/**
 * Capability — an executable, framework-neutral unit of a user's connectors.
 *
 * <p>Execution goes straight to the MCP runtime (the sole execution surface); the API is never an
 * execution surface.
 */
public final class Capability {

  private final CapabilityExecutor executor;
  private final String connector;
  private final String connectionId;
  private final String name;
  private final String rawName;
  private final @Nullable String title;
  private final String description;
  private final Map<String, Object> inputSchema;

  private Capability(CapabilityExecutor executor, CapabilityInit init) {
    this.executor = executor;
    this.connector = init.connector();
    this.connectionId = init.connectionId();
    this.name = init.name();
    this.rawName = init.rawName();
    this.title = init.title();
    this.description = init.description();
    this.inputSchema = Collections.unmodifiableMap(new LinkedHashMap<>(init.inputSchema()));
  }

  /**
   * Executes a capability at the MCP runtime — the sole execution surface. The runtime target (a
   * connection's {@code mcp_url}, which embeds its {@code vk_live_*} token) is bound into the
   * executor when the capability is built, so this signature carries no routing coordinates.
   */
  @FunctionalInterface
  public interface CapabilityExecutor {

    /** Execute {@code rawName} at the bound runtime (async form). */
    CompletableFuture<CapabilityResult> execute(
        String rawName, @Nullable Map<String, Object> args, ExecuteOptions options);
  }

  /**
   * Routing + naming inputs for {@link Capability#build}.
   *
   * @param connector slug of the connector this capability belongs to
   * @param connectionId connection id used to route execution
   * @param namespace function producing the display name from (connector, rawName)
   * @param executor the bound execution function
   */
  public record BuildCapabilityContext(
      String connector,
      String connectionId,
      BiFunction<String, String, String> namespace,
      CapabilityExecutor executor) {}

  private record CapabilityInit(
      String connector,
      String connectionId,
      String name,
      String rawName,
      @Nullable String title,
      String description,
      Map<String, Object> inputSchema) {}

  /** Slug of the connector this capability belongs to. */
  public String connector() {
    return connector;
  }

  /** Connection id used to route execution. */
  public String connectionId() {
    return connectionId;
  }

  /** Display name, namespaced across connectors (e.g. {@code github__create_issue}). */
  public String name() {
    return name;
  }

  /** Raw name as the connector exposes it (e.g. {@code create_issue}). */
  public String rawName() {
    return rawName;
  }

  /** Human-readable title, when the connector provides one. */
  public @Nullable String title() {
    return title;
  }

  /** Human-readable description (empty when absent). */
  public String description() {
    return description;
  }

  /** JSON Schema for the capability's arguments (empty object when absent). */
  public Map<String, Object> inputSchema() {
    return inputSchema;
  }

  /**
   * Execute this capability at the runtime (blocking).
   *
   * <p>An upstream tool failure is returned as {@code isError} (never thrown). A Vinkius-gate
   * block, however, throws a typed error the moment the runtime stops the call before execution:
   * plan quota → {@link com.vinkius.connect.error.QuotaException}, a declined overage payment →
   * {@link com.vinkius.connect.error.OverageException}, a tripped circuit breaker → {@link
   * com.vinkius.connect.error.RateLimitException}.
   */
  public CapabilityResult execute(@Nullable Map<String, Object> args, ExecuteOptions options) {
    return Futures.join(executeAsync(args, options));
  }

  /** Execute with default options. */
  public CapabilityResult execute(@Nullable Map<String, Object> args) {
    return execute(args, ExecuteOptions.EMPTY);
  }

  /** Execute with no arguments and default options. */
  public CapabilityResult execute() {
    return execute(null, ExecuteOptions.EMPTY);
  }

  /** Async {@link #execute}. */
  public CompletableFuture<CapabilityResult> executeAsync(
      @Nullable Map<String, Object> args, ExecuteOptions options) {
    return executor.execute(rawName, args, options);
  }

  /** Build a {@link Capability} from a raw {@link CapabilityData} payload. */
  public static Capability build(CapabilityData data, BuildCapabilityContext ctx) {
    String rawName = data.name();
    if (rawName == null || rawName.trim().isEmpty()) {
      throw new ProtocolException("Capability payload is missing a non-empty name.", data);
    }
    if (ctx.connector().isEmpty() || ctx.connectionId().isEmpty()) {
      throw new ProtocolException(
          "Capability payload is missing connector routing metadata.",
          Map.of("connector", ctx.connector(), "connectionId", ctx.connectionId()));
    }
    return new Capability(
        ctx.executor(),
        new CapabilityInit(
            ctx.connector(),
            ctx.connectionId(),
            ctx.namespace().apply(ctx.connector(), rawName),
            rawName,
            data.title(),
            data.description() != null ? data.description() : "",
            data.inputSchema() != null ? data.inputSchema() : Map.of()));
  }
}
