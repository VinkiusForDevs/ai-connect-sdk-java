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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * A small TTL cache for non-secret resolution data.
 *
 * <p>GOLDEN RULE: cache is an optimization, never a source of authorization. Only non-secret,
 * stable values belong here (e.g. external_id → user id, catalog credential schemas). Credentials,
 * Authorization headers, and any {@code vk_live_*} MUST NEVER be cached — and never even reach the
 * SDK.
 */
public final class ResolverCache {

  private static final long DEFAULT_TTL_MS = 5 * 60 * 1000;

  private record Entry(@Nullable Object value, long expiresAt) {}

  private final Map<String, Entry> store = new ConcurrentHashMap<>();
  private final long ttlMs;

  public ResolverCache() {
    this(DEFAULT_TTL_MS);
  }

  public ResolverCache(long ttlMs) {
    this.ttlMs = ttlMs;
  }

  /** The cached value for {@code key}, or {@code null} when absent or expired. */
  public @Nullable Object get(String key) {
    Entry entry = store.get(key);
    if (entry == null) {
      return null;
    }
    if (entry.expiresAt() <= System.currentTimeMillis()) {
      store.remove(key);
      return null;
    }
    return entry.value();
  }

  /** The cached value for {@code key} typed as {@code type}, or {@code null}. */
  public <T> @Nullable T get(String key, Class<T> type) {
    Object value = get(key);
    return value == null ? null : Json.convert(value, type);
  }

  public void set(String key, @Nullable Object value) {
    store.put(key, new Entry(value, System.currentTimeMillis() + ttlMs));
  }

  public void delete(String key) {
    store.remove(key);
  }

  public void clear() {
    store.clear();
  }

  /**
   * Return the cached value or compute, cache, and return it. When {@code value} is {@code null}
   * nothing is cached (mirroring the JS {@code undefined} short-circuit).
   */
  public <T> T resolve(String key, Supplier<T> compute) {
    @SuppressWarnings("unchecked")
    T cached = (T) get(key);
    if (cached != null) {
      return cached;
    }
    T value = compute.get();
    if (value != null) {
      set(key, value);
    }
    return value;
  }

  /** Async flavor of {@link #resolve}: memoize a completed future value. */
  public <T> CompletableFuture<T> resolveAsync(String key, Supplier<CompletableFuture<T>> compute) {
    @SuppressWarnings("unchecked")
    T cached = (T) get(key);
    if (cached != null) {
      return CompletableFuture.completedFuture(cached);
    }
    return compute
        .get()
        .thenApply(
            value -> {
              if (value != null) {
                set(key, value);
              }
              return value;
            });
  }
}
