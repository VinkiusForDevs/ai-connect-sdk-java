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
import org.jspecify.annotations.Nullable;

/**
 * SPI for the HTTP layer — the Java counterpart of the JS SDK's injectable {@code fetch}.
 *
 * <p>Implementations receive one fully-built request (URL, method, headers, optional JSON body,
 * deadline, cancellation token) and complete with the full response (status, headers, body text).
 * Implementations should complete exceptionally on network failure and honor {@link
 * Request#timeoutMs()} themselves; the transport still enforces the deadline as a backstop.
 */
public interface HttpSender {

  /** One outgoing HTTP request. */
  record Request(
      String url,
      String method,
      Map<String, String> headers,
      @Nullable String body,
      long timeoutMs,
      @Nullable CancellationToken cancellation) {}

  /** One full HTTP response (body read to completion). */
  record Response(int status, Map<String, String> headers, String body) {}

  /** Send one request, completing exceptionally on transport failure. */
  CompletableFuture<Response> send(Request request);
}
