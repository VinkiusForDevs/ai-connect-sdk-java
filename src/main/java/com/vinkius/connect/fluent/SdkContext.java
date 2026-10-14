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
package com.vinkius.connect.fluent;

import com.vinkius.connect.core.HttpClient;
import com.vinkius.connect.core.ResolverCache;
import com.vinkius.connect.core.RuntimeClient;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Shared runtime context threaded through the fluent layer (the Java counterpart of the JS {@code
 * SdkContext} interface).
 *
 * @param http the control-plane HTTP client (API auth bound)
 * @param appId the public application id
 * @param cache shared, process-scoped TTL cache for NON-SECRET, stable resolution data (per the
 *     cache golden rule, secrets must never enter this cache)
 * @param namespace controls how a capability's display name is namespaced across connectors
 * @param runtime builds a client for the MCP runtime — the ONLY surface for tool listing and
 *     execution; given a connection's {@code mcp_url} (which embeds its {@code vk_live_*} token),
 *     returns a client that talks JSON-RPC directly to the runtime
 */
public record SdkContext(
    HttpClient http,
    String appId,
    ResolverCache cache,
    BiFunction<String, String, String> namespace,
    Function<String, RuntimeClient> runtime) {

  /** Convenience factory mirroring the JS object-literal construction. */
  public static SdkContext of(
      HttpClient http,
      String appId,
      ResolverCache cache,
      @Nullable BiFunction<String, String, String> namespace,
      Function<String, RuntimeClient> runtime) {
    BiFunction<String, String, String> ns =
        namespace != null ? namespace : (connector, name) -> connector + "__" + name;
    return new SdkContext(http, appId, cache, ns, runtime);
  }
}
