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

import com.vinkius.connect.helpers.FakeHttpSender;
import com.vinkius.connect.helpers.FakeHttpSender.MockResponse;
import com.vinkius.connect.helpers.FakeHttpSender.RecordedCall;
import com.vinkius.connect.helpers.FakeHttpSender.Route;
import com.vinkius.connect.helpers.Fixtures;
import com.vinkius.connect.helpers.MockSdk;
import com.vinkius.connect.model.CredentialField;
import com.vinkius.connect.model.CredentialStatus;
import com.vinkius.connect.model.CredentialType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class CredentialsTest {

  private static final Route LIST_ROUTE =
      Route.get(
          "^/apps/vk_app_test/users/customer-123/mcps$",
          call ->
              MockResponse.ok(Map.of("data", List.of(Fixtures.connection("conn_1", "github")))));

  private static final Route CATALOG_ROUTE =
      Route.get(
          "^/catalog/mcps/github$",
          call ->
              MockResponse.ok(
                  Map.of(
                      "data",
                      Map.of(
                          "id",
                          "github",
                          "slug",
                          "github",
                          "credential_schema",
                          Map.of("GITHUB_TOKEN", Map.of("type", "api_key", "required", true))))));

  private static final Route PUT_ROUTE =
      Route.put(
          "^/apps/vk_app_test/users/customer-123/mcps/conn_1/credentials$",
          call ->
              MockResponse.ok(
                  Map.of(
                      "schema", Map.of("GITHUB_TOKEN", Map.of("type", "api_key")),
                      "configured", Map.of("GITHUB_TOKEN", true))));

  @Test
  void readsSchemaFromCatalog() {
    FakeHttpSender sender = FakeHttpSender.of(CATALOG_ROUTE);
    Vinkius vinkius = MockSdk.make(sender);

    Map<String, CredentialField> schema =
        vinkius.user(Fixtures.USER).connector("github").credentials().schema();

    CredentialField field = Objects.requireNonNull(schema.get("GITHUB_TOKEN"), "GITHUB_TOKEN");
    assertThat(field.type()).isEqualTo(CredentialType.API_KEY);
  }

  @Test
  void cachesSchemaPerClientInstance() {
    FakeHttpSender sender = FakeHttpSender.of(CATALOG_ROUTE);
    Vinkius vinkius = MockSdk.make(sender);
    CredentialsHandle credentials = vinkius.user(Fixtures.USER).connector("github").credentials();

    credentials.schema();
    credentials.schema();

    long catalogCalls =
        sender.calls().stream().filter(call -> call.path().startsWith("/catalog/")).count();
    assertThat(catalogCalls).isEqualTo(1);
  }

  @Test
  void wrapsSetValuesIntoCredentialsEnvelope() {
    FakeHttpSender sender = FakeHttpSender.of(LIST_ROUTE, PUT_ROUTE);
    Vinkius vinkius = MockSdk.make(sender);

    CredentialStatus status =
        vinkius
            .user(Fixtures.USER)
            .connector("github")
            .credentials()
            .set(Map.of("GITHUB_TOKEN", "ghp_x"));

    assertThat(status.configured()).containsEntry("GITHUB_TOKEN", true);
    assertThat(putCall(sender).body())
        .isEqualTo(Map.of("credentials", Map.of("GITHUB_TOKEN", "ghp_x")));
  }

  @Test
  void sendsMergeTrueSoServerPreservesUnsentCredentials() {
    FakeHttpSender sender = FakeHttpSender.of(LIST_ROUTE, PUT_ROUTE);
    Vinkius vinkius = MockSdk.make(sender);

    vinkius
        .user(Fixtures.USER)
        .connector("github")
        .credentials()
        .set(
            Map.of("GITHUB_TOKEN", "ghp_new"), SetCredentialsOptions.builder().merge(true).build());

    assertThat(putCall(sender).body())
        .isEqualTo(Map.of("credentials", Map.of("GITHUB_TOKEN", "ghp_new"), "merge", true));
  }

  private static RecordedCall putCall(FakeHttpSender sender) {
    return sender.calls().stream()
        .filter(call -> "PUT".equals(call.method()))
        .findFirst()
        .orElseThrow();
  }
}
