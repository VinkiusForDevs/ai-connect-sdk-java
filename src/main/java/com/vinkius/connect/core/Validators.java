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

import com.vinkius.connect.error.ConfigException;
import java.util.regex.Pattern;

/**
 * Input guards for the low-level API.
 *
 * <p>App users are addressed by their client-supplied {@code external_id} — the value is placed
 * directly into the request path. The value must be URL-path-safe: a {@code /} (encoded {@code
 * %2F}) and whitespace are rejected by common routing layers, so they are rejected up front with a
 * clear error.
 */
public final class Validators {

  private static final Pattern UNSAFE_EXTERNAL_ID = Pattern.compile("[\\s/\\\\]");

  private Validators() {}

  /** Throw unless {@code value} is a usable, URL-path-safe external id. */
  public static void assertExternalId(String value) {
    if (value.isEmpty() || value.length() > 255) {
      throw new ConfigException(
          "`externalId` must be a non-empty string of at most 255 characters.");
    }
    if (UNSAFE_EXTERNAL_ID.matcher(value).find()) {
      throw new ConfigException(
          "Invalid externalId \"" + value + "\": it must not contain whitespace, \"/\" or \"\\\".");
    }
  }
}
