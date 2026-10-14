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
package com.vinkius.connect.model;

import com.vinkius.connect.core.CancellationToken;
import org.jspecify.annotations.Nullable;

/**
 * Per-request controls accepted by every SDK call.
 *
 * <p>{@code idempotencyKey} is forwarded as the {@code Idempotency-Key} header for request
 * correlation. When the request is otherwise retryable (GET/PUT/DELETE, or a create the caller
 * flagged idempotent), a non-empty key keeps it safe to retry. The control plane does not run a
 * replay-deduplication store — retry safety comes from natural idempotency. Empty/whitespace-only
 * keys are rejected ({@link com.vinkius.connect.error.ConfigException}).
 *
 * <p>{@code timeoutMs} is a per-request deadline overriding the client-level timeout; {@code
 * cancellation} aborts the call (the Java counterpart of the JS {@code AbortSignal}).
 */
public class RequestOptions {

  /** The default, empty request options. */
  public static final RequestOptions EMPTY = new RequestOptions(new Builder());

  private final @Nullable String idempotencyKey;
  private final @Nullable Long timeoutMs;
  private final @Nullable CancellationToken cancellation;

  protected RequestOptions(Builder builder) {
    this.idempotencyKey = builder.idempotencyKey;
    this.timeoutMs = builder.timeoutMs;
    this.cancellation = builder.cancellation;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The idempotency key forwarded as {@code Idempotency-Key}, when set. */
  public @Nullable String idempotencyKey() {
    return idempotencyKey;
  }

  /** Per-request deadline in milliseconds, overriding the client-level timeout. */
  public @Nullable Long timeoutMs() {
    return timeoutMs;
  }

  /** Token that aborts the request when cancelled, when set. */
  public @Nullable CancellationToken cancellation() {
    return cancellation;
  }

  /** Fluent builder for {@link RequestOptions}. */
  public static class Builder {
    private @Nullable String idempotencyKey;
    private @Nullable Long timeoutMs;
    private @Nullable CancellationToken cancellation;

    public Builder idempotencyKey(String idempotencyKey) {
      this.idempotencyKey = idempotencyKey;
      return this;
    }

    public Builder timeoutMs(long timeoutMs) {
      this.timeoutMs = timeoutMs;
      return this;
    }

    public Builder cancellation(CancellationToken cancellation) {
      this.cancellation = cancellation;
      return this;
    }

    public RequestOptions build() {
      return new RequestOptions(this);
    }
  }
}
