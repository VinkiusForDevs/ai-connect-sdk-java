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

/** Derived state of a connector for a user. */
public enum ConnectorStatus {
  NOT_CONNECTED("not_connected"),
  NEEDS_CREDENTIALS("needs_credentials"),
  READY("ready"),
  DISABLED("disabled");

  private final String value;

  ConnectorStatus(String value) {
    this.value = value;
  }

  /** The wire representation of this status. */
  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static ConnectorStatus fromValue(String value) {
    for (ConnectorStatus status : values()) {
      if (status.value.equals(value)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown connector status: " + value);
  }
}
