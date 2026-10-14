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
package com.vinkius.connect.fluent;

import com.vinkius.connect.core.HttpSender;
import com.vinkius.connect.model.Hooks;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

/**
 * Options accepted by the {@link Vinkius} constructor.
 *
 * @param appId public application id — the {@code vk_app_*} identifier (route key)
 * @param apiKey secret application key — the {@code vk_app_sk_*} credential (Bearer auth)
 * @param timeoutMs per-request timeout in milliseconds (default 30000)
 * @param maxRetries max automatic retries for idempotent requests (default 2)
 * @param sender HTTP transport SPI — the Java counterpart of the JS injectable {@code fetch} (for
 *     tests / non-standard runtimes); defaults to the JDK client
 * @param userAgent extra token appended to the default User-Agent
 * @param hooks observability hooks (redacted)
 * @param namespaceCapability controls how a capability's display name is namespaced across
 *     connectors (default: {@code connector + "__" + name})
 * @param baseUrl control-plane base URL; defaults to the {@code VINKIUS_API_BASE} environment
 *     variable when set, otherwise {@code https://api.vinkius.com}
 */
public record VinkiusOptions(
    String appId,
    String apiKey,
    @Nullable Long timeoutMs,
    @Nullable Integer maxRetries,
    @Nullable HttpSender sender,
    @Nullable String userAgent,
    @Nullable Hooks hooks,
    @Nullable BiFunction<String, String, String> namespaceCapability,
    @Nullable String baseUrl) {

  /** Create options for one application. */
  public static Builder builder(String appId, String apiKey) {
    return new Builder(appId, apiKey);
  }

  /** Fluent builder for {@link VinkiusOptions}. */
  public static final class Builder {
    private final String appId;
    private final String apiKey;
    private @Nullable Long timeoutMs;
    private @Nullable Integer maxRetries;
    private @Nullable HttpSender sender;
    private @Nullable String userAgent;
    private @Nullable Hooks hooks;
    private @Nullable BiFunction<String, String, String> namespaceCapability;
    private @Nullable String baseUrl;

    private Builder(String appId, String apiKey) {
      this.appId = appId;
      this.apiKey = apiKey;
    }

    public Builder timeoutMs(long timeoutMs) {
      this.timeoutMs = timeoutMs;
      return this;
    }

    public Builder maxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
      return this;
    }

    public Builder sender(HttpSender sender) {
      this.sender = sender;
      return this;
    }

    public Builder userAgent(String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    public Builder hooks(Hooks hooks) {
      this.hooks = hooks;
      return this;
    }

    public Builder namespaceCapability(BiFunction<String, String, String> namespaceCapability) {
      this.namespaceCapability = namespaceCapability;
      return this;
    }

    public Builder baseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    public VinkiusOptions build() {
      return new VinkiusOptions(
          appId,
          apiKey,
          timeoutMs,
          maxRetries,
          sender,
          userAgent,
          hooks,
          namespaceCapability,
          baseUrl);
    }
  }
}
