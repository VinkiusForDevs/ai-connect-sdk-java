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

/**
 * Per-request controls for capability execution (the Java counterpart of the JS {@code
 * ExecuteOptions}).
 *
 * <p>{@code idempotencyKey} is forwarded as the {@code Idempotency-Key} header so a retried
 * execution (e.g. after a network timeout) does not duplicate side effects. Retries are armed only
 * when the caller opts in with a key.
 */
public class ExecuteOptions extends RequestOptions {

  /** The default, empty execution options. */
  public static final ExecuteOptions EMPTY = new ExecuteOptions(new Builder());

  protected ExecuteOptions(Builder builder) {
    super(builder);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Fluent builder for {@link ExecuteOptions}. */
  public static class Builder extends RequestOptions.Builder {

    @Override
    public Builder idempotencyKey(String idempotencyKey) {
      super.idempotencyKey(idempotencyKey);
      return this;
    }

    @Override
    public Builder timeoutMs(long timeoutMs) {
      super.timeoutMs(timeoutMs);
      return this;
    }

    @Override
    public Builder cancellation(CancellationToken cancellation) {
      super.cancellation(cancellation);
      return this;
    }

    @Override
    public ExecuteOptions build() {
      return new ExecuteOptions(this);
    }
  }
}
