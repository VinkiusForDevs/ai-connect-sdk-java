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

import com.vinkius.connect.error.ConnectionException;
import com.vinkius.connect.model.Hooks;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * Shared transport core: one send loop with per-request timeout, cancellation composition,
 * full-jitter retries honoring {@code Retry-After}, and redacted observability hooks.
 *
 * <p>Both the control plane ({@link HttpClient}) and the data plane ({@link RuntimeClient}) go
 * through here so timeout, retry, abort, and hook semantics can never drift apart. Hooks receive
 * ONLY redacted data: secret headers are masked and any {@code vk_live_*} path segment in the URL
 * (the data-plane token) is replaced.
 */
public final class Transport {

  /** Immutable transport settings shared by all clients built from one SDK instance. */
  public record Config(
      long timeoutMs,
      RetryPolicy retry,
      HttpSender sender,
      String userAgent,
      @Nullable Hooks hooks) {}

  private static final List<String> REQUEST_ID_HEADERS =
      List.of("x-request-id", "x-vinkius-request-id");

  private static final ScheduledExecutorService SCHEDULER =
      Executors.newScheduledThreadPool(
          1,
          runnable -> {
            Thread thread = new Thread(runnable, "vinkius-transport");
            thread.setDaemon(true);
            return thread;
          });

  private final Config config;

  public Transport(Config config) {
    this.config = config;
  }

