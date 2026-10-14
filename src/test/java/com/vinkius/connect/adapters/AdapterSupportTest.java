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
package com.vinkius.connect.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vinkius.connect.error.NotFoundException;
import com.vinkius.connect.fluent.Capability;
import com.vinkius.connect.model.CapabilityResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class AdapterSupportTest {

  private static Capability capability(String connector, String rawName) {
    return Capability.build(
        new com.vinkius.connect.model.CapabilityData(
            rawName, "Title", "Description", Map.of("type", "object"), null, null, null),
        new Capability.BuildCapabilityContext(
            connector,
            "conn_1",
            (c, n) -> c + "__" + n,
            (name, args, options) ->
                CompletableFuture.completedFuture(CapabilityResult.of(List.of(), false))));
  }

  @Test
  void parseArgsToleratesMalformedJsonAndExecutesWithEmptyObject() {
    assertThat(AdapterSupport.parseArgs("{broken json")).isEmpty();
    assertThat(AdapterSupport.parseArgs("")).isEmpty();
    assertThat(AdapterSupport.parseArgs(null)).isEmpty();
    assertThat(AdapterSupport.parseArgs("[1,2]")).isEmpty();
    assertThat(AdapterSupport.parseArgs("{\"title\":\"x\"}")).isEqualTo(Map.of("title", "x"));
  }

  @Test
  void findCapabilityMatchesDisplayOrRawName() {
    List<Capability> capabilities = List.of(capability("github", "create_issue"));

    Capability byDisplay = AdapterSupport.findCapability(capabilities, "github__create_issue");
    Capability byRaw = AdapterSupport.findCapability(capabilities, "create_issue");

    assertThat(byDisplay.rawName()).isEqualTo("create_issue");
    assertThat(byRaw.name()).isEqualTo("github__create_issue");
    assertThrows(
        NotFoundException.class, () -> AdapterSupport.findCapability(capabilities, "nope"));
  }
}
