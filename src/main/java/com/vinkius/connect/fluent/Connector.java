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
import com.vinkius.connect.core.RuntimeClient;
import com.vinkius.connect.error.ConnectorNotConnectedException;
import com.vinkius.connect.error.ProtocolException;
import com.vinkius.connect.model.CapabilityData;
import com.vinkius.connect.model.Connection;
import com.vinkius.connect.model.ConnectorStatus;
import com.vinkius.connect.model.IssuedConnectionToken;
import com.vinkius.connect.model.RequestOptions;
import com.vinkius.connect.resources.ConnectionsClient;
import com.vinkius.connect.resources.CreateConnectionInput;
import com.vinkius.connect.resources.IssueTokenInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Connector — a lazy handle to one connector for a user.
 *
 * <p>{@code user.connector("github")} performs no network call. The connection is addressed by the
 * user's {@code external_id} directly; the connection id is resolved on demand and memoized for the
 * handle.
 */
public final class Connector {

  private final Object lock = new Object();
  private final SdkContext ctx;
  private final UserContext user;
  private final String slug;

  /** Credential management for this connector (operates on an existing connection). */
  private final CredentialsHandle credentials;

  private volatile @Nullable String connectionId;

  /** Runtime client for this connection — the sole execution surface. Memoized. */
  private volatile @Nullable RuntimeClient runtimeClient;

  /** Connection id the memoized runtime client was provisioned for. */
  private volatile @Nullable String provisionedFor;

  private volatile @Nullable String provisionedUrl;

  /**
   * In-flight (or settled) token provisioning, memoized as a future so concurrent callers share one
   * mint instead of racing to create tokens.
   */
  private @Nullable CompletableFuture<String> provisioning;

  Connector(SdkContext ctx, UserContext user, String slug) {
    this.ctx = ctx;
    this.user = user;
    this.slug = slug;
    this.credentials = new CredentialsHandle(ctx, user, this);
  }

  /** The connector's catalog slug (or the id this handle was created with). */
  public String slug() {
    return slug;
  }

  /** Credential management for this connector. */
  public CredentialsHandle credentials() {
    return credentials;
  }

  private ConnectionsClient connections() {
    return new ConnectionsClient(ctx.http(), ctx.appId(), user.externalId());
  }

  /**
   * Connect this connector for the user (get-or-create; idempotent), then provision the
   * connection's {@code vk_live_*} data-plane token. The returned {@link Connection} carries {@code
   * runtime_url} — the sole surface for listing/executing this connection's tools, metered and
   * revocable. Callers should persist it; the URL (and its embedded token) is returned ONLY here.
   */
  public Connection connect(RequestOptions options) {
    return Futures.join(connectAsync(options));
  }

  /** Connect with default request options. */
  public Connection connect() {
    return connect(RequestOptions.EMPTY);
  }

  /** Async {@link #connect}. */
  public CompletableFuture<Connection> connectAsync(RequestOptions options) {
    return connections()
        .createAsync(new CreateConnectionInput(slug), options)
        .thenApply(
            connection -> {
              this.connectionId = connection.id();
              return connection;
            })
        .thenCompose(
            connection ->
                provisionRuntimeAsync(connection.id(), options)
                    .thenApply(connection::withRuntimeUrl));
  }

  /** Disconnect this connector for the user. */
  public void disconnect(RequestOptions options) {
    Futures.join(disconnectAsync(options));
  }

  /** Disconnect with default request options. */
  public void disconnect() {
    disconnect(RequestOptions.EMPTY);
  }

  /** Async {@link #disconnect}. */
  public CompletableFuture<Void> disconnectAsync(RequestOptions options) {
    return resolveConnectionIdAsync(options)
        .thenCompose(connectionId -> connections().deleteAsync(connectionId, options))
        .thenRun(
            () -> {
              synchronized (lock) {
                this.connectionId = null;
                this.runtimeClient = null;
                this.provisionedFor = null;
                this.provisionedUrl = null;
                this.provisioning = null;
              }
            });
  }

  /** Current readiness state. */
  public ConnectorStatus status(RequestOptions options) {
    return Futures.join(statusAsync(options));
  }

  /** Status with default request options. */
  public ConnectorStatus status() {
    return status(RequestOptions.EMPTY);
  }

  /** Async {@link #status}. */
  public CompletableFuture<ConnectorStatus> statusAsync(RequestOptions options) {
    return findConnectionAsync(options)
        .thenApply(
            connection ->
                connection != null
                    ? StatusUtil.deriveStatus(connection)
                    : ConnectorStatus.NOT_CONNECTED);
  }

  /** Capabilities exposed by this connector, listed directly from the runtime. */
  public CapabilitySet capabilities(RequestOptions options) {
    return Futures.join(capabilitiesAsync(options));
  }

  /** Capabilities with default request options. */
  public CapabilitySet capabilities() {
    return capabilities(RequestOptions.EMPTY);
  }

  /** Async {@link #capabilities}. */
  public CompletableFuture<CapabilitySet> capabilitiesAsync(RequestOptions options) {
    return resolveRuntimeAsync(options)
        .thenCompose(
            resolved -> listCapabilities(resolved.runtime(), resolved.connectionId(), options));
  }

