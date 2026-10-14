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
package com.vinkius.connect.core;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Secret redaction for observability hooks.
 *
 * <p>The SDK never logs by default, but any data handed to a user-provided hook is scrubbed first
 * so that credentials, tokens, and Authorization headers can never leak through logging.
 */
public final class Redactor {

  static final String REDACTED = "[REDACTED]";
  private static final int MAX_DEPTH = 6;

  private static final Set<String> SECRET_HEADERS =
      Set.of("authorization", "idempotency-key", "cookie", "set-cookie");

  private static final Set<String> SECRET_KEYS =
      Set.of(
          "authorization",
          "apikey",
          "api_key",
          "token",
          "access_token",
          "refresh_token",
          "mcp_url",
          "credentials",
          "password",
          "secret",
          "client_secret");

  private Redactor() {}

  /** Return a copy of headers with secret values redacted. */
  public static Map<String, String> redactHeaders(Map<String, String> headers) {
    Map<String, String> out = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      out.put(
          entry.getKey(),
          SECRET_HEADERS.contains(entry.getKey().toLowerCase(Locale.ROOT))
              ? REDACTED
              : entry.getValue());
    }
    return out;
  }

  /**
   * Mask any {@code vk_live_*} path segment in a URL. The data-plane runtime endpoint embeds its
   * bearer token in the path ({@code {RUNTIME}/{vk_live_*}/mcp}), so the raw URL must never reach
   * an observability hook.
   */
  public static String redactUrl(String url) {
    return url.replaceAll("vk_live_[A-Za-z0-9_-]+", REDACTED);
  }

  /**
   * Deep-clone a value, redacting any property whose key looks like a secret. Depth-limited and
   * cycle-safe: only the active recursion path counts as a cycle (shared diamond references clone
   * again instead of being flagged).
   */
  public static @Nullable Object redactBody(@Nullable Object value) {
    return clone(value, 0, new IdentityHashMap<>());
  }

  private static @Nullable Object clone(
      @Nullable Object value, int depth, IdentityHashMap<Object, Boolean> seen) {
    if (value == null || !(value instanceof Map<?, ?> || value instanceof List<?>)) {
      return value;
    }
    if (depth >= MAX_DEPTH) {
      return "[TRUNCATED]";
    }
    if (seen.containsKey(value)) {
      return "[CIRCULAR]";
    }
    seen.put(value, Boolean.TRUE);
    try {
      if (value instanceof List<?> list) {
        List<Object> out = new ArrayList<>(list.size());
        for (Object item : list) {
          out.add(clone(item, depth + 1, seen));
        }
        return out;
      }
      Map<String, ?> record = Json.asMap(value);
      Map<String, Object> out = new LinkedHashMap<>();
      if (record != null) {
        for (Map.Entry<String, ?> entry : record.entrySet()) {
          String key = entry.getKey();
          out.put(
              key,
              SECRET_KEYS.contains(key.toLowerCase(Locale.ROOT))
                  ? REDACTED
                  : clone(entry.getValue(), depth + 1, seen));
        }
      }
      return out;
    } finally {
      seen.remove(value);
    }
  }
}
