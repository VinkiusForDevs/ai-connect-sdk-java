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
import org.jspecify.annotations.Nullable;

/**
 * One logical request through the {@link Transport}: URL, method, headers, optional JSON body text,
 * retry-safety, error-message label, per-request deadline and cancellation.
 *
 * @param retryable whether transport-level failures and transient statuses may be retried
 * @param label noun used in error messages, e.g. "Request" / "Runtime request"
 */
public record TransportRequest(
    String url,
    String method,
    Map<String, String> headers,
    @Nullable String body,
    boolean retryable,
    String label,
    @Nullable Long timeoutMs,
    @Nullable CancellationToken cancellation) {

  /** Same request with a different retry-safety flag. */
  public TransportRequest withRetryable(boolean value) {
    return new TransportRequest(url, method, headers, body, value, label, timeoutMs, cancellation);
  }
}