  /**
   * Send one logical request, retrying transient failures when {@code retryable}. Returns the final
   * response (ok or not) — the caller maps non-ok statuses to typed errors, since control-plane and
   * data-plane contracts differ.
   */
  public CompletableFuture<TransportResult> send(TransportRequest request) {
    try {
      return attempt(request, 0);
    } catch (RuntimeException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private CompletableFuture<TransportResult> attempt(TransportRequest request, int attemptNumber) {
    long timeoutMs = request.timeoutMs() != null ? request.timeoutMs() : config.timeoutMs();
    int maxAttempts = request.retryable() ? config.retry().maxRetries() + 1 : 1;

    Hooks hooks = config.hooks();
    if (hooks != null) {
      hooks.onRequest(
          new Hooks.RequestInfo(
              request.method(),
              Redactor.redactUrl(request.url()),
              Redactor.redactHeaders(request.headers()),
              attemptNumber));
    }

    HttpSender.Request senderRequest =
        new HttpSender.Request(
            request.url(),
            request.method(),
            request.headers(),
            request.body(),
            timeoutMs,
            request.cancellation());

    // Race the sender against the deadline: a stalled send or body read must never hang the
    // caller (the body read happens inside the sender future by contract).
    CompletableFuture<HttpSender.Response> race = new CompletableFuture<>();
    ScheduledFuture<?> timeoutTask =
        SCHEDULER.schedule(
            () -> race.completeExceptionally(new TimeoutFailure(timeoutMs)),
            Math.max(1, timeoutMs),
            TimeUnit.MILLISECONDS);
    CancellationToken token = request.cancellation();
    if (token != null) {
      token.onCancel(() -> race.completeExceptionally(new AbortFailure()));
    }
    race.whenComplete((response, error) -> timeoutTask.cancel(false));

    CompletableFuture<HttpSender.Response> senderFuture;
    try {
      senderFuture = config.sender().send(senderRequest);
    } catch (RuntimeException e) {
      senderFuture = CompletableFuture.failedFuture(e);
    }
    senderFuture.whenComplete(
        (response, error) -> {
          if (race.isDone()) {
            return;
          }
          if (error != null) {
            race.completeExceptionally(Futures.unwrap(error));
          } else {
            race.complete(response);
          }
        });

    return race.handle(
            (response, error) ->
                handleResponse(request, attemptNumber, maxAttempts, timeoutMs, response, error))
        .thenCompose(future -> future);
  }

  private CompletableFuture<TransportResult> handleResponse(
      TransportRequest request,
      int attemptNumber,
      int maxAttempts,
      long timeoutMs,
      HttpSender.@Nullable Response response,
      @Nullable Throwable error) {
    if (error != null) {
      Throwable cause = Futures.unwrap(error);
      boolean callerAborted = cause instanceof AbortFailure || isTokenCancelled(request);
      if (callerAborted) {
        return CompletableFuture.failedFuture(
            new ConnectionException(request.label() + " aborted by caller", cause));
      }
      if (request.retryable() && attemptNumber < maxAttempts - 1) {
        return pauseAndRetry(request, attemptNumber, null);
      }
      if (cause instanceof TimeoutFailure) {
        return CompletableFuture.failedFuture(
            new ConnectionException(
                request.label() + " timed out after " + timeoutMs + "ms", cause));
      }
      return CompletableFuture.failedFuture(
          new ConnectionException(request.label() + " failed", cause));
    }

    if (response == null) {
      return CompletableFuture.failedFuture(new ConnectionException(request.label() + " failed"));
    }

    String requestId = extractRequestId(response.headers());
    // The body is read inside the sender future (same deadline window), mirroring the JS
    // contract where a stalled stream times out instead of hanging forever.
    Object body = Json.parse(response.body());
    Long retryAfterMs = RetryPolicy.parseRetryAfter(header(response, "retry-after"));

    Hooks hooks = config.hooks();
    if (hooks != null) {
      hooks.onResponse(
          new Hooks.ResponseInfo(
              response.status(),
              Redactor.redactUrl(request.url()),
              requestId,
              Redactor.redactBody(body),
              attemptNumber));
    }

    if (response.status() >= 200 && response.status() < 300) {
      return CompletableFuture.completedFuture(
          new TransportResult(response.status(), body, requestId, retryAfterMs));
    }

    if (request.retryable()
        && RetryPolicy.isRetryableStatus(response.status())
        && attemptNumber < maxAttempts - 1) {
      return pauseAndRetry(request, attemptNumber, retryAfterMs);
    }

    return CompletableFuture.completedFuture(
        new TransportResult(response.status(), body, requestId, retryAfterMs));
  }

  /** Backoff before the next attempt; a caller abort during the wait is a ConnectionError. */
  @SuppressWarnings("FutureReturnValueIgnored")
  private CompletableFuture<TransportResult> pauseAndRetry(
      TransportRequest request, int attemptNumber, @Nullable Long retryAfterMs) {
    long delay = RetryPolicy.backoffDelay(attemptNumber, config.retry(), retryAfterMs);
    return sleep(delay, request.cancellation())
        .handle(
            (ignored, error) -> {
              if (error != null) {
                return CompletableFuture.<TransportResult>failedFuture(
                    new ConnectionException(
                        request.label() + " aborted while waiting to retry",
                        Futures.unwrap(error)));
              }
              return attempt(request, attemptNumber + 1);
            })
        .thenCompose(future -> future);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private static CompletableFuture<Void> sleep(long ms, @Nullable CancellationToken token) {
    CompletableFuture<Void> done = new CompletableFuture<>();
    ScheduledFuture<?> task =
        SCHEDULER.schedule(() -> done.complete(null), ms, TimeUnit.MILLISECONDS);
    if (token != null) {
      token.onCancel(
          () -> {
            task.cancel(false);
            done.completeExceptionally(new AbortFailure());
          });
    }
    done.whenComplete((ignored, error) -> task.cancel(false));
    return done;
  }

  private static boolean isTokenCancelled(TransportRequest request) {
    return request.cancellation() != null && request.cancellation().isCancelled();
  }

  private static @Nullable String extractRequestId(Map<String, String> headers) {
    for (String name : REQUEST_ID_HEADERS) {
      String value = headerByName(headers, name);
      if (value != null && !value.isEmpty()) {
        return value;
      }
    }
    return null;
  }

  private static @Nullable String header(HttpSender.Response response, String name) {
    return headerByName(response.headers(), name);
  }

  private static @Nullable String headerByName(Map<String, String> headers, String name) {
    String direct = headers.get(name);
    if (direct != null) {
      return direct;
    }
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if (entry.getKey().toLowerCase(Locale.ROOT).equals(name)) {
        return entry.getValue();
      }
    }
    return null;
  }

  /** Deadline reached while waiting for the sender (send or body read). */
  private static final class TimeoutFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    TimeoutFailure(long timeoutMs) {
      super("timeout after " + timeoutMs + "ms");
    }
  }

  /** The caller's cancellation token fired. */
  private static final class AbortFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    AbortFailure() {
      super("aborted");
    }
  }
}
