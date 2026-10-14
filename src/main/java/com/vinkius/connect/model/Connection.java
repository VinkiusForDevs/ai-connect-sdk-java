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
 * A connection — an established connector for a user (isolated per user).
 *
 * <p>{@code slug} is the connector's catalog identifier. {@link #ready()} is the single source of
 * truth for readiness (active + required credentials). {@link #runtimeUrl()} is the fully-qualified
 * runtime endpoint {@code {RUNTIME}/{token}/mcp} — populated by {@code connect()} after a {@code
 * vk_live_*} token is provisioned; treat it as a secret (the embedded token authenticates the
 * request). Present only when a token was minted on that call.
 */
public record Connection(
    String id,
    @Nullable String slug,
    String name,
    @Nullable String description,
    String status,
    boolean ready,
    @Nullable Integer tokensCount,
    @Nullable String runtimeUrl) {

  /** Copy of this connection with the (secret) runtime URL attached. */
  public Connection withRuntimeUrl(String url) {
    return new Connection(id, slug, name, description, status, ready, tokensCount, url);
  }
}
