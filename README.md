<div align="center">

# Vinkius Connect for Java

### Build AI agents that act for each user.

One SDK for user-specific tools, credentials, permissions, and execution across **11,000+ systems**.

[![Connectors](https://api.vinkius.com/badges/connectors.svg)](https://vinkius.com)
[![AI Capabilities](https://api.vinkius.com/badges/capabilities.svg)](https://vinkius.com)

[Documentation](https://vinkius.com/learn/en/connect-sdk) · [Vinkius Cloud](https://cloud.vinkius.com) · [Site](https://vinkius.com)

</div>

---

<img src="assets/Futuristic_Java_Robotics_Factory.webp" />

<br >

AI applications become more useful when they can take action in the systems their users already work with. But shipping those actions means dealing with integrations, OAuth, credential storage, permissions, API changes, and model-specific tool formats.

**Vinkius Connect handles that connectivity layer.** Your application keeps its users and customer experience. Vinkius resolves each user's connected systems and available capabilities, executes actions on their behalf, and returns the result.

<img src="assets/Connectors_Real_Actions.webp" />

```text
Your AI application
        │
        │  Your authenticated user's external_id
        ▼
Vinkius Connect
  identity · connections · credentials · capabilities · execution
        │
        ├── GitHub
        ├── Slack
        ├── Project management
        └── 11,000+ connected systems
```

## Why Vinkius Connect?

- **Built for multi-user AI products.** Each user has their own connections and available capabilities.
- **One consistent interface.** Discover and execute actions without implementing each integration yourself.
- **Credentials stay out of model context.** Connector secrets are write-only through the documented API and are not returned to application code.
- **Framework flexibility.** Convert capabilities to a neutral JSON Schema format that maps directly to OpenAI, Anthropic, Gemini, LangChain, LlamaIndex, OpenAI Agents, CrewAI, or any other tool-calling format.
- **Managed connectivity.** Vinkius maintains connectors and their evolving APIs.
- **Your product stays yours.** Use your application's own user IDs and keep the customer relationship and user experience in your product.

## How it works

1. **Identify the user.** Pass the stable `external_id` assigned by your application backend.
2. **Discover capabilities.** Vinkius resolves the user's ready connectors and the actions they expose.
3. **Provide tools to the model.** Convert those capabilities into the tool format expected by your model or framework.
4. **Execute the selected action.** Send the tool call to Vinkius and return the result to your agent loop.

The `external_id` is an identifier, **not an authentication mechanism**. Only pass an ID that your backend has associated with the currently authenticated user. Never trust a user ID supplied directly by an untrusted client.

<img src="assets/Invisible_Infrastructure_Secure_Connections.webp" />

## Installation

The SDK is published to Maven Central as `com.vinkius:connect-sdk` and requires **Java 17+**.
Jackson Databind is a runtime dependency; JSpecify is part of the public API surface.

### Maven

```xml
<dependency>
  <groupId>com.vinkius</groupId>
  <artifactId>connect-sdk</artifactId>
  <version>0.1.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.vinkius:connect-sdk:0.1.0")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'com.vinkius:connect-sdk:0.1.0'
}
```

If the package is not yet available on Maven Central, install the current repository version into your local Maven repository:

```sh
docker compose run --rm sdk publishToMavenLocal --no-daemon
```

## Quick start

Create an AI application in [Vinkius Cloud](https://cloud.vinkius.com), then create an application key. Keep the key on your server. **Never expose it in browser code, mobile clients, or model prompts.**

Set the environment variables:

```bash
export VINKIUS_APP_ID="vk_app_..."
export VINKIUS_APP_KEY="vk_app_sk_..."
```

Then discover and execute a capability:

```java
import com.vinkius.connect.fluent.Capability;
import com.vinkius.connect.fluent.CapabilitySet;
import com.vinkius.connect.fluent.Vinkius;
import com.vinkius.connect.fluent.VinkiusOptions;
import com.vinkius.connect.model.CapabilityResult;
import java.util.Map;

public class Quickstart {
  public static void main(String[] args) {
    Vinkius vinkius =
        new Vinkius(
            VinkiusOptions.builder(
                    System.getenv("VINKIUS_APP_ID"), System.getenv("VINKIUS_APP_KEY"))
                .build());

    // Use the ID of the authenticated user from your own backend.
    CapabilitySet capabilities = vinkius.user("alice_123").capabilities();

    // Execute one capability by its namespaced name.
    Capability capability = capabilities.findCapability("github__create_issue");
    if (capability != null) {
      CapabilityResult result =
          capability.execute(
              Map.of("owner", "acme", "repo", "product", "title", "Ship it"));
      result.content().forEach(item -> System.out.println(item.text()));
    }
  }
}
```

The capability name and input schema depend on the connector and capabilities available to that user. Discover available capabilities before attempting to execute one.

Every blocking method has an async twin returning `CompletableFuture` (`connectAsync`, `capabilitiesAsync`, `executeAsync`, ...) for structured concurrency.

## Use capabilities with an AI model

The usual agent loop is straightforward:

1. Fetch the authenticated user's capabilities.
2. Convert them into the model's tool format.
3. Send the user's request and tools to the model.
4. Execute the returned tool call through Vinkius.
5. Return the result to the model as part of your agent loop.

### Example: framework-neutral tool loop

Any tool-calling API can consume the neutral JSON Schema format. Convert the capabilities, hand the tools to your model or framework, execute the returned call by name, and return the result:

```java
import com.vinkius.connect.adapters.JsonSchemaAdapter;
import com.vinkius.connect.fluent.CapabilitySet;
import com.vinkius.connect.model.CapabilityResult;
import java.util.List;
import java.util.Map;

// 1. Discover the authenticated user's capabilities.
CapabilitySet caps = vinkius.user("alice_123").capabilities();

// 2. Convert them into the neutral tool format.
List<JsonSchemaAdapter.JSONSchemaTool> tools =
    JsonSchemaAdapter.toJSONSchemaTools(caps.asList());

// 3. Send `tools` with the user's request to your model / agent framework.

// 4. Execute the returned tool call through Vinkius.
CapabilityResult result =
    JsonSchemaAdapter.executeByName(
        caps.asList(),
        "github__create_issue",
        Map.of("owner", "acme", "repo", "product", "title", "Ship it"));

// 5. Return the result content to the model as part of your agent loop.
if (!result.isError()) {
  result.content().forEach(item -> System.out.println(item.text()));
}
```

Framework-specific tool formats (OpenAI, Anthropic, Gemini, LangChain, LlamaIndex, OpenAI Agents, CrewAI, ...) are trivial to derive from the neutral JSON Schema tools; the TypeScript and Python SDKs ship those converters directly.

## Connect a user's system

Provision a connector for a user, set its credentials, check its status, and inspect its capabilities:

```java
import com.vinkius.connect.fluent.CapabilitySet;
import com.vinkius.connect.fluent.Connector;
import com.vinkius.connect.fluent.UserContext;
import com.vinkius.connect.model.ConnectorStatus;
import java.util.Map;

UserContext user = vinkius.user("alice_123");
Connector github = user.connector("github");

github.connect();  // Get or create the connection
github.credentials().set(Map.of("GITHUB_TOKEN", token));

ConnectorStatus status = github.status();
System.out.println(status);  // NOT_CONNECTED, NEEDS_CREDENTIALS, READY, or DISABLED

CapabilitySet capabilities = github.capabilities();
```

The example assumes `vinkius` is already configured and `token` was obtained securely. In a production application, prefer the supported authorization flow for the connector where available, and never ask users to paste secrets into an AI conversation.

Credential values are write-only through the documented interface. The credential schema and whether a value is set can be inspected; secret values cannot be read back.

## Framework adapters

A capability set can be converted to different tool formats. Adapter modules shape the tools; they do not require the target framework merely to perform the conversion.

| Framework / format | Adapter | Main functions |
| --- | --- | --- |
| Framework-neutral (OpenAI, Anthropic, Gemini, LangChain, ...) | `JsonSchemaAdapter` | `toJSONSchemaTools`, `executeByName` |

Example:

```java
import com.vinkius.connect.adapters.JsonSchemaAdapter;
import java.util.List;

CapabilitySet caps = vinkius.user("alice_123").capabilities();

List<JsonSchemaAdapter.JSONSchemaTool> jsonSchemaTools =
    JsonSchemaAdapter.toJSONSchemaTools(caps.asList());
```

## Core concepts

| Concept | Meaning |
| --- | --- |
| **Connector** | An external system a user connects, such as GitHub or Slack. |
| **Capability** | An executable action exposed by a connector, such as listing repositories or creating an issue. |
| **User** | A user identified by your application's `external_id`. Vinkius does not require a separate end-user identity. |
| **Control plane** | Provisioning and state management for users, connectors, credentials, and catalog discovery. |
| **Execution plane** | Runtime capability discovery and execution, per user through a short-lived token. |

### Capability discovery behavior

`user.capabilities()` resolves the user's ready connectors in parallel, with concurrency capped at eight. A single unresponsive connector does not necessarily fail the whole batch. Use `onConnectorError` (via `CapabilityQuery.builder()`) to observe individual connector failures. If all connectors fail, the first error is raised.

## Configuration

```java
import com.vinkius.connect.fluent.Vinkius;
import com.vinkius.connect.fluent.VinkiusOptions;

Vinkius vinkius =
    new Vinkius(
        VinkiusOptions.builder("vk_app_...", "vk_app_sk_...")
            .timeoutMs(30_000)       // default
            .maxRetries(2)           // default; retry-safe requests only
            .sender(null)            // optionally inject an HttpSender (tests / custom transports)
            .userAgent("acme-ai/1.0")
            .namespaceCapability((connector, name) -> connector + "__" + name)
            .hooks(null)             // observability hooks; sensitive data redacted
            .build());
```

- **`appId`** identifies your Vinkius application.
- **`apiKey`** is a server-side secret. Keep it out of clients and prompts.
- **`timeoutMs`** sets the request timeout.
- **`maxRetries`** controls retries for retry-safe requests.
- **`sender`** lets you inject an `HttpSender` implementation, useful for tests and custom transports; defaults to the dependency-free JDK client.
- **`userAgent`** appends your application's identifier to the SDK user agent.
- **`namespaceCapability`** controls how connector and capability names are combined.
- **`hooks`** configures observability hooks; sensitive headers and bodies are redacted before hooks run.
- **`baseUrl`** (or the `VINKIUS_API_BASE` environment variable) overrides the control-plane base URL for sandboxes; local `http://` hosts are allowed, non-local hosts print an insecure-transport warning.

Per-call overrides (timeout, cancellation, idempotency key) go through `RequestOptions` / `ExecuteOptions` on individual calls.

### Retries and idempotency

Transient HTTP statuses (`429`, `502`, `503`, `504`) and network errors are retried with full-jitter backoff only for retry-safe requests. Idempotent methods and requests carrying an `idempotencyKey` may be retried. Non-idempotent POST requests, such as minting a connection token, are not automatically retried.

For actions that create or modify data in external systems, design your agent loop to account for duplicate requests and uncertain outcomes. Use idempotency support where available and appropriate.

## Low-level API

The fluent API has REST equivalents when you need more direct control:

```java
Connection connection =
    vinkius.users().connections("alice_123").create(new CreateConnectionInput("github"));

vinkius.users()
    .connections("alice_123")
    .credentials(connection.id())
    .set(new SetCredentialsInput(Map.of("GITHUB_TOKEN", "..."), null));

vinkius.catalog().search("github");

Iterator<CatalogConnector> catalog = vinkius.catalog().iterate(null, RequestOptions.EMPTY);
while (catalog.hasNext()) {
  System.out.println(catalog.next());
}
```

## Error handling

SDK exceptions inherit from `VinkiusException` and expose a status, stable error code, and server `requestId` where available.

Common exception types include:

- `ConfigException`
- `AuthException`
- `NotFoundException`
- `ValidationException`
- `RateLimitException`
- `QuotaException`
- `OverageException`
- `ConnectorNotConnectedException`
- `ConnectionException`
- `ProtocolException`
- `NotImplementedException`

```java
import com.vinkius.connect.error.RateLimitException;
import com.vinkius.connect.error.VinkiusException;

try {
  CapabilitySet caps = vinkius.user("alice_123").capabilities();
} catch (RateLimitException e) {
  // Inspect retryAfterMs() when present.
  System.out.println(e.retryAfterMs());
} catch (VinkiusException e) {
  // Useful for logs and support; avoid logging secrets or sensitive payloads.
  System.out.println(e.code() + " " + e.status() + " " + e.requestId());
}
```

Capability-level execution failures are returned as results with `isError` set, allowing an agent loop to pass the failure back to the model and recover. Transport, configuration, and protocol failures are raised as exceptions. Runtime gate blocks (`vinkius_block` quota, overage, and rate-limit sentinels) surface as `QuotaException`, `OverageException`, or `RateLimitException`; read-only preview blocks stay in-band as `isError` results.

## Security practices

- **Keep application keys server-side.** Never ship `vk_app_sk_*` in frontend code, mobile applications, repositories, or prompts.
- **Authenticate users in your application.** Resolve `external_id` from trusted backend state; do not accept arbitrary user IDs from clients.
- **Scope actions to the right user.** Fetch and execute capabilities for the authenticated user's identity only.
- **Keep raw secrets out of prompts.** Connector credentials are write-only through the documented API and are not intended for model context.
- **Treat tool calls as actions.** Apply your product's confirmation, authorization, and policy requirements before executing sensitive or irreversible operations.
- **Protect logs.** Do not log API keys, tokens, credential values, or sensitive customer data.
- **Handle revocation and failures.** Make connection state visible in your product and define how your application responds when a connector is disabled, expired, or unavailable.

Vinkius Connect provides connectivity and execution primitives. Your application remains responsible for authenticating its users, enforcing product-level policy, and deciding when an AI-requested action requires confirmation.

## Development

The build runs exclusively inside Docker Compose — no JDK or Gradle is installed on the host:

```sh
docker compose run --rm sdk build --no-daemon   # compile + checkstyle + tests + javadoc + jacoco
docker compose run --rm sdk test --no-daemon    # tests only
docker compose run --rm sdk spotlessApply       # format in place
```

The tests use an injectable fake transport (`HttpSender` SPI) and run offline. Toolchain: Gradle 9.3.1, Eclipse Temurin 21 (compile with `--release 17`), `-Xlint:all -Werror`, ErrorProne + NullAway, Checkstyle, Spotless (google-java-format), JaCoCo (line >= 90%, branch >= 75% enforced), strict dependency locking + verification.

### Publishing to Maven Central

Publication uses the [Central Portal API via nmcp](https://gradleup.github.io/nmcp/). Required environment variables:

| Variable | Purpose |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central Portal token username |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal token password |
| `SIGNING_KEY` | ASCII-armored PGP private key (in-memory signing) |
| `SIGNING_PASSWORD` | PGP key passphrase |

```sh
docker compose run --rm sdk publishAggregationToCentralPortal --no-daemon
```

Uploads are validated and staged as `USER_MANAGED`; release them from <https://central.sonatype.com/>. Switch `publishingType` to `AUTOMATIC` in `settings.gradle.kts` to release immediately after validation. Snapshot publishing: `publishAggregationToCentralSnapshots`.

Local sanity check (no credentials needed):

```sh
docker compose run --rm sdk publishToMavenLocal --no-daemon
```

## License

[Apache License 2.0](./LICENSE), Copyright Vinkius.
