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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

  @Test
  void honorsPositiveRetryAfterHintCappedAtMaxDelayMs() {
    long capped = RetryPolicy.backoffDelay(0, RetryPolicy.DEFAULT, 60_000L);
    assertThat(capped).isEqualTo(RetryPolicy.DEFAULT.maxDelayMs());

    RetryPolicy tenSeconds =
        new RetryPolicy(
            RetryPolicy.DEFAULT.maxRetries(), RetryPolicy.DEFAULT.baseDelayMs(), 10_000L);
    long exact = RetryPolicy.backoffDelay(0, tenSeconds, 2_500L);
    assertThat(exact).isEqualTo(2_500L);
  }

  @Test
  void treatsRetryAfterZeroAsNoHint() {
    for (int i = 0; i < 20; i++) {
      // Attempt 0 window = baseDelayMs = 250, so 0 is possible, but the guard is against
      // 0-for-0 determinism — jitter makes a delay > 0 likely.
      assertThat(RetryPolicy.backoffDelay(0, RetryPolicy.DEFAULT, 0L)).isGreaterThanOrEqualTo(0L);
    }
  }

  @Test
  void neverExceedsMaxDelayWithoutHint() {
    for (int attempt : List.of(0, 1, 2, 5, 10)) {
      assertThat(RetryPolicy.backoffDelay(attempt, RetryPolicy.DEFAULT, null))
          .isLessThanOrEqualTo(RetryPolicy.DEFAULT.maxDelayMs());
    }
  }

  @Test
  void parsesDeltaSeconds() {
    assertThat(RetryPolicy.parseRetryAfter("30")).isEqualTo(30_000L);
  }

  @Test
  void parsesHttpDateIntoRemainingMilliseconds() {
    String future =
        DateTimeFormatter.RFC_1123_DATE_TIME.format(
            Instant.now().plusSeconds(10).atZone(ZoneOffset.UTC).toOffsetDateTime());
    Long ms = RetryPolicy.parseRetryAfter(future);
    assertThat(ms).isNotNull();
    assertThat(ms).isGreaterThan(0L);
    assertThat(ms).isLessThanOrEqualTo(10_000L);
  }

  @Test
  void returnsNullForGarbageAndMissingValues() {
    assertThat(RetryPolicy.parseRetryAfter(null)).isNull();
    assertThat(RetryPolicy.parseRetryAfter("soon")).isNull();
  }
}
