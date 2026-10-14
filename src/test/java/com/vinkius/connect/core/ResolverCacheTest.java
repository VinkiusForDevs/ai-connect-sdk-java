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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResolverCacheTest {

  @Test
  void storesAndRetrievesAValue() throws InterruptedException {
    ResolverCache cache = new ResolverCache(10_000);
    cache.set("key1", "value1");
    assertThat(cache.get("key1")).isEqualTo("value1");
  }

  @Test
  void returnsNullForAMissingKey() {
    ResolverCache cache = new ResolverCache();
    assertThat(cache.get("nope")).isNull();
  }

  @Test
  void expiresEntriesAfterTtl() throws InterruptedException {
    ResolverCache cache = new ResolverCache(100);
    cache.set("key", "val");
    assertThat(cache.get("key")).isEqualTo("val");
    Thread.sleep(150);
    assertThat(cache.get("key")).isNull();
  }

  @Test
  void deletesASpecificKey() {
    ResolverCache cache = new ResolverCache();
    cache.set("a", 1);
    cache.set("b", 2);
    cache.delete("a");
    assertThat(cache.get("a")).isNull();
    assertThat(cache.get("b")).isEqualTo(2);
  }

  @Test
  void clearsAllEntries() {
    ResolverCache cache = new ResolverCache();
    cache.set("a", 1);
    cache.set("b", 2);
    cache.clear();
    assertThat(cache.get("a")).isNull();
    assertThat(cache.get("b")).isNull();
  }

  @Test
  void resolveReturnsCachedValueWithoutRecomputing() {
    ResolverCache cache = new ResolverCache(10_000);
    AtomicInteger computeCount = new AtomicInteger();
    String v1 = cache.resolve("k", () -> "computed-" + computeCount.incrementAndGet());
    String v2 = cache.resolve("k", () -> "computed-" + computeCount.incrementAndGet());
    assertThat(v1).isEqualTo("computed-1");
    assertThat(v2).isEqualTo("computed-1");
    assertThat(computeCount.get()).isEqualTo(1);
  }

  @Test
  void resolveRecomputesAfterTtlExpiry() throws InterruptedException {
    ResolverCache cache = new ResolverCache(100);
    AtomicInteger computeCount = new AtomicInteger();
    assertThat(cache.resolve("k", () -> "v" + computeCount.incrementAndGet())).isEqualTo("v1");
    Thread.sleep(150);
    assertThat(cache.resolve("k", () -> "v" + computeCount.incrementAndGet())).isEqualTo("v2");
    assertThat(computeCount.get()).isEqualTo(2);
  }

  @Test
  void typedGetConvertsAndReturnsNullForMissingKeys() {
    ResolverCache cache = new ResolverCache(10_000);
    cache.set("age", 42);

    assertThat(cache.get("age", Integer.class)).isEqualTo(42);
    assertThat(cache.get("missing", Integer.class)).isNull();
  }

  @Test
  void resolveAsyncMemoizesCompletedFutureValues() {
    ResolverCache cache = new ResolverCache(10_000);
    AtomicInteger computeCount = new AtomicInteger();

    String first =
        cache
            .resolveAsync(
                "k",
                () -> CompletableFuture.completedFuture("async-" + computeCount.incrementAndGet()))
            .join();
    String second =
        cache
            .resolveAsync(
                "k",
                () -> CompletableFuture.completedFuture("async-" + computeCount.incrementAndGet()))
            .join();

    assertThat(first).isEqualTo("async-1");
    assertThat(second).isEqualTo("async-1");
    assertThat(computeCount.get()).isEqualTo(1);
  }
}
