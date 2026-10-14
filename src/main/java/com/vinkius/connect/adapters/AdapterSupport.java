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

import com.vinkius.connect.core.Json;
import com.vinkius.connect.error.ConfigException;
import com.vinkius.connect.error.NotFoundException;
import com.vinkius.connect.fluent.Capability;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Shared helpers for framework adapters (the Java counterpart of the JS {@code adapters/shared}).
 */
public final class AdapterSupport {

  private AdapterSupport() {}

  /** Ensure a usable JSON Schema object for function/tool parameters. */
  public static Map<String, Object> normalizeParams(@Nullable Map<String, Object> schema) {
    if (schema != null && !schema.isEmpty()) {
      return schema;
    }
    return Map.of("type", "object", "properties", Map.of());
  }

  /**
   * Parse a JSON arguments string into an object, tolerating malformed input: the platform
   * validates tool input, so an unparsable payload executes as an empty argument set and surfaces
   * as an {@code isError} tool result the model can recover from — rather than throwing mid
   * agent-loop.
   */
  public static Map<String, Object> parseArgs(@Nullable String raw) {
    if (raw == null || raw.isEmpty()) {
      return Map.of();
    }
    try {
      Object parsed = Json.parse(raw);
      Map<String, ?> asMap = Json.asMap(parsed);
      if (asMap == null) {
        return Map.of();
      }
      return castArgs(asMap);
    } catch (RuntimeException error) {
      return Map.of();
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castArgs(Map<String, ?> value) {
    return (Map<String, Object>) value;
  }

  /** Find a capability by display name or raw name, throwing a typed error when absent. */
  public static Capability findCapability(List<Capability> capabilities, String name) {
    for (Capability candidate : capabilities) {
      if (candidate.name().equals(name) || candidate.rawName().equals(name)) {
        return candidate;
      }
    }
    throw new NotFoundException("Unknown capability: " + name);
  }

  /**
   * Guard against tool names a platform would reject (length/charset), throwing a clear {@link
   * ConfigException} up front instead of a cryptic platform 400 at call time.
   */
  public static void validateToolName(
      String name, String platform, Pattern pattern, int maxLength) {
    if (name.length() > maxLength || !pattern.matcher(name).matches()) {
      throw new ConfigException(
          "Capability \""
              + name
              + "\" cannot be exposed as a "
              + platform
              + " tool name: it must be "
              + maxLength
              + " char(s) or fewer and match "
              + pattern
              + " (got "
              + name.length()
              + " char(s)). "
              + "Override VinkiusOptions.namespaceCapability to produce platform-compatible names.");
    }
  }
}
