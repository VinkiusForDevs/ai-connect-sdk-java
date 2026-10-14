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

/** An end-user of your application. {@code id} is the internal public id. */
public record AppUser(
    String id,
    String externalId,
    String status,
    @Nullable Map<String, Object> metadata,
    @Nullable String applicationId,
    @Nullable Integer mcpCount,
    String createdAt,
    String updatedAt) {}
