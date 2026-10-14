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

import java.nio.charset.StandardCharsets;

/**
 * Path-segment encoding with {@code encodeURIComponent} semantics (identical to the JS SDK), so
 * generated URLs match the wire contract byte for byte.
 */
public final class UriUtil {

  private UriUtil() {}

  /** Percent-encode one path segment exactly like JS {@code encodeURIComponent}. */
  public static String encodeSegment(String value) {
    StringBuilder out = new StringBuilder(value.length());
    for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
      char c = (char) (raw & 0xFF);
      if (isUnreserved(c)) {
        out.append(c);
      } else {
        out.append('%');
        out.append(Character.toUpperCase(Character.forDigit((raw >> 4) & 0xF, 16)));
        out.append(Character.toUpperCase(Character.forDigit(raw & 0xF, 16)));
      }
    }
    return out.toString();
  }

  private static boolean isUnreserved(char c) {
    return (c >= 'A' && c <= 'Z')
        || (c >= 'a' && c <= 'z')
        || (c >= '0' && c <= '9')
        || c == '-'
        || c == '_'
        || c == '.'
        || c == '!'
        || c == '~'
        || c == '*'
        || c == '\''
        || c == '('
        || c == ')';
  }
}
