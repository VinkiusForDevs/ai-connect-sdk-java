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
import com.vinkius.connect.core.Pagination;
import com.vinkius.connect.core.UriUtil;
import com.vinkius.connect.core.Validators;
import com.vinkius.connect.model.AppUser;
import com.vinkius.connect.model.Paginated;
import com.vinkius.connect.model.RequestOptions;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Application end-users (low-level, 1:1 with the API).
 *
 * <p>Users are addressed by their client-supplied {@code external_id}; {@code create} is idempotent
 * (the API upserts by {@code (application_id, external_id)}). Tool listing/execution is NOT here —
 * that is the runtime's job, reached per connection via {@link #connections}.
 */
public final class AppUsersClient {

  private final HttpClient http;
  private final String appId;

  public AppUsersClient(HttpClient http, String appId) {
    this.http = http;
    this.appId = appId;
  }

  private String base() {
    return "/apps/" + UriUtil.encodeSegment(appId) + "/users";
  }

  private String userPath(String externalId) {
    Validators.assertExternalId(externalId);
    return base() + "/" + UriUtil.encodeSegment(externalId);
  }

  /** Get-or-create a user by external id (idempotent). */
  public CompletableFuture<AppUser> createAsync(CreateAppUserInput input, RequestOptions options) {
    Validators.assertExternalId(input.externalId());
    return http.<Object>postAsync(base(), input, true, options)
        .thenApply(body -> Pagination.unwrapItem(body, AppUser.class));
  }

  /** Blocking {@link #createAsync}. */
  public AppUser create(CreateAppUserInput input, RequestOptions options) {
    return Futures.join(createAsync(input, options));
  }

  /** Blocking {@link #createAsync} with default request options. */
  public AppUser create(CreateAppUserInput input) {
    return create(input, RequestOptions.EMPTY);
  }

  /** Fetch one user by external id. */
  public CompletableFuture<AppUser> getAsync(String externalId, RequestOptions options) {
    return http.<Object>getAsync(userPath(externalId), options)
        .thenApply(body -> Pagination.unwrapItem(body, AppUser.class));
  }

  /** Blocking {@link #getAsync}. */
  public AppUser get(String externalId, RequestOptions options) {
    return Futures.join(getAsync(externalId, options));
  }

  /** Fetch with default request options. */
  public AppUser get(String externalId) {
    return get(externalId, RequestOptions.EMPTY);
  }

  /** Partially update a user (status and/or metadata). */
  public CompletableFuture<AppUser> updateAsync(
      String externalId, UpdateAppUserInput patch, RequestOptions options) {
    return http.<Object>patchAsync(userPath(externalId), patch, options)
        .thenApply(body -> Pagination.unwrapItem(body, AppUser.class));
  }

  /** Blocking {@link #updateAsync}. */
  public AppUser update(String externalId, UpdateAppUserInput patch, RequestOptions options) {
    return Futures.join(updateAsync(externalId, patch, options));
  }

  /** Update with default request options. */
  public AppUser update(String externalId, UpdateAppUserInput patch) {
    return update(externalId, patch, RequestOptions.EMPTY);
  }

  /** Delete a user by external id. */
  public CompletableFuture<Void> deleteAsync(String externalId, RequestOptions options) {
    return http.<Object>deleteAsync(userPath(externalId), options).thenApply(ignored -> null);
  }

  /** Blocking {@link #deleteAsync}. */
  public void delete(String externalId, RequestOptions options) {
    Futures.join(deleteAsync(externalId, options));
  }

  /** Delete with default request options. */
  public void delete(String externalId) {
    delete(externalId, RequestOptions.EMPTY);
  }

  /** List users (page-based, supports {@code status} and {@code page} filters). */
  public CompletableFuture<Paginated<AppUser>> listAsync(
      @Nullable String status, @Nullable Integer page, RequestOptions options) {
    HttpRequest request =
        HttpRequest.builder("GET", base())
            .query("status", status)
            .query("page", page)
            .options(options)
            .build();
    return http.<Object>requestAsync(request)
        .thenApply(body -> Pagination.normalizePaginated(body, AppUser.class));
  }

  /** Blocking {@link #listAsync}. */
  public Paginated<AppUser> list(
      @Nullable String status, @Nullable Integer page, RequestOptions options) {
    return Futures.join(listAsync(status, page, options));
  }

  /** List users without filters. */
  public Paginated<AppUser> list(RequestOptions options) {
    return list(null, null, options);
  }

  /** List users with default request options. */
  public Paginated<AppUser> list() {
    return list(null, null, RequestOptions.EMPTY);
  }

  /** Async {@link #list} with default request options. */
  public CompletableFuture<Paginated<AppUser>> listAsync() {
    return listAsync(null, null, RequestOptions.EMPTY);
  }

  /**
   * Iterate every user across all pages (auto-follows {@code page}). Prefer over {@link #list} when
   * you need the full set without manual page bookkeeping. Blocking.
   */
  public Iterator<AppUser> iterate(@Nullable String status, RequestOptions options) {
    Paginated<AppUser> first = list(status, null, options);
    return Pagination.pageIterator(first, page -> list(status, page, options));
  }

  /** Collect every user across all pages (blocking convenience). */
  public List<AppUser> iterateAll(@Nullable String status, RequestOptions options) {
    List<AppUser> all = new ArrayList<>();
    Iterator<AppUser> iterator = iterate(status, options);
    while (iterator.hasNext()) {
      all.add(iterator.next());
    }
    return all;
  }

  /** Scoped connections client for a specific user. */
  public ConnectionsClient connections(String externalId) {
    return new ConnectionsClient(http, appId, externalId);
  }
}
