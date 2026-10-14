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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vinkius.connect.core.Json;
import org.junit.jupiter.api.Test;

class ConnectorStatusTest {

  @Test
  void roundtripsEveryWireValue() {
    for (ConnectorStatus status : ConnectorStatus.values()) {
      assertThat(ConnectorStatus.fromValue(status.value())).isSameAs(status);
    }
    assertThat(ConnectorStatus.NOT_CONNECTED.value()).isEqualTo("not_connected");
    assertThat(ConnectorStatus.NEEDS_CREDENTIALS.value()).isEqualTo("needs_credentials");
    assertThat(ConnectorStatus.READY.value()).isEqualTo("ready");
    assertThat(ConnectorStatus.DISABLED.value()).isEqualTo("disabled");
  }

  @Test
  void jsonUsesTheWireRepresentation() {
    assertThat(Json.write(ConnectorStatus.READY)).isEqualTo("\"ready\"");
    assertThat(Json.convert("ready", ConnectorStatus.class)).isEqualTo(ConnectorStatus.READY);
  }

  @Test
  void rejectsUnknownWireValues() {
    assertThatThrownBy(() -> ConnectorStatus.fromValue("exploded"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown connector status");
  }
}
