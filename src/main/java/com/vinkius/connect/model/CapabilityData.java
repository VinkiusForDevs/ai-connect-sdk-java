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
 * Raw capability shape returned by the execution plane (MCP {@code tools/list}).
 *
 * <p>Fields {@link #connector()} and {@link #connectionId()} are present only on the aggregated
 * user-scoped endpoint; runtime listings carry only the tool fields.
 */
public record CapabilityData(
    String name,
    @Nullable String title,
    @Nullable String description,
    @Nullable Map<String, Object> inputSchema,
    @Nullable Object annotations,
    @Nullable String connector,
    @Nullable String connectionId) {}
