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

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Observability hooks. {@code headers} and {@code body} are ALWAYS redacted before being handed to
 * these callbacks — secrets never reach a hook. {@code attempt} is the 0-based retry attempt that
 * produced the event.
 */
public interface Hooks {

  /** Invoked just before each attempt (request data already redacted). */
  default void onRequest(RequestInfo info) {}

  /** Invoked when a response arrives (body already redacted). */
  default void onResponse(ResponseInfo info) {}

  /** Redacted snapshot of an outgoing request. */
  record RequestInfo(
      String method, String url, Map<String, String> headers, @Nullable Integer attempt) {}

  /** Redacted snapshot of a response. */
  record ResponseInfo(
      int status,
      String url,
      @Nullable String requestId,
      @Nullable Object body,
      @Nullable Integer attempt) {}
}
