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

/** 429 (data-plane) — plan quota exhausted. */
public final class QuotaException extends VinkiusException {

  private static final long serialVersionUID = 1L;

  private final @Nullable String upgradeUrl;

  public QuotaException(
      String message,
      int status,
      @Nullable String requestId,
      @Nullable Object details,
      @Nullable String upgradeUrl) {
    super(message, VinkiusErrorCode.QUOTA_EXCEEDED, status, requestId, details, null);
    this.upgradeUrl = upgradeUrl;
  }

  /** Upgrade URL advertised by the runtime, when present. */
  public @Nullable String upgradeUrl() {
    return upgradeUrl;
  }
}
