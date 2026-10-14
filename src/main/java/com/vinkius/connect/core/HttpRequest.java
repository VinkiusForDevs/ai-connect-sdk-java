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

import com.vinkius.connect.model.RequestOptions;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One control-plane API call: method, path, query, optional JSON-serializable body, the tri-state
 * idempotency flag and per-request options.
 *
 * <p>{@code idempotent} wins outright when set. When {@code null}, a request is retryable if its
 * method is naturally idempotent (GET/PUT/DELETE) OR it carries a non-empty idempotency key.
 * Genuinely non-idempotent requests (e.g. {@code tokens.issue}) are hard-flagged {@code false} and
 * never auto-retried regardless of any key.
 */
public record HttpRequest(
    String method,
    String path,
    Map<String, Object> query,
    @Nullable Object body,
    @Nullable Boolean idempotent,
    Map<String, String> headers,
    RequestOptions options) {

  public HttpRequest {
    query = query == null ? Map.of() : Map.copyOf(query);
    headers = headers == null ? Map.of() : Map.copyOf(headers);
    options = options == null ? RequestOptions.EMPTY : options;
  }

  /** Builder for {@link HttpRequest}. */
  public static Builder builder(String method, String path) {
    return new Builder(method, path);
  }

  /** Fluent builder for {@link HttpRequest}. */
  public static final class Builder {
    private final String method;
    private final String path;
    private Map<String, Object> query = Map.of();
    private @Nullable Object body;
    private @Nullable Boolean idempotent;
    private Map<String, String> headers = Map.of();
    private RequestOptions options = RequestOptions.EMPTY;

    private Builder(String method, String path) {
      this.method = method;
      this.path = path;
    }

    public Builder query(Map<String, Object> query) {
      this.query = query;
      return this;
    }

    public Builder query(String key, @Nullable Object value) {
      Map<String, Object> next = new LinkedHashMap<>(this.query);
      if (value != null) {
        next.put(key, value);
      }
      this.query = next;
      return this;
    }

    public Builder body(@Nullable Object body) {
      this.body = body;
      return this;
    }

    public Builder idempotent(boolean idempotent) {
      this.idempotent = idempotent;
      return this;
    }

    public Builder headers(Map<String, String> headers) {
      this.headers = headers;
      return this;
    }

    public Builder options(RequestOptions options) {
      this.options = options;
      return this;
    }

    public HttpRequest build() {
      return new HttpRequest(method, path, query, body, idempotent, headers, options);
    }
  }
}
