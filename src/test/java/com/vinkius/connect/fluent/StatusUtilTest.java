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

import static org.assertj.core.api.Assertions.assertThat;

import com.vinkius.connect.model.Connection;
import com.vinkius.connect.model.ConnectorStatus;
import com.vinkius.connect.model.ConnectorSummary;
import org.junit.jupiter.api.Test;

class StatusUtilTest {

  private static Connection makeConnection(boolean ready, String status, String slug) {
    return new Connection(
        "conn_1", slug, slug != null ? slug : "my-server", null, status, ready, null, null);
  }

  private static Connection makeConnection() {
    return makeConnection(true, "active", "github");
  }

  @Test
  void returnsReadyWhenActiveAndReady() {
    assertThat(StatusUtil.deriveStatus(makeConnection())).isEqualTo(ConnectorStatus.READY);
  }

  @Test
  void returnsNeedsCredentialsWhenActiveButNotReady() {
    assertThat(StatusUtil.deriveStatus(makeConnection(false, "active", "github")))
        .isEqualTo(ConnectorStatus.NEEDS_CREDENTIALS);
  }

  @Test
  void returnsDisabledWhenStatusIsNotActive() {
    assertThat(StatusUtil.deriveStatus(makeConnection(true, "suspended", "github")))
        .isEqualTo(ConnectorStatus.DISABLED);
    assertThat(StatusUtil.deriveStatus(makeConnection(true, "inactive", "github")))
        .isEqualTo(ConnectorStatus.DISABLED);
  }

  @Test
  void returnsSummaryWithSlugStatusAndConnectionId() {
    ConnectorSummary summary = StatusUtil.summarize(makeConnection());
    assertThat(summary).isEqualTo(new ConnectorSummary("github", ConnectorStatus.READY, "conn_1"));
  }

  @Test
  void fallsBackToNameWhenSlugIsNull() {
    Connection connection =
        new Connection("conn_1", null, "my-server", null, "active", true, null, null);
    assertThat(StatusUtil.summarize(connection).slug()).isEqualTo("my-server");
  }
}
