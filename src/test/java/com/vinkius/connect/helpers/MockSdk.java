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
package com.vinkius.connect.helpers;

import com.vinkius.connect.fluent.Vinkius;
import com.vinkius.connect.fluent.VinkiusOptions;
import java.util.function.UnaryOperator;

/**
 * Build a {@link Vinkius} client wired to a {@link FakeHttpSender}. Retries off by default (parity
 * with the JS {@code makeVinkius} helper).
 */
public final class MockSdk {

  /** Test application id. */
  public static final String APP_ID = "vk_app_test";

  /** Test secret key. */
  public static final String API_KEY = "vk_app_sk_test";

  /** Local base URL so the mock's full-URL assertions hold (parity with VINKIUS_API_BASE). */
  public static final String BASE_URL = "http://localhost:8080";

  private MockSdk() {}

  /** A client wired to the sender, with default test options (retries off). */
  public static Vinkius make(FakeHttpSender sender) {
    return make(sender, options -> options);
  }

  /** A client wired to the sender with per-test option overrides. */
  public static Vinkius make(
      FakeHttpSender sender, UnaryOperator<VinkiusOptions.Builder> overrides) {
    VinkiusOptions.Builder builder =
        VinkiusOptions.builder(APP_ID, API_KEY).maxRetries(0).sender(sender).baseUrl(BASE_URL);
    return new Vinkius(overrides.apply(builder).build());
  }
}
