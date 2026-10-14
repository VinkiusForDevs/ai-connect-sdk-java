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
package com.vinkius.connect.helpers;

import com.vinkius.connect.core.CancellationToken;
import com.vinkius.connect.core.HttpSender;
import com.vinkius.connect.core.Json;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Scriptable {@link HttpSender} — the Java counterpart of the JS test helper {@code mock-fetch}.
 *
 * <p>Routes are matched by method + path regex (first match wins). Every request is recorded for
 * assertions. Delayed responses honor the request's {@link CancellationToken}, faithfully mirroring
 * real fetch semantics: an already-cancelled token rejects immediately.
 */
public final class FakeHttpSender implements HttpSender {

  /** One recorded outgoing request. */
  public record RecordedCall(
      String method,
      String url,
      String path,
      Map<String, String> query,
      @Nullable Object body,
      Map<String, String> headers) {

    /** Case-insensitive header lookup. */
    public @Nullable String header(String name) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        if (entry.getKey().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
          return entry.getValue();
        }
      }
      return null;
    }

    /** First value of a query parameter, or {@code null}. */
    public @Nullable String queryParam(String name) {
      return query.get(name);
    }
  }

  /** A scripted response. */
  public record MockResponse(
      int status, Map<String, String> headers, @Nullable Object body, long delayMs) {

    /** HTTP 200 with a JSON body. */
    public static MockResponse ok(@Nullable Object body) {
      return of(200, body);
    }

    /** Any status with a JSON body. */
    public static MockResponse of(int status, @Nullable Object body) {
      return new MockResponse(status, Map.of(), body, 0);
    }

    /** Copy with extra response headers (merged case-insensitively on top). */
    public MockResponse withHeaders(Map<String, String> extra) {
      Map<String, String> merged = new LinkedHashMap<>(headers);
      merged.putAll(extra);
      return new MockResponse(status, merged, body, delayMs);
    }

    /** Copy with a single response header. */
    public MockResponse withHeader(String name, String value) {
      Map<String, String> merged = new LinkedHashMap<>(headers);
      merged.put(name, value);
      return new MockResponse(status, merged, body, delayMs);
    }

    /** Copy with simulated latency (honors the request's cancellation token). */
    public MockResponse withDelay(long millis) {
      return new MockResponse(status, headers, body, millis);
    }
  }

  /** A scripted responder for one route. */
  @FunctionalInterface
  public interface Responder {
    MockResponse respond(RecordedCall call);
  }

  /** One route: HTTP method + path regex + responder. */
  public record Route(String method, Pattern path, Responder respond) {

    /** A GET route. */
    public static Route get(String pathRegex, Responder responder) {
      return new Route("GET", Pattern.compile(pathRegex), responder);
    }

    /** A POST route. */
    public static Route post(String pathRegex, Responder responder) {
      return new Route("POST", Pattern.compile(pathRegex), responder);
    }

    /** A PUT route. */
    public static Route put(String pathRegex, Responder responder) {
      return new Route("PUT", Pattern.compile(pathRegex), responder);
    }

    /** A PATCH route. */
    public static Route patch(String pathRegex, Responder responder) {
      return new Route("PATCH", Pattern.compile(pathRegex), responder);
    }

    /** A DELETE route. */
    public static Route delete(String pathRegex, Responder responder) {
      return new Route("DELETE", Pattern.compile(pathRegex), responder);
    }
  }

  private final List<Route> routes;
  private final List<RecordedCall> calls = new ArrayList<>();

  private FakeHttpSender(List<Route> routes) {
    this.routes = List.copyOf(routes);
  }

  /** A sender with no routes (add yours in order). */
  public static FakeHttpSender of(Route... routes) {
    return new FakeHttpSender(List.of(routes));
  }

  /** A sender with the given routes. */
  public static FakeHttpSender of(List<Route> routes) {
    return new FakeHttpSender(routes);
  }

  /** All calls received, in order. */
  public List<RecordedCall> calls() {
    synchronized (calls) {
      return List.copyOf(calls);
    }
  }

  /** The call at {@code index} (asserts existence implicitly). */
  public RecordedCall call(int index) {
    synchronized (calls) {
      if (index < 0 || index >= calls.size()) {
        throw new AssertionError(
            "Expected call #"
                + index
                + " but only "
                + calls.size()
                + " call(s) recorded: "
                + calls());
      }
      return calls.get(index);
    }
  }

  /** Number of calls received. */
  public int callCount() {
    synchronized (calls) {
      return calls.size();
    }
  }

  /** Forget all recorded calls. */
  public void reset() {
    synchronized (calls) {
      calls.clear();
    }
  }

  @Override
  public CompletableFuture<Response> send(Request request) {
    URI uri = URI.create(request.url());
    String path = uri.getRawPath() != null ? uri.getRawPath() : "";
    Map<String, String> query = parseQuery(uri.getRawQuery());
    @Nullable Object body = request.body() == null ? null : Json.parse(request.body());
    RecordedCall call =
        new RecordedCall(
            request.method(),
            request.url(),
            path,
            query,
            body,
            new LinkedHashMap<>(request.headers()));

    synchronized (calls) {
      calls.add(call);
    }

    CancellationTokenState cancellation = CancellationTokenState.of(request.cancellation());
    if (cancellation.alreadyCancelled()) {
      return CompletableFuture.failedFuture(new CancellationException("aborted"));
    }

    @Nullable Route route = null;
    for (Route candidate : routes) {
      if (candidate.method().equals(request.method()) && candidate.path().matcher(path).matches()) {
        route = candidate;
        break;
      }
    }
    if (route == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("No mock route for " + request.method() + " " + path));
    }

    MockResponse response;
    try {
      response = route.respond().respond(call);
    } catch (RuntimeException error) {
      return CompletableFuture.failedFuture(error);
    }

    if (response.delayMs() > 0) {
      return delay(response, cancellation);
    }
    return CompletableFuture.completedFuture(toResponse(response));
  }

  private static CompletableFuture<Response> delay(
      MockResponse response, CancellationTokenState cancellation) {
    CompletableFuture<Response> future = new CompletableFuture<>();
    cancellation.whenCancelled(
        () -> future.completeExceptionally(new CancellationException("aborted")));
    if (future.isDone()) {
      return future;
    }
    Thread worker =
        new Thread(
            () -> {
              long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(response.delayMs());
              while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                  break;
                }
                try {
                  TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(5)));
                } catch (InterruptedException interrupted) {
                  Thread.currentThread().interrupt();
                  future.completeExceptionally(new CancellationException("aborted"));
                  return;
                }
              }
              future.complete(toResponse(response));
            },
            "fake-http-delay");
    worker.setDaemon(true);
    worker.start();
    return future;
  }

  private static Response toResponse(MockResponse response) {
    String payload = response.body() == null ? "" : Json.write(response.body());
    return new Response(response.status(), new LinkedHashMap<>(response.headers()), payload);
  }

  private static Map<String, String> parseQuery(@Nullable String rawQuery) {
    Map<String, String> query = new LinkedHashMap<>();
    if (rawQuery == null || rawQuery.isEmpty()) {
      return query;
    }
    for (String pair : rawQuery.split("&", -1)) {
      if (pair.isEmpty()) {
        continue;
      }
      int eq = pair.indexOf('=');
      String key = eq >= 0 ? pair.substring(0, eq) : pair;
      String value = eq >= 0 ? pair.substring(eq + 1) : "";
      query.putIfAbsent(
          URLDecoder.decode(key, StandardCharsets.UTF_8),
          URLDecoder.decode(value, StandardCharsets.UTF_8));
    }
    return query;
  }

  /** Minimal view over a (possibly null) cancellation token. */
  private record CancellationTokenState(@Nullable CancellationToken token) {

    static CancellationTokenState of(@Nullable CancellationToken token) {
      return new CancellationTokenState(token);
    }

    boolean alreadyCancelled() {
      return token != null && token.isCancelled();
    }

    void whenCancelled(Runnable callback) {
      if (token != null) {
        token.onCancel(callback);
      }
    }
  }
}
