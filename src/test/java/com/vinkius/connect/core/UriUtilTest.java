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

import org.junit.jupiter.api.Test;

class UriUtilTest {

  @Test
  void keepsEveryUnreservedCharacterVerbatim() {
    String input = "ABCXYZabcxyz0123456789-_.!~*'()";
    assertThat(UriUtil.encodeSegment(input)).isEqualTo(input);
  }

  @Test
  void percentEncodesReservedCharactersWithUppercaseHex() {
    assertThat(UriUtil.encodeSegment("a b/c?#&=%")).isEqualTo("a%20b%2Fc%3F%23%26%3D%25");
  }

  @Test
  void percentEncodesMultiByteUtf8Sequences() {
    assertThat(UriUtil.encodeSegment("\u00e9")).isEqualTo("%C3%A9");
    assertThat(UriUtil.encodeSegment("\u6f22\u5b57")).isEqualTo("%E6%BC%A2%E5%AD%97");
  }

  @Test
  void emptySegmentEncodesToEmptyString() {
    assertThat(UriUtil.encodeSegment("")).isEmpty();
  }
}
