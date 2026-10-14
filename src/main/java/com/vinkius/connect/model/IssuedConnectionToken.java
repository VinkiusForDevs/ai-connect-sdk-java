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

import org.jspecify.annotations.Nullable;

/**
 * A provisioned data-plane access token for a connection.
 *
 * <p>The plaintext token and its runtime URL are returned ONLY at creation/rotation — never
 * re-fetched. Every tool call through {@link #mcpUrl()} is metered against this token, which the
 * user can disable or delete (kill switch).
 */
public record IssuedConnectionToken(
    String id,
    String name,
    boolean isEnabled,
    @Nullable String token,
    @Nullable String mcpUrl,
    String createdAt) {}
