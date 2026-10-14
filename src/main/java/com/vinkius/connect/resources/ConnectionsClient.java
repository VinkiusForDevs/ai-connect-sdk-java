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
package com.vinkius.connect.resources;

import com.vinkius.connect.core.Futures;
import com.vinkius.connect.core.HttpClient;
import com.vinkius.connect.core.Pagination;
import com.vinkius.connect.core.UriUtil;
import com.vinkius.connect.core.Validators;
import com.vinkius.connect.model.Connection;
import com.vinkius.connect.model.RequestOptions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Connections for a user (low-level, 1:1 with the API).
 *
 * <p>A connection is an established connector for a user. Creating one connects the connector
 * (get-or-create user + connection; idempotent per user+connector). The API transport path is
 * {@code /apps/{app}/users/{externalId}/mcps} (the Vinkius data plane is MCP-based internally); the
 * SDK exposes it as "connections".
 */
public final class ConnectionsClient {

  private final HttpClient http;
  private final String appId;
  private final String externalId;

  public ConnectionsClient(HttpClient http, String appId, String externalId) {
    Validators.assertExternalId(externalId);
    this.http = http;
    this.appId = appId;
    this.externalId = externalId;
  }

  private String base() {
    return "/apps/"
        + UriUtil.encodeSegment(appId)
        + "/users/"
        + UriUtil.encodeSegment(externalId)
        + "/mcps";
  }

  /** List the user's connections (not paginated; empty for an unknown user). */
  public CompletableFuture<List<Connection>> listAsync(RequestOptions options) {
    return http.<Object>getAsync(base(), options)
        .thenApply(body -> Pagination.unwrapList(body, Connection.class));
  }

  /** Blocking {@link #listAsync}. */
  public List<Connection> list(RequestOptions options) {
    return Futures.join(listAsync(options));
  }

  /** List with default request options. */
  public List<Connection> list() {
    return list(RequestOptions.EMPTY);
  }

  /** Async {@link #list} with default request options. */
  public CompletableFuture<List<Connection>> listAsync() {
    return listAsync(RequestOptions.EMPTY);
  }

  /** Connect a catalog connector for the user (get-or-create; idempotent). */
  public CompletableFuture<Connection> createAsync(
      CreateConnectionInput input, RequestOptions options) {
    Map<String, Object> body = Map.of("catalog_mcp_id", input.connector());
    return http.<Object>postAsync(base(), body, true, options)
        .thenApply(value -> Pagination.unwrapItem(value, Connection.class));
  }

  /** Blocking {@link #createAsync}. */
  public Connection create(CreateConnectionInput input, RequestOptions options) {
    return Futures.join(createAsync(input, options));
  }

  /** Connect with default request options. */
  public Connection create(CreateConnectionInput input) {
    return create(input, RequestOptions.EMPTY);
  }

  /** Fetch one connection by id. */
  public CompletableFuture<Connection> getAsync(String connectionId, RequestOptions options) {
    return http.<Object>getAsync(base() + "/" + UriUtil.encodeSegment(connectionId), options)
        .thenApply(body -> Pagination.unwrapItem(body, Connection.class));
  }

  /** Blocking {@link #getAsync}. */
  public Connection get(String connectionId, RequestOptions options) {
    return Futures.join(getAsync(connectionId, options));
  }

  /** Fetch with default request options. */
  public Connection get(String connectionId) {
    return get(connectionId, RequestOptions.EMPTY);
  }

  /** Disconnect (delete) a connection by id. */
  public CompletableFuture<Void> deleteAsync(String connectionId, RequestOptions options) {
    return http.<Object>deleteAsync(base() + "/" + UriUtil.encodeSegment(connectionId), options)
        .thenApply(ignored -> null);
  }

  /** Blocking {@link #deleteAsync}. */
  public void delete(String connectionId, RequestOptions options) {
    Futures.join(deleteAsync(connectionId, options));
  }

  /** Disconnect with default request options. */
  public void delete(String connectionId) {
    delete(connectionId, RequestOptions.EMPTY);
  }

  /** Credentials client for one connection. */
  public CredentialsClient credentials(String connectionId) {
    return new CredentialsClient(http, appId, externalId, connectionId);
  }

  /** Data-plane token management for this connection (mints the vk_live_* + mcp_url). */
  public ConnectionTokensClient tokens(String connectionId) {
    return new ConnectionTokensClient(http, appId, externalId, connectionId);
  }
}
