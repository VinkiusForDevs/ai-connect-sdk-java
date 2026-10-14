plugins {
    id("com.gradleup.nmcp.settings").version("1.6.2")
}

val inContainer = System.getenv("VINKIUS_SDK_BUILD") == "container"
val allowHost = gradle.startParameter.projectProperties["allowHostBuild"] == "true"

if (!inContainer && !allowHost) {
    throw GradleException(
        "Este build roda somente dentro do Docker Compose (nada de JDK/Gradle na maquina host).\n" +
            "Use:  docker compose run --rm sdk build --no-daemon\n" +
            "Ou:   docker compose run --rm sdk <task>\n" +
            "Override de emergencia: -PallowHostBuild=true",
    )
}

rootProject.name = "connect-sdk"

// Maven Central via the Central Portal (nmcp). Credentials are read from the environment;
// a real release also needs SIGNING_KEY/SIGNING_PASSWORD (see build.gradle.kts signing block).
// Publish with:
//   docker compose run --rm sdk publishAggregationToCentralPortal --no-daemon
nmcpSettings {
    centralPortal {
        username = providers.environmentVariable("MAVEN_CENTRAL_USERNAME").orNull ?: ""
        password = providers.environmentVariable("MAVEN_CENTRAL_PASSWORD").orNull ?: ""
        // USER_MANAGED: uploads are validated and staged; release them from the portal UI.
        // Switch to "AUTOMATIC" to release immediately after a successful validation.
        publishingType = "USER_MANAGED"
        publicationName = "connect-sdk:${providers.gradleProperty("sdkVersion").orNull ?: ""}"
    }
}
