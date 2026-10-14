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
package com.vinkius.connect.error;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Maps an HTTP error response to the appropriate {@link VinkiusException} subclass.
 *
 * <p>Message extraction honors both {@code message} and {@code error} (plus MCP {@code content})
 * exactly like the JS SDK, so wire-level error text is never lost.
 */
public final class HttpErrorMapper {

  private HttpErrorMapper() {}

  /** Map an HTTP error response to the appropriate typed error. */
  public static VinkiusException map(
      int status,
      @Nullable Object body,
      @Nullable String requestId,
      @Nullable Integer retryAfterMs) {
    String message = pickMessage(body, "Request failed with status " + status);
    return switch (status) {
      case 401, 403 -> new AuthException(message, status, requestId, body);
      case 404 -> new NotFoundException(message, status, requestId, body);
      case 422 -> new ValidationException(message, pickErrors(body), status, requestId, body);
      case 402 -> new OverageException(message, status, requestId, body, pickUpgradeUrl(body));
      case 429 ->
          isMcpShaped(body)
              ? new QuotaException(message, status, requestId, body, pickUpgradeUrl(body))
              : new RateLimitException(message, status, requestId, body, retryAfterMs);
      default ->
          new VinkiusException(message, VinkiusErrorCode.API_ERROR, status, requestId, body, null);
    };
  }

  private static @Nullable Map<String, ?> asRecord(@Nullable Object value) {
    if (value instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, ?> asStringMap = (Map<String, ?>) map;
      return asStringMap;
    }
    return null;
  }

  /** Extract a human message from an API body, honoring both {@code message} and {@code error}. */
  static String pickMessage(@Nullable Object body, String fallback) {
    Map<String, ?> record = asRecord(body);
    if (record != null) {
      Object message = record.get("message");
      if (message instanceof String s && !s.isEmpty()) {
        return s;
      }
      Object error = record.get("error");
      if (error instanceof String s && !s.isEmpty()) {
        return s;
      }
      if (record.get("content") instanceof List<?> content) {
        for (Object entry : content) {
          Map<String, ?> item = asRecord(entry);
          if (item != null && item.get("text") instanceof String text && !text.isEmpty()) {
            return text;
          }
        }
      }
    }
    if (body instanceof String s && !s.isEmpty()) {
      return s;
    }
    return fallback;
  }

  /** Whether an API body is the MCP-shaped quota/overage envelope. */
  private static boolean isMcpShaped(@Nullable Object body) {
    Map<String, ?> record = asRecord(body);
    if (record == null) {
      return false;
    }
    return Boolean.TRUE.equals(record.get("isError"))
        || record.get("upgrade_url") instanceof String;
  }

  private static @Nullable String pickUpgradeUrl(@Nullable Object body) {
    Map<String, ?> record = asRecord(body);
    if (record != null && record.get("upgrade_url") instanceof String url) {
      return url;
    }
    return null;
  }

  private static Map<String, List<String>> pickErrors(@Nullable Object body) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    Map<String, ?> record = asRecord(body);
    if (record != null && record.get("errors") instanceof Map<?, ?> errors) {
      for (Map.Entry<?, ?> entry : errors.entrySet()) {
        List<String> messages = new ArrayList<>();
        if (entry.getValue() instanceof List<?> values) {
          for (Object value : values) {
            messages.add(String.valueOf(value));
          }
        }
        result.put(String.valueOf(entry.getKey()), messages);
      }
    }
    return result;
  }
}
