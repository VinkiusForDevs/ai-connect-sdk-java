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

/** A normalized list response. {@code meta}/{@code links} only present on page-based endpoints. */
public record Paginated<T>(List<T> data, @Nullable PageMeta meta, @Nullable PageLinks links) {

  /** A bare list page (no pagination metadata). */
  public static <T> Paginated<T> of(List<T> data) {
    return new Paginated<>(data, null, null);
  }
}
