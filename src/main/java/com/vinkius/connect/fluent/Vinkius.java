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

import com.vinkius.connect.Version;
import com.vinkius.connect.core.HttpClient;
import com.vinkius.connect.core.HttpSender;
import com.vinkius.connect.core.JdkHttpSender;
import com.vinkius.connect.core.ResolverCache;
import com.vinkius.connect.core.RetryPolicy;
import com.vinkius.connect.core.RuntimeClient;
import com.vinkius.connect.error.ConfigException;
import com.vinkius.connect.resources.AppUsersClient;
import com.vinkius.connect.resources.CatalogClient;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

/**
 * Vinkius — the SDK root, scoped to a single application.
 *
 * <p>Holds the only secret the integrator manages ({@code vk_app_sk_*}) and the public app id
 * ({@code vk_app_*}). Validates configuration eagerly and fails fast with a clear {@link
 * ConfigException}.
 */
public final class Vinkius {

  private static final String DEFAULT_BASE_URL = "https://api.vinkius.com";
  private static final long DEFAULT_TIMEOUT_MS = 30_000;

  private final SdkContext ctx;
  private final CatalogClient catalog;
  private final AppUsersClient users;

  /** Create the SDK root for one application. */
  public Vinkius(VinkiusOptions options) {
    String appId = options.appId();
    String apiKey = options.apiKey();

    if (appId == null || !appId.startsWith("vk_app_") || appId.startsWith("vk_app_sk_")) {
      throw new ConfigException(
          "`appId` must be a public application id starting with \"vk_app_\" (not the secret key).");
    }
    if (apiKey == null || !apiKey.startsWith("vk_app_sk_")) {
      throw new ConfigException(
          "`apiKey` must be a secret application key starting with \"vk_app_sk_\".");
    }

    String baseUrl = normalizeBaseUrl(resolveBaseUrl(options));
    warnIfInsecure(baseUrl);

    long timeoutMs = options.timeoutMs() != null ? options.timeoutMs() : DEFAULT_TIMEOUT_MS;
    RetryPolicy defaults = RetryPolicy.DEFAULT;
    RetryPolicy retry =
        new RetryPolicy(
            options.maxRetries() != null ? options.maxRetries() : defaults.maxRetries(),
            defaults.baseDelayMs(),
            defaults.maxDelayMs());
    HttpSender sender = options.sender() != null ? options.sender() : new JdkHttpSender();
    String userAgent = buildUserAgent(options.userAgent());
    BiFunction<String, String, String> namespace = options.namespaceCapability();

    HttpClient http =
        new HttpClient(
            new HttpClient.Config(
                baseUrl, apiKey, appId, timeoutMs, retry, sender, userAgent, options.hooks()));

    // The runtime is the sole execution surface; its config mirrors the API client's transport
    // settings but carries no API auth (the mcp_url embeds the vk_live_* token). Hooks apply to
    // the data plane too — redacted.
    this.ctx =
        SdkContext.of(
            http,
            appId,
            new ResolverCache(),
            namespace,
            mcpUrl ->
                new RuntimeClient(
                    mcpUrl,
                    new RuntimeClient.Config(
                        timeoutMs, retry, sender, userAgent, options.hooks())));

    this.catalog = new CatalogClient(http);
    this.users = new AppUsersClient(http, appId);
  }

  /** Catalog discovery. */
  public CatalogClient catalog() {
    return catalog;
  }

  /** Low-level users client, scoped to this application. */
  public AppUsersClient users() {
    return users;
  }

  /** A lazy, zero-request handle to one end-user's capabilities. */
  public UserContext user(String externalId) {
    if (externalId == null || externalId.isEmpty()) {
      throw new ConfigException("`externalId` must be a non-empty string.");
    }
    if (externalId.startsWith("vk_app_user_")) {
      throw new ConfigException(
          "`externalId` must be YOUR user id (e.g. \"alice_123\"), not a Vinkius internal id. "
              + "The SDK resolves identifiers automatically from the external_id you provide.");
    }
    return new UserContext(ctx, externalId);
  }

  /** The shared runtime context (for extensions and the fluent layer). */
  public SdkContext context() {
    return ctx;
  }

  private static @Nullable String env(String name) {
    String value = System.getenv(name);
    return value == null || value.trim().isEmpty() ? null : value;
  }

  private static String resolveBaseUrl(VinkiusOptions options) {
    // The Vinkius Cloud endpoint is fixed; only a sandbox/staging override comes from the
    // VINKIUS_API_BASE environment variable (or explicit options), never from user code.
    if (options.baseUrl() != null && !options.baseUrl().trim().isEmpty()) {
      return options.baseUrl();
    }
    String override = env("VINKIUS_API_BASE");
    return override != null ? override : DEFAULT_BASE_URL;
  }

  private static String normalizeBaseUrl(String url) {
    try {
      URI parsed = new URI(url);
      if (parsed.getScheme() == null || parsed.getHost() == null) {
        throw new URISyntaxException(url, "not an absolute URL");
      }
      String normalized = parsed.toString();
      int end = normalized.length();
      while (end > 0 && normalized.charAt(end - 1) == '/') {
        end--;
      }
      return normalized.substring(0, end);
    } catch (URISyntaxException e) {
      throw new ConfigException("Invalid baseUrl: " + url, e);
    }
  }

  private static void warnIfInsecure(String baseUrl) {
    try {
      URI parsed = new URI(baseUrl);
      String host = parsed.getHost();
      boolean isLocal =
          "localhost".equals(host)
              || "127.0.0.1".equals(host)
              || "[::1]".equals(host)
              || (host != null && host.endsWith(".localhost"));
      if ("http".equalsIgnoreCase(parsed.getScheme()) && !isLocal) {
        System.err.println(
            "[vinkius] baseUrl uses insecure http:// on a non-local host; use https:// in production.");
      }
    } catch (URISyntaxException e) {
      /* already validated */
    }
  }

  private static String buildUserAgent(@Nullable String extra) {
    String runtime = " java/" + Runtime.version().feature();
    String base = "vinkius-connect/" + Version.SDK_VERSION + runtime;
    return extra != null && !extra.isEmpty() ? base + " " + extra : base;
  }
}
