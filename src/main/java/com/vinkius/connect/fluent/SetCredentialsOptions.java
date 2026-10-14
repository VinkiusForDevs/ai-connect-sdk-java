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
package com.vinkius.connect.fluent;

import com.vinkius.connect.core.CancellationToken;
import com.vinkius.connect.model.RequestOptions;
import org.jspecify.annotations.Nullable;

/**
 * Options for {@link CredentialsHandle#set}; {@code merge} is forwarded in the body so the stored
 * credentials are updated instead of replaced.
 */
public class SetCredentialsOptions extends RequestOptions {

  /** The default, empty set-credentials options (replace semantics). */
  public static final SetCredentialsOptions EMPTY = new SetCredentialsOptions(new Builder());

  private final @Nullable Boolean merge;

  protected SetCredentialsOptions(Builder builder) {
    super(builder);
    this.merge = builder.merge;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Merge into the stored credentials instead of replacing them, when set. */
  public @Nullable Boolean merge() {
    return merge;
  }

  /** Fluent builder for {@link SetCredentialsOptions}. */
  public static class Builder extends RequestOptions.Builder {
    private @Nullable Boolean merge;

    public Builder merge(boolean merge) {
      this.merge = merge;
      return this;
    }

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
    public SetCredentialsOptions build() {
      return new SetCredentialsOptions(this);
    }
  }
}
