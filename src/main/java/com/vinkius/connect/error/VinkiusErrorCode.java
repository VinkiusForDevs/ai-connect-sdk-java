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

/** Stable, machine-readable error codes (wire values identical to the JS SDK). */
public enum VinkiusErrorCode {
  CONFIG_ERROR("config_error"),
  AUTH_ERROR("auth_error"),
  NOT_FOUND("not_found"),
  VALIDATION_ERROR("validation_error"),
  RATE_LIMIT("rate_limit"),
  QUOTA_EXCEEDED("quota_exceeded"),
  OVERAGE_BLOCKED("overage_blocked"),
  CONNECTOR_NOT_CONNECTED("connector_not_connected"),
  NOT_IMPLEMENTED("not_implemented"),
  CONNECTION_ERROR("connection_error"),
  PROTOCOL_ERROR("protocol_error"),
  API_ERROR("api_error");

  private final String value;

  VinkiusErrorCode(String value) {
    this.value = value;
  }

  /** The stable wire representation of this code. */
  public String value() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }
}
