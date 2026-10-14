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

import com.vinkius.connect.core.Futures;
import com.vinkius.connect.core.HttpClient;
import com.vinkius.connect.core.HttpRequest;
import com.vinkius.connect.core.Json;
import com.vinkius.connect.core.Pagination;
import com.vinkius.connect.core.UriUtil;
import com.vinkius.connect.error.ProtocolException;
import com.vinkius.connect.model.CatalogConnector;
import com.vinkius.connect.model.CatalogConnectorDetail;
import com.vinkius.connect.model.CatalogSearchResult;
import com.vinkius.connect.model.Paginated;
import com.vinkius.connect.model.RequestOptions;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Catalog discovery.
 *
 * <p>{@code GET /catalog/mcps} → paginated {@link CatalogConnector}; {@code GET
 * /catalog/mcps/{slug}} → {@link CatalogConnectorDetail} (with credential schema); {@code GET
 * /marketplace/search?q=…&page=1} → {@link CatalogSearchResult}.
 */
public final class CatalogClient {

  private final HttpClient http;

  public CatalogClient(HttpClient http) {
    this.http = http;
  }

  /** List public connectors (page-based, 50 per page). */
  public CompletableFuture<Paginated<CatalogConnector>> listAsync(
      @Nullable Integer page, RequestOptions options) {
    HttpRequest request =
        HttpRequest.builder("GET", "/catalog/mcps").query("page", page).options(options).build();
    return http.<Object>requestAsync(request)
        .thenApply(body -> Pagination.normalizePaginated(body, CatalogConnector.class));
  }

  /** Blocking {@link #listAsync}. */
  public Paginated<CatalogConnector> list(@Nullable Integer page, RequestOptions options) {
    return Futures.join(listAsync(page, options));
  }

  /** List the first page. */
  public Paginated<CatalogConnector> list(RequestOptions options) {
    return list(null, options);
  }

  /** List the first page with default request options. */
  public Paginated<CatalogConnector> list() {
    return list(null, RequestOptions.EMPTY);
  }

  /** Async {@link #list} with default request options. */
  public CompletableFuture<Paginated<CatalogConnector>> listAsync() {
    return listAsync(null, RequestOptions.EMPTY);
  }

  /**
   * Iterate every catalog connector across all pages. Convenience over {@link #list} for callers
   * that want the full catalog without manual page bookkeeping. Blocking.
   */
  public Iterator<CatalogConnector> iterate(@Nullable Integer page, RequestOptions options) {
    Paginated<CatalogConnector> first = list(page, options);
    return Pagination.pageIterator(first, next -> list(next, options));
  }

  /** Fetch a single connector by slug (or uuid), including its credential schema. */
  public CompletableFuture<CatalogConnectorDetail> getAsync(String slug, RequestOptions options) {
    return http.<Object>getAsync("/catalog/mcps/" + UriUtil.encodeSegment(slug), options)
        .thenApply(body -> Pagination.unwrapItem(body, CatalogConnectorDetail.class));
  }

  /** Blocking {@link #getAsync}. */
  public CatalogConnectorDetail get(String slug, RequestOptions options) {
    return Futures.join(getAsync(slug, options));
  }

  /** Fetch with default request options. */
  public CatalogConnectorDetail get(String slug) {
    return get(slug, RequestOptions.EMPTY);
  }

  /** Search the marketplace; returns a paged {@link CatalogSearchResult}. */
  public CompletableFuture<CatalogSearchResult> searchAsync(
      String query, @Nullable Integer page, RequestOptions options) {
    HttpRequest request =
        HttpRequest.builder("GET", "/marketplace/search")
            .query("q", query)
            .query("page", page == null ? 1 : page)
            .options(options)
            .build();
    return http.<Object>requestAsync(request).thenApply(CatalogClient::normalizeMarketplaceSearch);
  }

  /** Blocking {@link #searchAsync}. */
  public CatalogSearchResult search(String query, @Nullable Integer page, RequestOptions options) {
    return Futures.join(searchAsync(query, page, options));
  }

  /** Search the marketplace (first page). */
  public CatalogSearchResult search(String query, RequestOptions options) {
    return search(query, null, options);
  }

  /** Search with default request options (first page). */
  public CatalogSearchResult search(String query) {
    return search(query, null, RequestOptions.EMPTY);
  }

  /** Async {@link #search} with default request options (first page). */
  public CompletableFuture<CatalogSearchResult> searchAsync(String query) {
    return searchAsync(query, null, RequestOptions.EMPTY);
  }

  /**
   * Normalize a marketplace/search body; a missing {@code results} array throws a {@link
   * ProtocolException}.
   */
  public static CatalogSearchResult normalizeMarketplaceSearch(Object body) {
    Map<String, ?> response = Json.asMap(body);
    if (response == null || !(response.get("results") instanceof List<?> results)) {
      throw new ProtocolException(
          "Malformed marketplace search response: missing results array.", body);
    }
    List<CatalogConnector> items = new ArrayList<>(results.size());
    int index = 0;
    for (Object item : results) {
      items.add(toSearchConnector(item, index));
      index++;
    }
    boolean hasMore = Boolean.TRUE.equals(response.get("has_more"));
    int page = response.get("page") instanceof Number number ? number.intValue() : 1;
    return new CatalogSearchResult(items, hasMore, page);
  }

  /**
   * Map one marketplace/search result item to a {@link CatalogConnector}; malformed items throw.
   */
  private static CatalogConnector toSearchConnector(Object item, int index) {
    Map<String, ?> it = Json.asMap(item);
    if (it == null) {
      throw new ProtocolException(
          "Malformed marketplace search item at index " + index + ": missing id.", item);
    }
    Object id = it.get("id");
    if (!(id instanceof String idText) || idText.trim().isEmpty()) {
      throw new ProtocolException(
          "Malformed marketplace search item at index " + index + ": missing id.", item);
    }
    Object slug = it.get("slug");
    if (!(slug instanceof String slugText) || slugText.trim().isEmpty()) {
      throw new ProtocolException(
          "Malformed marketplace search item at index " + index + ": missing slug.", item);
    }
    Object title = it.get("title");
    if (!(title instanceof String titleText)) {
      throw new ProtocolException(
          "Malformed marketplace search item at index " + index + ": missing title.", item);
    }
    Object listingType = it.get("listing_type");
    if (!(listingType instanceof String listing)) {
      throw new ProtocolException(
          "Malformed marketplace search item at index " + index + ": missing listing_type.", item);
    }
    String shortDescription = it.get("short_description") instanceof String text ? text : null;
    String publisherType = it.get("publisher_type") instanceof String text ? text : "community";
    // The marketplace wire key is `requires_auth`; catalog detail uses `requires_buyer_auth`.
    boolean requiresBuyerAuth =
        Boolean.TRUE.equals(it.get("requires_buyer_auth"))
            || Boolean.TRUE.equals(it.get("requires_auth"));
    String serverType = it.get("server_type") instanceof String text ? text : null;
    Integer toolsCount = it.get("tools_count") instanceof Number number ? number.intValue() : null;
    return new CatalogConnector(
        idText,
        slugText,
        titleText,
        shortDescription,
        publisherType,
        listing,
        requiresBuyerAuth,
        serverType,
        toolsCount);
  }
}
