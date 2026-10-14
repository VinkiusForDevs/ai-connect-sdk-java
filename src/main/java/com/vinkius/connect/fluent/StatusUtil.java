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

import com.vinkius.connect.model.Connection;
import com.vinkius.connect.model.ConnectorStatus;
import com.vinkius.connect.model.ConnectorSummary;

/**
 * Map a {@link Connection} to its derived {@link ConnectorStatus} (the Java counterpart of the JS
 * {@code status} module).
 */
public final class StatusUtil {

  private StatusUtil() {}

  /**
   * The backend owns "readiness" ({@code Connection.ready}); the SDK only maps it. {@code
   * not_connected} is handled by the caller when no connection matches.
   */
  public static ConnectorStatus deriveStatus(Connection connection) {
    if (!"active".equals(connection.status())) {
      return ConnectorStatus.DISABLED;
    }
    return connection.ready() ? ConnectorStatus.READY : ConnectorStatus.NEEDS_CREDENTIALS;
  }

  /** Summary of a connection with derived status ({@code slug} falls back to the display name). */
  public static ConnectorSummary summarize(Connection connection) {
    String slug = connection.slug() != null ? connection.slug() : connection.name();
    return new ConnectorSummary(slug, deriveStatus(connection), connection.id());
  }
}
