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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Default {@link HttpSender} backed by {@code java.net.http.HttpClient} (Java 11+).
 *
 * <p>No third-party HTTP stack: the SDK ships dependency-light on purpose (Jackson only).
 */
public final class JdkHttpSender implements HttpSender {

  private final HttpClient client;

  public JdkHttpSender() {
    this.client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
  }

  @Override
  public CompletableFuture<Response> send(Request request) {
    try {
      HttpRequest.BodyPublisher publisher =
          request.body() == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(request.body());
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create(request.url()))
              .timeout(Duration.ofMillis(Math.max(1, request.timeoutMs())))
              .method(request.method(), publisher);
      for (Map.Entry<String, String> header : request.headers().entrySet()) {
        builder.header(header.getKey(), header.getValue());
      }
      return client
          .sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
          .thenApply(
              response ->
                  new Response(
                      response.statusCode(),
                      flattenHeaders(response),
                      response.body() == null ? "" : response.body()));
    } catch (RuntimeException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private static Map<String, String> flattenHeaders(HttpResponse<?> response) {
    Map<String, String> headers = new LinkedHashMap<>();
    response
        .headers()
        .map()
        .forEach(
            (name, values) -> {
              if (!values.isEmpty()) {
                headers.put(name.toLowerCase(java.util.Locale.ROOT), values.get(0));
              }
            });
    return headers;
  }
}
