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
import com.vinkius.connect.model.IssuedConnectionToken;
import com.vinkius.connect.model.RequestOptions;
import java.util.concurrent.CompletableFuture;

/**
 * Connection tokens (low-level, 1:1 with the API).
 *
 * <p>A token is the {@code vk_live_*} data-plane credential bound to one connection. The API
 * returns its plaintext AND its runtime {@code mcp_url} ONLY on this create call — they are never
 * re-fetchable. Every tool call through the runtime is metered against this token, and the user can
 * disable/delete it (kill switch).
 *
 * <p>The SDK provisions exactly one token per connection at connect() time and remembers its
 * runtime URL; it never silently re-mints on execution, so a revoked token fails closed instead of
 * quietly billing a fresh one.
 */
public final class ConnectionTokensClient {

  private final HttpClient http;
  private final String appId;
  private final String externalId;
  private final String connectionId;

  public ConnectionTokensClient(
      HttpClient http, String appId, String externalId, String connectionId) {
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
        + "/tokens";
  }

  /**
   * Mint a data-plane token; the response carries the one-time plaintext + mcp_url. Not retry-safe:
   * each POST mints a distinct token — never auto-retried, even with a key.
   */
  public CompletableFuture<IssuedConnectionToken> issueAsync(
      IssueTokenInput input, RequestOptions options) {
    return http.<Object>postAsync(base(), input, false, options)
        .thenApply(body -> Pagination.unwrapItem(body, IssuedConnectionToken.class));
  }

  /** Blocking {@link #issueAsync}. */
  public IssuedConnectionToken issue(IssueTokenInput input, RequestOptions options) {
    return Futures.join(issueAsync(input, options));
  }

  /** Mint a token with default request options (never auto-retried). */
  public IssuedConnectionToken issue(IssueTokenInput input) {
    return issue(input, RequestOptions.EMPTY);
  }

  /** Async {@link #issue} with default request options. */
  public CompletableFuture<IssuedConnectionToken> issueAsync(IssueTokenInput input) {
    return issueAsync(input, RequestOptions.EMPTY);
  }

  /** Mint a token without a label. */
  public IssuedConnectionToken issue(RequestOptions options) {
    return issue(IssueTokenInput.unnamed(), options);
  }
}
