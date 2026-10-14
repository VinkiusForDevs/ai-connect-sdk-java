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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Credential field types as described by a connector's {@code credential_schema}. */
public enum CredentialType {
  API_KEY("api_key"),
  TOKEN("token"),
  PASSWORD("password"),
  CONNECTION_STRING("connection_string"),
  STRING("string"),
  NUMBER("number"),
  EMAIL("email"),
  URL("url"),
  SELECT("select"),
  BOOLEAN("boolean"),
  OAUTH2("oauth2");

  private final String value;

  CredentialType(String value) {
    this.value = value;
  }

  /** The wire representation of this credential type. */
  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static CredentialType fromValue(String value) {
    for (CredentialType type : values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown credential type: " + value);
  }
}
