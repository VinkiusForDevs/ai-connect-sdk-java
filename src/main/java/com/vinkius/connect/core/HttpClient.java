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

import com.vinkius.connect.error.ConfigException;
import com.vinkius.connect.error.HttpErrorMapper;
import com.vinkius.connect.error.VinkiusException;
import com.vinkius.connect.model.Hooks;
import com.vinkius.connect.model.RequestOptions;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * The control-plane HTTP client: bearer auth with the {@code vk_app_sk_*} key (never logged).
 *
 * <p>Responsibilities: automatic retries (idempotent requests only) with full-jitter backoff
 * honoring {@code Retry-After}; consistent error mapping (see {@link HttpErrorMapper}); redacted
 * observability hooks. Transport mechanics (timeout, abort composition, retries, hooks) live in
 * {@link Transport} and are shared with the data-plane {@link RuntimeClient}.
 */
public final class HttpClient {

  /** Header carrying the public app id — the API authenticates it against the key. */
  private static final String APP_ID_HEADER = "x-vinkius-app-id";

  private final String baseUrl;
  private final String apiKey;
  private final String appId;
  private final String userAgent;
  private final Transport transport;

  /** Immutable control-plane client configuration. */
  public record Config(
      String baseUrl,
      String apiKey,
      String appId,
      long timeoutMs,
      RetryPolicy retry,
      HttpSender sender,
      String userAgent,
      @Nullable Hooks hooks) {}

  public HttpClient(Config config) {
    this.baseUrl = stripTrailingSlashes(config.baseUrl());
    this.apiKey = config.apiKey();
    this.appId = config.appId();
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

  // ── Convenience verbs ────────────────────────────────────────────────────────

  /** GET {@code path}; completes with the parsed body (caller unwraps/converts it). */
  @SuppressWarnings("unchecked")
  public <T> CompletableFuture<T> getAsync(String path, RequestOptions options) {
    return requestAsync(HttpRequest.builder("GET", path).options(options).build());
  }

  /** Blocking GET. */
  public Object get(String path, RequestOptions options) {
    return Futures.join(getAsync(path, options));
  }

  /** POST {@code path} with an explicit retry-safety flag. */
  @SuppressWarnings("unchecked")
  public <T> CompletableFuture<T> postAsync(
      String path, @Nullable Object body, boolean idempotent, RequestOptions options) {
    return requestAsync(
        HttpRequest.builder("POST", path)
            .body(body)
            .idempotent(idempotent)
            .options(options)
            .build());
  }

  /** Blocking POST. */
  public Object post(
      String path, @Nullable Object body, boolean idempotent, RequestOptions options) {
    return Futures.join(postAsync(path, body, idempotent, options));
  }

  /** PUT {@code path} (naturally idempotent — retried on transient failures). */
  @SuppressWarnings("unchecked")
  public <T> CompletableFuture<T> putAsync(
      String path, @Nullable Object body, RequestOptions options) {
    return requestAsync(HttpRequest.builder("PUT", path).body(body).options(options).build());
  }

  /** Blocking PUT. */
  public Object put(String path, @Nullable Object body, RequestOptions options) {
    return Futures.join(putAsync(path, body, options));
  }

  /** PATCH {@code path}. */
  @SuppressWarnings("unchecked")
  public <T> CompletableFuture<T> patchAsync(
      String path, @Nullable Object body, RequestOptions options) {
    return requestAsync(HttpRequest.builder("PATCH", path).body(body).options(options).build());
  }

  /** Blocking PATCH. */
  public Object patch(String path, @Nullable Object body, RequestOptions options) {
    return Futures.join(patchAsync(path, body, options));
  }

  /** DELETE {@code path} (naturally idempotent — retried on transient failures). */
  @SuppressWarnings("unchecked")
  public <T> CompletableFuture<T> deleteAsync(String path, RequestOptions options) {
    return requestAsync(HttpRequest.builder("DELETE", path).options(options).build());
  }

  /** Blocking DELETE. */
  public Object delete(String path, RequestOptions options) {
    return Futures.join(deleteAsync(path, options));
  }

  // ── Core request ─────────────────────────────────────────────────────────────

  /**
   * Send one API request; completes with the parsed body on 2xx or with the mapped typed error (see
   * {@link HttpErrorMapper}) otherwise.
   */
  @SuppressWarnings("unchecked")
  public <T> CompletableFuture<T> requestAsync(HttpRequest request) {
    String key = request.options().idempotencyKey();
    if (key != null && key.isBlank()) {
      return CompletableFuture.failedFuture(
          new ConfigException("Idempotency-Key must be a non-empty, non-whitespace string."));
    }
    // Honor the explicit idempotency flag first: a request a caller marked non-idempotent stays
    // non-retryable even when a key is supplied. Only when the flag is unset does a non-empty key
    // opt the request into retry safety.
    boolean retryable =
        request.idempotent() != null
            ? request.idempotent()
            : isIdempotentMethod(request.method()) || key != null;

    String url = buildUrl(request.path(), request.query());
    Map<String, String> headers = buildHeaders(request);
    TransportRequest transportRequest =
        new TransportRequest(
            url,
            request.method(),
            headers,
            request.body() == null ? null : Json.write(request.body()),
            retryable,
            "Request",
            request.options().timeoutMs(),
            request.options().cancellation());

    return transport
        .send(transportRequest)
        .<T>thenApply(
            result -> {
              if (result.status() >= 200 && result.status() < 300) {
                return (T) result.body();
              }
              throw mapError(result);
            });
  }

  /** Blocking variant of {@link #requestAsync}. */
  public Object request(HttpRequest request) {
    return Futures.join(requestAsync(request));
  }

  private static VinkiusException mapError(TransportResult result) {
    return HttpErrorMapper.map(
        result.status(),
        result.body(),
        result.requestId(),
        result.retryAfterMs() == null ? null : result.retryAfterMs().intValue());
  }

  private String buildUrl(String path, Map<String, Object> query) {
    String normalizedPath = path.startsWith("/") ? path : "/" + path;
    StringBuilder url = new StringBuilder(baseUrl).append(normalizedPath);
    if (!query.isEmpty()) {
      url.append('?');
      boolean first = true;
      for (Map.Entry<String, Object> entry : query.entrySet()) {
        if (entry.getValue() == null) {
          continue;
        }
        if (!first) {
          url.append('&');
        }
        first = false;
        url.append(encode(String.valueOf(entry.getKey())));
        url.append('=');
        url.append(encode(String.valueOf(entry.getValue())));
      }
    }
    return url.toString();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private Map<String, String> buildHeaders(HttpRequest request) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("authorization", "Bearer " + apiKey);
    headers.put(APP_ID_HEADER, appId);
    headers.put("accept", "application/json");
    headers.put("user-agent", userAgent);
    headers.putAll(request.headers());
    if (request.body() != null) {
      headers.put("content-type", "application/json");
    }
    String key = request.options().idempotencyKey();
    if (key != null && !key.isEmpty()) {
      headers.put("idempotency-key", key);
    }
    return headers;
  }

  private static boolean isIdempotentMethod(String method) {
    return "GET".equals(method) || "PUT".equals(method) || "DELETE".equals(method);
  }

  private static String stripTrailingSlashes(String value) {
    String result = value;
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }
}
