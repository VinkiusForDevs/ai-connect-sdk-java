import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-library`
    `maven-publish`
    signing
    jacoco
    checkstyle
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

group = "com.vinkius"
version = providers.gradleProperty("sdkVersion").get()

description = "Give your AI application real-world capabilities: per-user connectors and executable AI capabilities."

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.jspecify)
    implementation(libs.jackson.databind)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

dependencyLocking {
    lockAllConfigurations()
}

val generateVersionSource = tasks.register("generateVersionSource") {
    val sdkVersion = providers.gradleProperty("sdkVersion")
    val outputDir = layout.buildDirectory.dir("generated/version/src")
    inputs.property("sdkVersion", sdkVersion)
    outputs.dir(outputDir)
    doLast {
        val packageDir = outputDir.get().asFile.resolve("com/vinkius/connect")
        packageDir.mkdirs()
        packageDir.resolve("Version.java").writeText(
            """
            package com.vinkius.connect;

            public final class Version {
                public static final String SDK_VERSION = "${sdkVersion.get()}";

                private Version() {}
            }
            """.trimIndent() + "\n",
        )
    }
}

sourceSets {
    main {
        java.srcDir(generateVersionSource)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-parameters",
            "-Xlint:all",
            "-Xlint:processing",
            "-Werror",
        ),
    )
    options.errorprone {
        errorproneArgs.add("-XepDisableWarningsInGeneratedCode")
        excludedPaths = ".*/build/generated/.*"
        errorproneArgs.add("-Xep:NullAway:ERROR")
        errorproneArgs.add("-XepOpt:NullAway:AnnotatedPackages=com.vinkius")
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.jar {
    manifest {
        attributes(
            "Automatic-Module-Name" to "com.vinkius.connect",
            "Implementation-Title" to "Vinkius Connect SDK",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Vinkius",
        )
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

spotless {
    java {
        target("src/*/java/**/*.java")
        licenseHeaderFile("config/license-header.txt")
        googleJavaFormat(libs.versions.googleJavaFormat.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    configFile = file("config/checkstyle/checkstyle.xml")
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

tasks.javadoc {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:all,-missing", true)
        addBooleanOption("Werror", true)
        windowTitle = "Vinkius Connect SDK ${project.version}"
        docTitle = "Vinkius Connect SDK"
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "connect-sdk"
            from(components["java"])
            pom {
                name = "Vinkius Connect SDK"
                description = project.description
                url = "https://github.com/vinkius-labs/ai-connect-sdk-java"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        distribution = "repo"
                    }
                }
                developers {
                    developer {
                        id = "vinkius"
                        name = "Vinkius"
                        url = "https://vinkius.com"
                    }
                }
                scm {
                    url = "https://github.com/vinkius-labs/ai-connect-sdk-java"
                    connection = "scm:git:https://github.com/vinkius-labs/ai-connect-sdk-java.git"
                    developerConnection = "scm:git:ssh://git@github.com/vinkius-labs/ai-connect-sdk-java.git"
                }
            }
        }
    }
}

val signingKey = providers.gradleProperty("signingKey").orElse(providers.environmentVariable("SIGNING_KEY"))
val signingPassword = providers.gradleProperty("signingPassword").orElse(providers.environmentVariable("SIGNING_PASSWORD"))

signing {
    if (signingKey.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), signingPassword.orNull)
        sign(publishing.publications)
    }
}
