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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Helpers for bridging {@link CompletableFuture} APIs with synchronous call sites. */
public final class Futures {

  private Futures() {}

  /**
   * Block until the future completes and return its value, unwrapping async failures: SDK
   * exceptions are rethrown as-is so callers see the typed hierarchy, never {@link
   * CompletionException}.
   */
  public static <T> T join(CompletableFuture<T> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ConnectionException("Operation interrupted", e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new ConnectionException(String.valueOf(cause.getMessage()), cause);
    }
  }

  /** Unwrap a possibly-wrapped completion failure to its root cause. */
  public static Throwable unwrap(Throwable error) {
    Throwable current = error;
    while ((current instanceof CompletionException || current instanceof ExecutionException)
        && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }
}