  /**
   * List capabilities for an ALREADY-KNOWN connection id — skips the connection lookup entirely
   * (used by the {@code user.capabilities()} fan-out).
   */
  public CapabilitySet capabilitiesForConnection(String connectionId, RequestOptions options) {
    return Futures.join(capabilitiesForConnectionAsync(connectionId, options));
  }

  /** Async {@link #capabilitiesForConnection}. */
  public CompletableFuture<CapabilitySet> capabilitiesForConnectionAsync(
      String connectionId, RequestOptions options) {
    this.connectionId = connectionId;
    return resolveRuntimeAsync(options)
        .thenCompose(
            resolved -> listCapabilities(resolved.runtime(), resolved.connectionId(), options));
  }

  private CompletableFuture<CapabilitySet> listCapabilities(
      RuntimeClient runtime, String connectionId, RequestOptions options) {
    return runtime
        .listToolsAsync(options)
        .thenApply(
            raw -> {
              Capability.CapabilityExecutor executor = Executor.make(runtime);
              Capability.BuildCapabilityContext buildCtx =
                  new Capability.BuildCapabilityContext(
                      slug, connectionId, ctx.namespace(), executor);
              List<Capability> capabilities = new ArrayList<>(raw.size());
              for (CapabilityData data : raw) {
                capabilities.add(Capability.build(data, buildCtx));
              }
              return CapabilitySet.fromCapabilities(capabilities);
            });
  }

  /** Resolve (and memoize) this connector's connection id, or throw if not connected. */
  public String resolveConnectionId(RequestOptions options) {
    return Futures.join(resolveConnectionIdAsync(options));
  }

  /** Async {@link #resolveConnectionId}. */
  public CompletableFuture<String> resolveConnectionIdAsync(RequestOptions options) {
    String current = connectionId;
    if (current != null) {
      return CompletableFuture.completedFuture(current);
    }
    return findConnectionAsync(options)
        .thenApply(
            connection -> {
              if (connection == null) {
                throw new ConnectorNotConnectedException(
                    "Connector \""
                        + slug
                        + "\" is not connected for this user — call connect() first.");
              }
              return connection.id();
            });
  }

  /**
   * Resolve the runtime client for this connection, provisioning a token on demand. Memoized per
   * handle so a single connect()/capabilities() flow mints at most one token — including under
   * concurrency: the in-flight provisioning future is shared, so parallel callers never race into a
   * double mint.
   */
  private CompletableFuture<Resolved> resolveRuntimeAsync(RequestOptions options) {
    return resolveConnectionIdAsync(options)
        .thenCompose(
            connectionId -> {
              RuntimeClient current = runtimeClient;
              if (current != null) {
                return CompletableFuture.completedFuture(new Resolved(current, connectionId));
              }
              return provisionRuntimeAsync(connectionId, options)
                  .thenApply(
                      url -> {
                        RuntimeClient provisioned = runtimeClient;
                        if (provisioned == null) {
                          throw new ProtocolException(
                              "Token provisioning did not return a runtime client.",
                              Map.of("connectionId", connectionId, "mcpUrl", url));
                        }
                        return new Resolved(provisioned, connectionId);
                      });
            });
  }

  /** Mint the connection's data-plane token and memoize a runtime client from its mcp_url. */
  @SuppressWarnings({"FutureReturnValueIgnored", "ReferenceEquality"})
  private CompletableFuture<String> provisionRuntimeAsync(
      String connectionId, RequestOptions options) {
    // Memo hits are keyed by connection id: a NEW connection (e.g. after a delete + reconnect)
    // must never inherit the previous token's mcp_url.
    synchronized (lock) {
      String memoUrl = provisionedUrl;
      if (connectionId.equals(provisionedFor) && memoUrl != null) {
        return CompletableFuture.completedFuture(memoUrl);
      }
      if (provisioning != null) {
        return provisioning;
      }

      CompletableFuture<String> attempt =
          connections()
              .tokens(connectionId)
              .issueAsync(IssueTokenInput.unnamed(), options)
              .thenApply(issued -> applyProvisioned(connectionId, issued));

      provisioning = attempt;
      // A failed mint must not poison the handle: clear so a later call retries.
      attempt.whenComplete(
          (url, error) -> {
            if (error != null) {
              synchronized (lock) {
                if (provisioning == attempt) {
                  provisioning = null;
                }
              }
            }
          });
      return attempt;
    }
  }

  private String applyProvisioned(String connectionId, IssuedConnectionToken issued) {
    String url = issued.mcpUrl();
    if (url == null) {
      throw new ProtocolException(
          "Token provisioning did not return a runtime URL (mcp_url).",
          Map.of("connectionId", connectionId));
    }
    synchronized (lock) {
      this.runtimeClient = ctx.runtime().apply(url);
      this.provisionedFor = connectionId;
      this.provisionedUrl = url;
    }
    return url;
  }

  private CompletableFuture<@Nullable Connection> findConnectionAsync(RequestOptions options) {
    return connections()
        .listAsync(options)
        .thenApply(
            connections -> {
              Connection match = null;
              for (Connection connection : connections) {
                boolean slugMatches =
                    connection.slug() != null && connection.slug().equals(this.slug);
                if (slugMatches || connection.id().equals(this.slug)) {
                  match = connection;
                  break;
                }
              }
              this.connectionId = match != null ? match.id() : null;
              return match;
            });
  }

  private record Resolved(RuntimeClient runtime, String connectionId) {}
}
