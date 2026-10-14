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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;

/**
 * Retry policy and backoff math.
 *
 * <p>Retries apply only to idempotent requests and only for transient statuses (429, 502, 503, 504)
 * or network errors. Backoff uses full jitter to avoid thundering-herd retries; a {@code
 * Retry-After} header, when present, wins.
 *
 * @param maxRetries maximum automatic retries for retryable requests
 * @param baseDelayMs first backoff window, doubled per attempt
 * @param maxDelayMs cap on the backoff window (and on server hints)
 */
public record RetryPolicy(int maxRetries, long baseDelayMs, long maxDelayMs) {

  /** Default policy: 2 retries, 250ms base, 4000ms cap. */
  public static final RetryPolicy DEFAULT = new RetryPolicy(2, 250, 4000);

  /** Whether an HTTP status is worth retrying. */
  public static boolean isRetryableStatus(int status) {
    return status == 429 || status == 502 || status == 503 || status == 504;
  }

  /**
   * Compute the delay before the next attempt (0-indexed).
   *
   * <p>A positive {@code retryAfterMs} (server hint) wins, capped at {@code maxDelayMs}. {@code
   * Retry-After: 0} is treated as NO hint: honoring it exactly would make simultaneous clients
   * hammer the server in lockstep — full jitter instead. Without a hint, full jitter over an
   * exponentially growing window.
   */
  public static long backoffDelay(int attempt, RetryPolicy policy, @Nullable Long retryAfterMs) {
    if (retryAfterMs != null && retryAfterMs > 0) {
      return Math.min(retryAfterMs, policy.maxDelayMs);
    }
    long window = Math.min(policy.maxDelayMs, policy.baseDelayMs * (1L << Math.min(attempt, 30)));
    return Math.round(ThreadLocalRandom.current().nextDouble() * window);
  }

  /**
   * Parse a {@code Retry-After} header (delta-seconds or HTTP-date) into milliseconds; {@code null}
   * or unparseable input yields {@code null} (no hint).
   */
  public static @Nullable Long parseRetryAfter(@Nullable String headerValue) {
    if (headerValue == null || headerValue.isEmpty()) {
      return null;
    }
    try {
      double seconds = Double.parseDouble(headerValue.trim());
      return Math.max(0L, Math.round(seconds * 1000));
    } catch (NumberFormatException ignored) {
      // Fall through to HTTP-date parsing.
    }
    try {
      Instant date =
          OffsetDateTime.parse(headerValue.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
              .toInstant();
      return Math.max(0L, date.toEpochMilli() - System.currentTimeMillis());
    } catch (DateTimeParseException ignored) {
      // Fall through to ISO-8601 parsing.
    }
    try {
      Instant date = Instant.parse(headerValue.trim());
      return Math.max(0L, date.toEpochMilli() - System.currentTimeMillis());
    } catch (RuntimeException e) {
      return null;
    }
  }
}
