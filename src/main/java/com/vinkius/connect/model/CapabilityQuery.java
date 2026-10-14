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
import java.util.List;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;

/**
 * Filter for {@code UserContext.capabilities()} aggregation (extends {@link RequestOptions}).
 *
 * <p>{@code include}/{@code exclude} are client-side filters applied before any call. {@code
 * onConnectorError} lets the caller observe partial fan-out failures: the aggregation is
 * failure-tolerant (the rest still resolves), but by default the failure is swallowed.
 */
public final class CapabilityQuery extends RequestOptions {

  private final List<String> include;
  private final List<String> exclude;
  private final @Nullable BiConsumer<String, Throwable> onConnectorError;

  private CapabilityQuery(Builder builder) {
    super(builder);
    this.include = List.copyOf(builder.include);
    this.exclude = List.copyOf(builder.exclude);
    this.onConnectorError = builder.onConnectorError;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Connector slugs to restrict to (empty = no restriction). */
  public List<String> include() {
    return include;
  }

  /** Connector slugs to exclude (empty = none). */
  public List<String> exclude() {
    return exclude;
  }

  /** Callback notified with (slug, error) for each failed connector, or {@code null}. */
  public @Nullable BiConsumer<String, Throwable> onConnectorError() {
    return onConnectorError;
  }

  /** Fluent builder for {@link CapabilityQuery}. */
  public static final class Builder extends RequestOptions.Builder {
    private List<String> include = List.of();
    private List<String> exclude = List.of();
    private @Nullable BiConsumer<String, Throwable> onConnectorError;

    public Builder include(String... slugs) {
      this.include = List.of(slugs);
      return this;
    }

    public Builder exclude(String... slugs) {
      this.exclude = List.of(slugs);
      return this;
    }

    public Builder onConnectorError(BiConsumer<String, Throwable> onConnectorError) {
      this.onConnectorError = onConnectorError;
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
    public CapabilityQuery build() {
      return new CapabilityQuery(this);
    }
  }
}
