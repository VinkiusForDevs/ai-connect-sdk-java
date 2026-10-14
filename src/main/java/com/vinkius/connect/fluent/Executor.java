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

import com.vinkius.connect.core.RuntimeClient;
import com.vinkius.connect.model.RequestOptions;

/** Factory for a {@link Capability.CapabilityExecutor} bound to one connection's runtime. */
public final class Executor {

  private Executor() {}

  /**
   * Bind an executor to a connection's runtime client. Execution goes straight to the MCP runtime
   * (metered against the connection's {@code vk_live_*} token); the API is never an execution
   * surface.
   */
  public static Capability.CapabilityExecutor make(RuntimeClient runtime) {
    return (rawName, args, options) ->
        runtime.callToolAsync(rawName, args, options != null ? options : RequestOptions.EMPTY);
  }
}
