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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.vinkius.connect.error.ProtocolException;
import com.vinkius.connect.model.PageLinks;
import com.vinkius.connect.model.PageMeta;
import com.vinkius.connect.model.Paginated;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaginationTest {

  @Test
  void normalizesFullLaravelPaginatedResponse() {
    java.util.Map<String, Object> links = new java.util.LinkedHashMap<>();
    links.put("first", "/foo?page=1");
    links.put("last", "/foo?page=3");
    links.put("prev", null);
    links.put("next", "/foo?page=2");
    Map<String, Object> body =
        Map.of(
            "data",
            List.of(Map.of("id", "1"), Map.of("id", "2")),
            "meta",
            Map.of(
                "current_page", 1,
                "from", 1,
                "last_page", 3,
                "path", "/foo",
                "per_page", 2,
                "to", 2,
                "total", 6),
            "links",
            links);

    Paginated<JsonNode> result = Pagination.normalizePaginated(body, JsonNode.class);

    assertThat(result.data()).hasSize(2);
    assertThat(result.meta()).isNotNull();
    PageMeta meta = java.util.Objects.requireNonNull(result.meta(), "meta");
    assertThat(meta.total()).isEqualTo(6);
    assertThat(result.links()).isNotNull();
    PageLinks pageLinks = java.util.Objects.requireNonNull(result.links(), "links");
    assertThat(pageLinks.next()).isEqualTo("/foo?page=2");
  }

  @Test
  void normalizesBareDataEnvelopeWithoutMetaOrLinks() {
    Map<String, Object> body = Map.of("data", List.of(Map.of("id", "a")));

    Paginated<JsonNode> result = Pagination.normalizePaginated(body, JsonNode.class);

    assertThat(result.data()).hasSize(1);
    assertThat(result.meta()).isNull();
    assertThat(result.links()).isNull();
  }

  @Test
  void rejectsMalformedPaginatedResponse() {
    assertThatThrownBy(() -> Pagination.normalizePaginated(null, JsonNode.class))
        .isInstanceOf(ProtocolException.class)
        .hasMessageContaining("Expected a list response");
    assertThatThrownBy(() -> Pagination.normalizePaginated(null, JsonNode.class))
        .isInstanceOf(ProtocolException.class);
  }

  @Test
  void unwrapsDataEnvelope() {
    List<Integer> list = Pagination.unwrapList(Map.of("data", List.of(1, 2, 3)), Integer.class);
    assertThat(list).containsExactly(1, 2, 3);
  }

  @Test
  void passesThroughBareArray() {
    List<Integer> list = Pagination.unwrapList(List.of(4, 5), Integer.class);
    assertThat(list).containsExactly(4, 5);
  }

  @Test
  void rejectsNullInsteadOfTreatingMalformedResponseAsEmpty() {
    assertThatThrownBy(() -> Pagination.unwrapList(null, Integer.class))
        .isInstanceOf(ProtocolException.class)
        .hasMessageContaining("Expected a list response");
  }

  @Test
  void unwrapsDataItemEnvelope() {
    Map<String, Object> body = Map.of("data", Map.of("id", "x", "name", "foo"));
    JsonNode item = Pagination.unwrapItem(body, JsonNode.class);
    assertThat(item.get("id").asText()).isEqualTo("x");
    assertThat(item.get("name").asText()).isEqualTo("foo");
  }

  @Test
  void returnsBodyItselfWhenNoDataWrapper() {
    Map<String, Object> bare = Map.of("id", "x", "name", "foo");
    JsonNode item = Pagination.unwrapItem(bare, JsonNode.class);
    assertThat(item.get("id").asText()).isEqualTo("x");
    assertThat(item.get("name").asText()).isEqualTo("foo");
  }
}
