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

/**
 * Per-request cancellation token — the Java counterpart of the JS {@code AbortSignal}.
 *
 * <p>Cancelling fires registered callbacks once; the transport converts an active cancellation into
 * a {@link com.vinkius.connect.error.ConnectionException} ("aborted by caller").
 */
public final class CancellationToken {

  private final Object lock = new Object();
  private final List<Runnable> callbacks = new ArrayList<>();
  private boolean cancelled;

  /** Cancel the token and notify listeners exactly once. */
  public void cancel() {
    List<Runnable> toRun;
    synchronized (lock) {
      if (cancelled) {
        return;
      }
      cancelled = true;
      toRun = new ArrayList<>(callbacks);
      callbacks.clear();
    }
    for (Runnable callback : toRun) {
      callback.run();
    }
  }

  /** Whether {@link #cancel()} has been called. */
  public boolean isCancelled() {
    synchronized (lock) {
      return cancelled;
    }
  }

  /**
   * Register a callback for cancellation. If the token is already cancelled the callback runs
   * immediately on the calling thread.
   */
  public void onCancel(Runnable callback) {
    synchronized (lock) {
      if (!cancelled) {
        callbacks.add(callback);
        return;
      }
    }
    callback.run();
  }
}
