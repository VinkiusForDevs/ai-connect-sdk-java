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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RedactorTest {

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castBody(@Nullable Object value) {
    java.util.Objects.requireNonNull(value);
    return (Map<String, Object>) value;
  }

  @Test
  void redactsAuthorizationAndCookieHeadersCaseInsensitively() {
    Map<String, String> out =
        Redactor.redactHeaders(
            Map.of(
                "Authorization", "Bearer vk_app_sk_xxx",
                "content-type", "application/json",
                "Cookie", "session=abc",
                "Idempotency-Key", "idem-1234"));
    assertThat(out.get("Authorization")).isEqualTo("[REDACTED]");
    assertThat(out.get("Cookie")).isEqualTo("[REDACTED]");
    assertThat(out.get("Idempotency-Key")).isEqualTo("[REDACTED]");
    assertThat(out.get("content-type")).isEqualTo("application/json");
  }

  @Test
  void passesThroughNonSecretHeadersUnchanged() {
    Map<String, String> out =
        Redactor.redactHeaders(Map.of("x-request-id", "abc", "accept", "*/*"));
    assertThat(out)
        .containsExactlyInAnyOrderEntriesOf(Map.of("x-request-id", "abc", "accept", "*/*"));
  }

  @Test
  void redactsKnownSecretKeysAtAnyDepth() {
    Map<String, Object> nestedConfig = new LinkedHashMap<>();
    nestedConfig.put("api_key", "sk-xxx");
    nestedConfig.put("safe", "ok");
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("config", nestedConfig);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("user", "alice");
    body.put("credentials", Map.of("GITHUB_TOKEN", "ghp_xxx"));
    body.put("nested", nested);

    @SuppressWarnings("unchecked")
    Map<String, Object> out = castBody(Redactor.redactBody(body));

    assertThat(out.get("credentials")).isEqualTo("[REDACTED]");
    assertThat(out.get("nested"))
        .isEqualTo(Map.of("config", Map.of("api_key", "[REDACTED]", "safe", "ok")));
    assertThat(out.get("user")).isEqualTo("alice");
  }

  @Test
  void handlesArraysPrimitivesAndNull() {
    assertThat(Redactor.redactBody(null)).isNull();
    assertThat(Redactor.redactBody(42)).isEqualTo(42);
    assertThat(Redactor.redactBody("hello")).isEqualTo("hello");
    assertThat(Redactor.redactBody(List.of(1, Map.of("token", "x"))))
        .isEqualTo(List.of(1, Map.of("token", "[REDACTED]")));
  }

  @Test
  void detectsCircularReferences() {
    Map<String, Object> obj = new LinkedHashMap<>();
    obj.put("name", "a");
    obj.put("self", obj);

    @SuppressWarnings("unchecked")
    Map<String, Object> out = castBody(Redactor.redactBody(obj));

    assertThat(out.get("self")).isEqualTo("[CIRCULAR]");
  }

  @Test
  void clonesSharedNonCyclicReferencesInsteadOfFlaggingCircular() {
    Map<String, Object> shared = new LinkedHashMap<>();
    shared.put("safe", "ok");
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("a", shared);
    body.put("b", shared);

    @SuppressWarnings("unchecked")
    Map<String, Object> out = castBody(Redactor.redactBody(body));

    assertThat(out.get("a")).isEqualTo(Map.of("safe", "ok"));
    assertThat(out.get("b")).isEqualTo(Map.of("safe", "ok"));
  }

  @Test
  void truncatesAtMaxDepth() {
    Map<String, Object> deep = new LinkedHashMap<>();
    deep.put("leaf", true);
    for (int i = 0; i < 10; i++) {
      Map<String, Object> wrapper = new LinkedHashMap<>();
      wrapper.put("child", deep);
      deep = wrapper;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> out = castBody(Redactor.redactBody(deep));

    Object cursor = out;
    boolean found = false;
    for (int i = 0; i < 12; i++) {
      if ("[TRUNCATED]".equals(cursor)) {
        found = true;
        break;
      }
      if (cursor instanceof Map<?, ?> map) {
        Object next = map.get("child");
        if (next == null) {
          next = map.get("leaf");
        }
        if (next == null) {
          break;
        }
        cursor = next;
      } else {
        break;
      }
    }
    assertThat(found).isTrue();
  }
}
