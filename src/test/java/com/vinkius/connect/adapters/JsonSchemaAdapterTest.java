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

import com.vinkius.connect.fluent.Capability;
import com.vinkius.connect.fluent.CapabilitySet;
import com.vinkius.connect.fluent.Vinkius;
import com.vinkius.connect.helpers.FakeHttpSender;
import com.vinkius.connect.helpers.FakeHttpSender.MockResponse;
import com.vinkius.connect.helpers.FakeHttpSender.Route;
import com.vinkius.connect.helpers.Fixtures;
import com.vinkius.connect.helpers.MockSdk;
import com.vinkius.connect.model.CapabilityResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonSchemaAdapterTest {

  private static CapabilitySet getCapabilities(FakeHttpSender sender) {
    Vinkius vinkius = MockSdk.make(sender);
    return vinkius.user(Fixtures.USER).capabilities();
  }

  private static FakeHttpSender routes() {
    return FakeHttpSender.of(
        Route.get(
            "^/apps/vk_app_test/users/customer-123/mcps$",
            call ->
                MockResponse.ok(
                    Map.of(
                        "data", List.of(Fixtures.connection("conn_1", "github", true, "active"))))),
        Fixtures.tokenRoute("conn_1"),
        Fixtures.runtimeRoute());
  }

  @Test
  void convertsCapabilitiesToNeutralJsonSchemaToolsAndExecutesByName() {
    FakeHttpSender sender = routes();
    CapabilitySet capabilities = getCapabilities(sender);

    List<JsonSchemaAdapter.JSONSchemaTool> tools =
        JsonSchemaAdapter.toJSONSchemaTools(capabilities.asList());

    assertThat(tools).hasSize(1);
    JsonSchemaAdapter.JSONSchemaTool tool = tools.get(0);
    assertThat(tool.name()).isEqualTo("github__create_issue");
    assertThat(tool.parameters()).containsEntry("type", "object");

    CapabilityResult result =
        JsonSchemaAdapter.executeByName(
            capabilities.asList(), "create_issue", Map.of("title", "x"));
    assertThat(result.isError()).isFalse();
  }

  @Test
  void executeByNameFindsCapabilityByDisplayOrRawName() {
    FakeHttpSender sender = routes();
    CapabilitySet capabilities = getCapabilities(sender);
    List<Capability> list = capabilities.asList();

    CapabilityResult byRaw =
        JsonSchemaAdapter.executeByName(list, "create_issue", Map.of("title", "x"));
    CapabilityResult byDisplay =
        JsonSchemaAdapter.executeByName(list, "github__create_issue", Map.of("title", "x"));

    assertThat(byRaw.isError()).isFalse();
    assertThat(byDisplay.isError()).isFalse();
  }
}
