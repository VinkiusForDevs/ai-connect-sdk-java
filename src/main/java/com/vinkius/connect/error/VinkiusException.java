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
package com.vinkius.connect.error;

import org.jspecify.annotations.Nullable;

/**
 * Base class for every error thrown by the SDK.
 *
 * <p>Every failure carries {@link #status()} (0 for client-side/transport errors), a stable {@link
 * #code()}, and (when available) the server {@link #requestId()}. Secrets are never placed on error
 * objects — {@link #details()} only ever holds the parsed API body, which by contract never echoes
 * credentials.
 */
public class VinkiusException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int status;
  private final VinkiusErrorCode code;
  private final @Nullable String requestId;
  private final transient @Nullable Object details;

  public VinkiusException(
      String message,
      VinkiusErrorCode code,
      int status,
      @Nullable String requestId,
      @Nullable Object details,
      @Nullable Throwable cause) {
    super(message, cause);
    this.code = code;
    this.status = status;
    this.requestId = requestId;
    this.details = details;
  }

  /** HTTP status (0 for client-side / transport errors). */
  public int status() {
    return status;
  }

  /** Stable machine-readable code. */
  public VinkiusErrorCode code() {
    return code;
  }

  /** Server-provided request id, when present. */
  public @Nullable String requestId() {
    return requestId;
  }

  /** Parsed API response body (never contains secrets). */
  public @Nullable Object details() {
    return details;
  }
}
