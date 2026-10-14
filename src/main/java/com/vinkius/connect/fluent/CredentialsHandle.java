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
import com.vinkius.connect.model.CatalogConnectorDetail;
import com.vinkius.connect.model.CredentialField;
import com.vinkius.connect.model.CredentialStatus;
import com.vinkius.connect.model.RequestOptions;
import com.vinkius.connect.resources.CatalogClient;
import com.vinkius.connect.resources.CredentialsClient;
import com.vinkius.connect.resources.SetCredentialsInput;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * CredentialsHandle — high-level credential management for one connection.
 *
 * <p>Operates on an EXISTING connection (it never connects implicitly). The flat {@code
 * set(values)} map is wrapped into the HTTP {@code { credentials: values }} envelope so the wire
 * shape never leaks into the SDK surface.
 */
public final class CredentialsHandle {

  private final SdkContext ctx;
  private final UserContext user;
  private final Connector connector;

  CredentialsHandle(SdkContext ctx, UserContext user, Connector connector) {
    this.ctx = ctx;
    this.user = user;
    this.connector = connector;
  }

  /**
   * The credential schema this connector requires (from the catalog). Cached (non-secret, stable
   * data — per the cache golden rule, no credential VALUES ever pass through here, only field
   * descriptors).
   */
  public Map<String, CredentialField> schema(RequestOptions options) {
    return Futures.join(schemaAsync(options));
  }

  /** Schema with default request options. */
  public Map<String, CredentialField> schema() {
    return schema(RequestOptions.EMPTY);
  }

  /** Async {@link #schema}. */
  public CompletableFuture<Map<String, CredentialField>> schemaAsync(RequestOptions options) {
    String cacheKey = "credential-schema:" + connector.slug();
    Object cached = ctx.cache().get(cacheKey);
    if (cached != null) {
      return CompletableFuture.completedFuture(castSchema(cached));
    }
    return new CatalogClient(ctx.http())
        .getAsync(connector.slug(), options)
        .thenApply(CatalogConnectorDetail::credentialSchema)
        .thenApply(
            schemaValue -> {
              ctx.cache().set(cacheKey, schemaValue);
              return schemaValue;
            });
  }

  @SuppressWarnings("unchecked")
  private static Map<String, CredentialField> castSchema(Object value) {
    return (Map<String, CredentialField>) value;
  }

  /** Which credential keys are configured (never the values). */
  public CredentialStatus status(RequestOptions options) {
    return Futures.join(statusAsync(options));
  }

  /** Status with default request options. */
  public CredentialStatus status() {
    return status(RequestOptions.EMPTY);
  }

  /** Async {@link #status}. */
  public CompletableFuture<CredentialStatus> statusAsync(RequestOptions options) {
    return connector
        .resolveConnectionIdAsync(options)
        .thenCompose(connectionId -> client(connectionId).statusAsync(options));
  }

  /**
   * Store credentials, validated server-side. Pass {@link
   * SetCredentialsOptions.Builder#merge(boolean) merge(true)} to update only the keys you send.
   */
  public CredentialStatus set(Map<String, String> values, SetCredentialsOptions options) {
    return Futures.join(setAsync(values, options));
  }

  /** Store credentials with default options (replace semantics). */
  public CredentialStatus set(Map<String, String> values) {
    return set(values, SetCredentialsOptions.EMPTY);
  }

  /** Async {@link #set}. */
  public CompletableFuture<CredentialStatus> setAsync(
      Map<String, String> values, SetCredentialsOptions options) {
    return connector
        .resolveConnectionIdAsync(options)
        .thenCompose(
            connectionId ->
                client(connectionId)
                    .setAsync(new SetCredentialsInput(values, options.merge()), options));
  }

  private CredentialsClient client(String connectionId) {
    return new CredentialsClient(ctx.http(), ctx.appId(), user.externalId(), connectionId);
  }
}
