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
package com.vinkius.connect.resources;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Input for {@link AppUsersClient#create} — get-or-create by {@code external_id} (idempotent). */
public record CreateAppUserInput(
    String externalId, @Nullable String status, @Nullable Map<String, Object> metadata) {

  /** Create with only the external id. */
  public static CreateAppUserInput of(String externalId) {
    return new CreateAppUserInput(externalId, null, null);
  }
}
