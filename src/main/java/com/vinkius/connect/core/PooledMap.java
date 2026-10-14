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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/**
 * Concurrency-limited async mapping.
 *
 * <p>{@link #pooledMap} maps over {@code items} with at most {@code limit} in-flight futures;
 * result order matches the order of {@code items}. One flaky task must not sink the batch unless
 * every task fails — the caller decides the tolerance policy.
 */
public final class PooledMap {

  private PooledMap() {}

  /** Map over {@code items} with at most {@code limit} in-flight futures, preserving order. */
  public static <TIn, TOut> CompletableFuture<List<TOut>> pooledMap(
      List<? extends TIn> items,
      int limit,
      BiFunction<? super TIn, Integer, CompletableFuture<TOut>> fn) {
    int size = items.size();
    if (size == 0) {
      return CompletableFuture.completedFuture(new ArrayList<>(0));
    }
    int workerCount = Math.max(1, Math.min(limit, size));
    Object[] results = new Object[size];
    AtomicInteger next = new AtomicInteger();

    List<CompletableFuture<Void>> workers = new ArrayList<>(workerCount);
    for (int i = 0; i < workerCount; i++) {
      workers.add(runWorker(items, next, results, fn));
    }
    return CompletableFuture.allOf(workers.toArray(new CompletableFuture<?>[0]))
        .thenApply(
            ignored -> {
              List<TOut> out = new ArrayList<>(size);
              for (Object value : results) {
                @SuppressWarnings("unchecked")
                TOut item = (TOut) value;
                out.add(item);
              }
              return out;
            });
  }

  private static <TIn, TOut> CompletableFuture<Void> runWorker(
      List<? extends TIn> items,
      AtomicInteger next,
      Object[] results,
      BiFunction<? super TIn, Integer, CompletableFuture<TOut>> fn) {
    CompletableFuture<Void> worker = new CompletableFuture<>();
    step(items, next, results, fn, worker);
    return worker;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private static <TIn, TOut> void step(
      List<? extends TIn> items,
      AtomicInteger next,
      Object[] results,
      BiFunction<? super TIn, Integer, CompletableFuture<TOut>> fn,
      CompletableFuture<Void> worker) {
    if (worker.isDone()) {
      return;
    }
    int index = next.getAndIncrement();
    if (index >= items.size()) {
      worker.complete(null);
      return;
    }
    CompletableFuture<TOut> future;
    try {
      future = fn.apply(items.get(index), index);
    } catch (Throwable error) {
      worker.completeExceptionally(error);
      return;
    }
    future.whenComplete(
        (value, error) -> {
          if (error != null) {
            worker.completeExceptionally(error);
            return;
          }
          results[index] = value;
          step(items, next, results, fn, worker);
        });
  }
}
