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

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Capability execution result. Ordinary failures set {@link #isError()} (never thrown); Vinkius
 * gate blocks are thrown as typed errors.
 *
 * <p>{@link #structuredContent()} is the parsed (structured) output returned by the capability,
 * surfaced verbatim (never parsed by the SDK) when the connector provides it; absent otherwise.
 */
public record CapabilityResult(
    List<ContentItem> content, boolean isError, @Nullable Object structuredContent) {

  /** Result without structured content. */
  public static CapabilityResult of(List<ContentItem> content, boolean isError) {
    return new CapabilityResult(content, isError, null);
  }
}
