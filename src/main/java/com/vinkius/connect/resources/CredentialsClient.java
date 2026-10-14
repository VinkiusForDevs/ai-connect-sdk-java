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
import com.vinkius.connect.core.Json;
import com.vinkius.connect.core.UriUtil;
import com.vinkius.connect.core.Validators;
import com.vinkius.connect.model.CredentialStatus;
import com.vinkius.connect.model.RequestOptions;
import java.util.concurrent.CompletableFuture;

/**
 * Connection credentials (low-level, 1:1 with the API).
 *
 * <p>Values are write-only: the API never returns them, only which keys are set. NOTE the low-level
 * {@link #set} takes the raw HTTP envelope {@code { credentials: {...}, merge? }}; the high-level
 * {@code CredentialsHandle} accepts the flat map instead.
 */
public final class CredentialsClient {

  private final HttpClient http;
  private final String appId;
  private final String externalId;
  private final String connectionId;

  public CredentialsClient(HttpClient http, String appId, String externalId, String connectionId) {
    Validators.assertExternalId(externalId);
    this.http = http;
    this.appId = appId;
    this.externalId = externalId;
    this.connectionId = connectionId;
  }

  private String base() {
    return "/apps/"
        + UriUtil.encodeSegment(appId)
        + "/users/"
        + UriUtil.encodeSegment(externalId)
        + "/mcps/"
        + UriUtil.encodeSegment(connectionId)
        + "/credentials";
  }

  /** Which credential keys are configured (never the values). */
  public CompletableFuture<CredentialStatus> statusAsync(RequestOptions options) {
    return http.<Object>getAsync(base(), options)
        .thenApply(body -> Json.convert(body, CredentialStatus.class));
  }

  /** Blocking {@link #statusAsync}. */
  public CredentialStatus status(RequestOptions options) {
    return Futures.join(statusAsync(options));
  }

  /** Status with default request options. */
  public CredentialStatus status() {
    return status(RequestOptions.EMPTY);
  }

  /** Async {@link #status} with default request options. */
  public CompletableFuture<CredentialStatus> statusAsync() {
    return statusAsync(RequestOptions.EMPTY);
  }

  /** Store credentials, validated server-side. {@code merge} updates only the sent keys. */
  public CompletableFuture<CredentialStatus> setAsync(
      SetCredentialsInput input, RequestOptions options) {
    return http.<Object>putAsync(base(), input, options)
        .thenApply(body -> Json.convert(body, CredentialStatus.class));
  }

  /** Blocking {@link #setAsync}. */
  public CredentialStatus set(SetCredentialsInput input, RequestOptions options) {
    return Futures.join(setAsync(input, options));
  }

  /** Store with default request options. */
  public CredentialStatus set(SetCredentialsInput input) {
    return set(input, RequestOptions.EMPTY);
  }

  /** Async {@link #set} with default request options. */
  public CompletableFuture<CredentialStatus> setAsync(SetCredentialsInput input) {
    return setAsync(input, RequestOptions.EMPTY);
  }
}
