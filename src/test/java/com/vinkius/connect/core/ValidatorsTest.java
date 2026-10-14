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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vinkius.connect.error.ConfigException;
import org.junit.jupiter.api.Test;

class ValidatorsTest {

  @Test
  void acceptsSimpleAlphanumericId() {
    assertThatCode(() -> Validators.assertExternalId("alice_123")).doesNotThrowAnyException();
  }

  @Test
  void acceptsUuid() {
    assertThatCode(() -> Validators.assertExternalId("550e8400-e29b-41d4-a716-446655440000"))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsSpecialUrlSafeCharacters() {
    assertThatCode(() -> Validators.assertExternalId("user:alice@org")).doesNotThrowAnyException();
  }

  @Test
  void rejectsEmptyString() {
    assertThatThrownBy(() -> Validators.assertExternalId("")).isInstanceOf(ConfigException.class);
  }

  @Test
  void rejectsWhitespace() {
    assertThatThrownBy(() -> Validators.assertExternalId("alice 123"))
        .isInstanceOf(ConfigException.class);
    assertThatThrownBy(() -> Validators.assertExternalId("alice\t123"))
        .isInstanceOf(ConfigException.class);
    assertThatThrownBy(() -> Validators.assertExternalId("alice\n123"))
        .isInstanceOf(ConfigException.class);
  }

  @Test
  void rejectsForwardSlash() {
    assertThatThrownBy(() -> Validators.assertExternalId("alice/123"))
        .isInstanceOf(ConfigException.class);
  }

  @Test
  void rejectsBackslash() {
    assertThatThrownBy(() -> Validators.assertExternalId("alice\\123"))
        .isInstanceOf(ConfigException.class);
  }

  @Test
  void rejectsStringsLongerThan255Characters() {
    assertThatThrownBy(() -> Validators.assertExternalId("a".repeat(256)))
        .isInstanceOf(ConfigException.class);
  }

  @Test
  void acceptsExactly255Characters() {
    assertThatCode(() -> Validators.assertExternalId("a".repeat(255))).doesNotThrowAnyException();
  }
}
