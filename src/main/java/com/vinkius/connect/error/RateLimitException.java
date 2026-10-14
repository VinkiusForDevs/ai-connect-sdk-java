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
package com.vinkius.connect.error;

import org.jspecify.annotations.Nullable;

/** 429 — control-plane rate limit. Retried automatically for idempotent calls. */
public final class RateLimitException extends VinkiusException {

  private static final long serialVersionUID = 1L;

  private final @Nullable Integer retryAfterMs;

  public RateLimitException(
      String message,
      int status,
      @Nullable String requestId,
      @Nullable Object details,
      @Nullable Integer retryAfterMs) {
    super(message, VinkiusErrorCode.RATE_LIMIT, status, requestId, details, null);
    this.retryAfterMs = retryAfterMs;
  }

  /** Milliseconds to wait before retrying, if the server advertised it. */
  public @Nullable Integer retryAfterMs() {
    return retryAfterMs;
  }
}
