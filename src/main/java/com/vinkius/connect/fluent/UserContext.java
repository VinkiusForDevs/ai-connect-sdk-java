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
import com.vinkius.connect.core.PooledMap;
import com.vinkius.connect.core.Validators;
import com.vinkius.connect.error.ConnectionException;
import com.vinkius.connect.model.AppUser;
import com.vinkius.connect.model.CapabilityQuery;
import com.vinkius.connect.model.Connection;
import com.vinkius.connect.model.ConnectorStatus;
import com.vinkius.connect.model.ConnectorSummary;
import com.vinkius.connect.model.RequestOptions;
import com.vinkius.connect.resources.AppUsersClient;
import com.vinkius.connect.resources.CreateAppUserInput;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * UserContext — a lazy handle to one end-user's connectors and capabilities.
 *
 * <p>{@code vinkius.user("usr_123")} performs zero network calls and stores nothing but the
 * client's {@code external_id}. Every operation addresses the API by that external_id directly —
 * the SDK never resolves nor holds an internal user id.
 */
public final class UserContext {

  private static final int MAX_FANOUT_CONCURRENCY = 8;

  private final SdkContext ctx;
  private final String externalId;
  private final AppUsersClient users;

  UserContext(SdkContext ctx, String externalId) {
    Validators.assertExternalId(externalId);
    this.ctx = ctx;
    this.externalId = externalId;
    this.users = new AppUsersClient(ctx.http(), ctx.appId());
  }

  /** The end-user's client-supplied external id. */
  public String externalId() {
    return externalId;
  }

  /** Explicitly upsert the user (e.g. to attach metadata up front). Optional. */
  public AppUser ensure(Map<String, Object> metadata) {
    return Futures.join(ensureAsync(metadata));
  }

  /** Explicitly upsert the user (no metadata). */
  public AppUser ensure() {
    return Futures.join(ensureAsync(null));
  }

  /** Async {@link #ensure}. */
  public CompletableFuture<AppUser> ensureAsync(@Nullable Map<String, Object> metadata) {
    CreateAppUserInput input = new CreateAppUserInput(externalId, null, metadata);
    return users.createAsync(input, RequestOptions.EMPTY);
  }

  /** Fetch the underlying user resource. */
  public AppUser get(RequestOptions options) {
    return Futures.join(getAsync(options));
  }

  /** Fetch with default request options. */
  public AppUser get() {
    return get(RequestOptions.EMPTY);
  }

  /** Async {@link #get}. */
  public CompletableFuture<AppUser> getAsync(RequestOptions options) {
    return users.getAsync(externalId, options);
  }

  /** Lazy handle to one connector. */
  public Connector connector(String slug) {
    return new Connector(ctx, this, slug);
  }

  /** Connected connectors for this user, with derived status. */
  public List<ConnectorSummary> connectors(RequestOptions options) {
    return Futures.join(connectorsAsync(options));
  }

  /** Connectors with default request options. */
  public List<ConnectorSummary> connectors() {
    return connectors(RequestOptions.EMPTY);
  }

  /** Async {@link #connectors}. */
  public CompletableFuture<List<ConnectorSummary>> connectorsAsync(RequestOptions options) {
    return users
        .connections(externalId)
        .listAsync(options)
        .thenApply(
            connections -> {
              List<ConnectorSummary> summaries = new ArrayList<>(connections.size());
              for (Connection connection : connections) {
                summaries.add(StatusUtil.summarize(connection));
              }
              return summaries;
            });
  }

  /**
   * Aggregated, executable capabilities across the user's ready connectors.
   *
   * <p>Tools are listed and executed exclusively at the MCP runtime — the sole metered, revocable
   * surface. Because each connection is billed and kill-switched independently by its own {@code
   * vk_live_*} token, aggregation is a fan-out over the connected connectors (each resolves its own
   * runtime), not a single API call. Only {@code ready} connectors are included; the rest cannot
   * list tools until connected/credentialed.
   *
   * <p>The connection list is fetched ONCE and each connector handle reuses the connection id
   * already resolved by {@link StatusUtil#summarize} — no per-connector re-listing. For a single
   * connector, prefer {@code user.connector(slug).capabilities()} — it avoids listing every
   * connection.
   */
  public CapabilitySet capabilities(CapabilityQuery query) {
    return Futures.join(capabilitiesAsync(query));
  }

  /** Aggregated capabilities with no filters. */
  public CapabilitySet capabilities() {
    return capabilities(CapabilityQuery.builder().build());
  }

  /** Async {@link #capabilities}. */
  public CompletableFuture<CapabilitySet> capabilitiesAsync(CapabilityQuery query) {
    Set<String> include = query.include().isEmpty() ? null : new HashSet<>(query.include());
    Set<String> exclude = query.exclude().isEmpty() ? null : new HashSet<>(query.exclude());

    return connectorsAsync(query)
        .thenCompose(
            summaries -> {
              List<ConnectorSummary> targets = new ArrayList<>();
              for (ConnectorSummary summary : summaries) {
                if (summary.status() != ConnectorStatus.READY) {
                  continue;
                }
                if (include != null && !include.contains(summary.slug())) {
                  continue;
                }
                if (exclude != null && exclude.contains(summary.slug())) {
                  continue;
                }
                targets.add(summary);
              }

              // Failure-tolerant, concurrency-limited fan-out: one flaky runtime must not sink the
              // whole aggregation (all-failed still throws), and N connectors do not open N
              // simultaneous sockets.
              AtomicReference<Throwable> firstError = new AtomicReference<>();
              CompletableFuture<List<CapabilitySet>> settled =
                  PooledMap.pooledMap(
                      targets,
                      MAX_FANOUT_CONCURRENCY,
                      (summary, index) -> {
                        String knownConnectionId = summary.connectionId();
                        CompletableFuture<CapabilitySet> attempt =
                            knownConnectionId != null
                                ? connector(summary.slug())
                                    .capabilitiesForConnectionAsync(knownConnectionId, query)
                                : connector(summary.slug()).capabilitiesAsync(query);
                        var onConnectorError = query.onConnectorError();
                        return attempt.handle(
                            (set, error) -> {
                              if (error != null) {
                                Throwable root = Futures.unwrap(error);
                                firstError.compareAndSet(null, root);
                                if (onConnectorError != null) {
                                  onConnectorError.accept(summary.slug(), root);
                                }
                                return null;
                              }
                              return set;
                            });
                      });

              return settled.thenApply(
                  sets -> {
                    int failures = 0;
                    List<Capability> merged = new ArrayList<>();
                    for (CapabilitySet set : sets) {
                      if (set == null) {
                        failures++;
                      } else {
                        merged.addAll(set.asList());
                      }
                    }
                    Throwable root = firstError.get();
                    if (failures > 0 && failures == targets.size() && root != null) {
                      if (root instanceof RuntimeException runtime) {
                        throw runtime;
                      }
                      if (root instanceof Error error) {
                        throw error;
                      }
                      throw new ConnectionException(String.valueOf(root.getMessage()), root);
                    }
                    return CapabilitySet.fromCapabilities(merged);
                  });
            });
  }
}
