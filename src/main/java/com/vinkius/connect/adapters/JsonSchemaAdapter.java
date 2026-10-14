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
package com.vinkius.connect.adapters;

import com.vinkius.connect.fluent.Capability;
import com.vinkius.connect.model.CapabilityResult;
import com.vinkius.connect.model.ExecuteOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Neutral JSON Schema adapter (the Java counterpart of {@code @vinkius/connect/json-schema}).
 *
 * <p>The lowest common denominator: converts Vinkius capabilities into plain function definitions
 * {@code { name, description, parameters }} (JSON Schema). Use it with any OpenAI-compatible
 * provider, or as the bridge for environments outside this SDK (e.g. feeding a custom agent loop).
 */
public final class JsonSchemaAdapter {

  private JsonSchemaAdapter() {}

  /**
   * One neutral tool definition: a name, a description, and its JSON Schema {@code parameters}.
   *
   * @param name display name (namespaced)
   * @param description human-readable description
   * @param parameters JSON Schema of the arguments (never empty)
   */
  public record JSONSchemaTool(String name, String description, Map<String, Object> parameters) {}

  /** Convert Vinkius capabilities to neutral JSON Schema tool definitions. */
  public static List<JSONSchemaTool> toJSONSchemaTools(List<Capability> capabilities) {
    List<JSONSchemaTool> tools = new ArrayList<>(capabilities.size());
    for (Capability capability : capabilities) {
      tools.add(
          new JSONSchemaTool(
              capability.name(),
              capability.description(),
              AdapterSupport.normalizeParams(capability.inputSchema())));
    }
    return tools;
  }

  /** Execute a capability by (namespaced or raw) name — a generic dispatch helper. */
  public static CapabilityResult executeByName(
      List<Capability> capabilities,
      String name,
      @Nullable Map<String, Object> args,
      ExecuteOptions options) {
    Capability capability = AdapterSupport.findCapability(capabilities, name);
    return capability.execute(args != null ? args : Map.of(), options);
  }

  /** Execute with default options. */
  public static CapabilityResult executeByName(
      List<Capability> capabilities, String name, @Nullable Map<String, Object> args) {
    return executeByName(capabilities, name, args, ExecuteOptions.EMPTY);
  }

  /** Async {@link #executeByName}. */
  public static CompletableFuture<CapabilityResult> executeByNameAsync(
      List<Capability> capabilities,
      String name,
      @Nullable Map<String, Object> args,
      ExecuteOptions options) {
    Capability capability = AdapterSupport.findCapability(capabilities, name);
    return capability.executeAsync(args != null ? args : Map.of(), options);
  }
}
