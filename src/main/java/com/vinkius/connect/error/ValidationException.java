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

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** 422 — request validation failed. */
public final class ValidationException extends VinkiusException {

  private static final long serialVersionUID = 1L;

  private final transient Map<String, List<String>> errors;

  public ValidationException(
      String message,
      Map<String, List<String>> errors,
      int status,
      @Nullable String requestId,
      @Nullable Object details) {
    super(message, VinkiusErrorCode.VALIDATION_ERROR, status, requestId, details, null);
    this.errors = errors;
  }

  /** Field → messages, as returned by the API. */
  public Map<String, List<String>> errors() {
    return errors;
  }
}
