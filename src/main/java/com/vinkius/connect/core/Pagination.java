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

import com.vinkius.connect.error.ProtocolException;
import com.vinkius.connect.model.PageLinks;
import com.vinkius.connect.model.PageMeta;
import com.vinkius.connect.model.Paginated;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Pagination normalization.
 *
 * <p>The API is heterogeneous: some list endpoints return the full Laravel length-aware envelope
 * ({@code { data, links, meta }}), others return only {@code { data: [...] }}. This utility coerces
 * both into {@link Paginated} and exposes the raw array when a plain list is expected.
 */
public final class Pagination {

  private Pagination() {}

  private static final String LIST_ERROR =
      "Expected a list response shaped as an array or { data: [...] }.";

  private static @Nullable List<?> rawData(@Nullable Object body) {
    if (body instanceof List<?> list) {
      return list;
    }
    Map<String, ?> record = Json.asMap(body);
    if (record == null) {
      return null;
    }
    return record.get("data") instanceof List<?> data ? data : null;
  }

  /** Normalize a valid list response into a {@link Paginated} value. */
  public static <T> Paginated<T> normalizePaginated(@Nullable Object body, Class<T> type) {
    if (body instanceof List<?> list) {
      return new Paginated<>(Json.convertList(list, type), null, null);
    }
    Map<String, ?> record = Json.asMap(body);
    List<?> data = rawData(body);
    if (record == null || data == null) {
      throw new ProtocolException(LIST_ERROR, body);
    }
    PageMeta meta = readMeta(record.get("meta"));
    PageLinks links = readLinks(record.get("links"));
    return new Paginated<>(Json.convertList(data, type), meta, links);
  }

  /** Unwrap a {@code { data: [...] }} (or bare array) response into a plain list. */
  public static <T> List<T> unwrapList(@Nullable Object body, Class<T> type) {
    if (body instanceof List<?> list) {
      return Json.convertList(list, type);
    }
    List<?> data = rawData(body);
    if (data == null) {
      throw new ProtocolException(LIST_ERROR, body);
    }
    return Json.convertList(data, type);
  }

  /** Unwrap a single-resource {@code { data: {...} }} response. */
  public static <T> T unwrapItem(@Nullable Object body, Class<T> type) {
    if (Json.isObject(body)) {
      Map<String, ?> record = Json.asMap(body);
      if (record != null && record.containsKey("data") && record.get("data") != null) {
        return Json.convert(record.get("data"), type);
      }
    }
    return Json.convert(body, type);
  }

  /**
   * Walk every page of a page-based list, yielding each item in order. Starts from {@code first}
   * (already fetched), then advances {@code meta.currentPage} until {@code meta.lastPage} is
   * reached. A single-page response (no {@code meta}) yields exactly once. Blocking: {@code
   * fetchPage} must return a completed page.
   */
  public static <T> Iterator<T> pageIterator(
      Paginated<T> first, Function<Integer, Paginated<T>> fetchPage) {
    return new PageIterator<>(first, fetchPage);
  }

  private static final class PageIterator<T> implements Iterator<T> {
    private Paginated<T> current;
    private int page;
    private int index;
    private List<T> items;
    private final Function<Integer, Paginated<T>> fetchPage;

    PageIterator(Paginated<T> first, Function<Integer, Paginated<T>> fetchPage) {
      this.current = first;
      this.page = first.meta() != null ? first.meta().currentPage() : 1;
      this.items = first.data();
      this.index = 0;
      this.fetchPage = fetchPage;
    }

    @Override
    public boolean hasNext() {
      advance();
      return index < items.size();
    }

    @Override
    public T next() {
      advance();
      if (index >= items.size()) {
        throw new NoSuchElementException();
      }
      return items.get(index++);
    }

    private void advance() {
      while (index >= items.size()) {
        int lastPage = current.meta() != null ? current.meta().lastPage() : page;
        if (page >= lastPage) {
          return;
        }
        current = fetchPage.apply(page + 1);
        page += 1;
        items = current.data();
        index = 0;
      }
    }
  }

  private static @Nullable PageMeta readMeta(@Nullable Object value) {
    Map<String, ?> meta = Json.asMap(value);
    if (meta == null) {
      return null;
    }
    String path = stringValue(meta.get("path"));
    return new PageMeta(
        intValue(meta.get("current_page")),
        integerValue(meta.get("from")),
        intValue(meta.get("last_page")),
        path == null ? "" : path,
        intValue(meta.get("per_page")),
        integerValue(meta.get("to")),
        intValue(meta.get("total")));
  }

  private static @Nullable PageLinks readLinks(@Nullable Object value) {
    Map<String, ?> links = Json.asMap(value);
    if (links == null) {
      return null;
    }
    return new PageLinks(
        stringValue(links.get("first")),
        stringValue(links.get("last")),
        stringValue(links.get("prev")),
        stringValue(links.get("next")));
  }

  private static int intValue(@Nullable Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }

  private static @Nullable Integer integerValue(@Nullable Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }

  private static @Nullable String stringValue(@Nullable Object value) {
    return value instanceof String s ? s : null;
  }
}
