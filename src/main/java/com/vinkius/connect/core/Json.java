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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Jackson plumbing shared by the whole SDK.
 *
 * <p>The control-plane wire is snake_case with optional fields omitted ({@code undefined} in JS),
 * so a single mapper configures both conventions. Deserialization ignores unknown fields to stay
 * forward-compatible with additive API changes (JS objects silently keep extra keys).
 */
public final class Json {

  /** Mapper for control-plane requests/responses (snake_case, non-null inclusion). */
  public static final ObjectMapper MAPPER = createMapper();

  private Json() {}

  private static ObjectMapper createMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    return mapper;
  }

  /**
   * Parse a raw response body: empty text becomes {@code null}; valid JSON becomes the mapped value
   * (Map/List/scalar); anything else is returned as the raw String — mirroring the JS {@code
   * JSON.parse} fallback.
   */
  public static @Nullable Object parse(String text) {
    if (text.isEmpty()) {
      return null;
    }
    try {
      return MAPPER.readValue(text, Object.class);
    } catch (IOException | RuntimeException e) {
      return text;
    }
  }

  /** Serialize a value to JSON (returns "{}" for null, like {@code JSON.stringify(undefined)}). */
  public static String write(@Nullable Object value) {
    if (value == null) {
      return "{}";
    }
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize request body", e);
    }
  }

  /** Convert a parsed wire value into a model type. */
  public static <T> T convert(@Nullable Object value, Class<T> type) {
    return MAPPER.convertValue(value, type);
  }

  /** Convert a parsed wire list into a list of model values. */
  public static <T> List<T> convertList(@Nullable List<?> values, Class<T> type) {
    if (values == null) {
      return List.of();
    }
    List<T> result = new ArrayList<>(values.size());
    for (Object value : values) {
      result.add(MAPPER.convertValue(value, type));
    }
    return result;
  }

  /** Read a JSON string into a tree node (returns {@code null} for invalid JSON). */
  public static @Nullable JsonNode readTree(String text) {
    try {
      return MAPPER.readTree(text);
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  /** Whether a parsed wire value is a JSON object. */
  public static boolean isObject(@Nullable Object value) {
    return value instanceof Map<?, ?>;
  }

  /** Cast a parsed wire value to a record map (returns {@code null} otherwise). */
  public static @Nullable Map<String, ?> asMap(@Nullable Object value) {
    if (value instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, ?> asStringMap = (Map<String, ?>) map;
      return asStringMap;
    }
    return null;
  }
}
