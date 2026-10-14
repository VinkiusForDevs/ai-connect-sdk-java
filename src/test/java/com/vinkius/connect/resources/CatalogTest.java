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
package com.vinkius.connect.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vinkius.connect.error.ProtocolException;
import com.vinkius.connect.fluent.Vinkius;
import com.vinkius.connect.helpers.FakeHttpSender;
import com.vinkius.connect.helpers.FakeHttpSender.MockResponse;
import com.vinkius.connect.helpers.FakeHttpSender.RecordedCall;
import com.vinkius.connect.helpers.FakeHttpSender.Route;
import com.vinkius.connect.helpers.MockSdk;
import com.vinkius.connect.model.CatalogConnector;
import com.vinkius.connect.model.CatalogSearchResult;
import com.vinkius.connect.model.Paginated;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class CatalogTest {

  @Test
  void normalizesPaginatedListResponse() {
    Map<String, Object> meta =
        Map.of(
            "current_page", 1,
            "from", 1,
            "last_page", 1,
            "path", "/",
            "per_page", 50,
            "to", 1,
            "total", 1);
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/catalog/mcps$",
                call ->
                    MockResponse.ok(
                        Map.of(
                            "data",
                            List.of(Map.of("id", "github", "slug", "github", "title", "GitHub")),
                            "meta",
                            meta))));
    Vinkius vinkius = MockSdk.make(sender);

    Paginated<CatalogConnector> page = vinkius.catalog().list();

    assertThat(page.data()).hasSize(1);
    assertThat(Objects.requireNonNull(page.meta(), "meta").total()).isEqualTo(1);
  }

  @Test
  void unwrapsSingleResourceDetailResponse() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/catalog/mcps/github$",
                call ->
                    MockResponse.ok(
                        Map.of(
                            "data",
                            Map.of(
                                "id", "github",
                                "slug", "github",
                                "credential_schema", Map.of())))));
    Vinkius vinkius = MockSdk.make(sender);

    var detail = vinkius.catalog().get("github");

    assertThat(detail.slug()).isEqualTo("github");
  }

  @Test
  void usesMarketplaceSearchEndpointAndNormalizesResults() {
    Map<String, Object> item =
        Map.of(
            "id", "coindesk-bitcoin-price-index",
            "slug", "coindesk-bitcoin-price-index",
            "title", "CoinDesk Bitcoin Price Index",
            "short_description", "Get real-time Bitcoin prices.",
            "publisher_type", "verified",
            "listing_type", "paid",
            "server_type", "managed",
            "tools_count", 8,
            "requires_auth", false);
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/marketplace/search$",
                call ->
                    MockResponse.ok(
                        Map.of("results", List.of(item), "has_more", true, "page", 1))));
    Vinkius vinkius = MockSdk.make(sender);

    CatalogSearchResult result = vinkius.catalog().search("cryptocurrency price");

    RecordedCall first = sender.call(0);
    assertThat(first.queryParam("q")).isEqualTo("cryptocurrency price");
    assertThat(first.queryParam("page")).isEqualTo("1");
    assertThat(result.hasMore()).isTrue();
    assertThat(result.page()).isEqualTo(1);
    // listing_type is taken straight from the wire — never derived from server_type.
    assertThat(result.items())
        .containsExactly(
            new CatalogConnector(
                "coindesk-bitcoin-price-index",
                "coindesk-bitcoin-price-index",
                "CoinDesk Bitcoin Price Index",
                "Get real-time Bitcoin prices.",
                "verified",
                "paid",
                false,
                "managed",
                8));
  }

  @Test
  void throwsProtocolErrorWhenSearchItemMissingListingType() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get(
                "^/marketplace/search$",
                call ->
                    MockResponse.ok(
                        Map.of(
                            "results",
                            List.of(Map.of("id", "x", "slug", "x", "title", "X")),
                            "has_more",
                            false,
                            "page",
                            1))));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.catalog().search("x")).isInstanceOf(ProtocolException.class);
  }

  @Test
  void throwsProtocolErrorWhenBodyMissingResultsArray() {
    FakeHttpSender sender =
        FakeHttpSender.of(
            Route.get("^/marketplace/search$", call -> MockResponse.ok(Map.of("foo", "bar"))));
    Vinkius vinkius = MockSdk.make(sender);

    assertThatThrownBy(() -> vinkius.catalog().search("x")).isInstanceOf(ProtocolException.class);
  }
}
