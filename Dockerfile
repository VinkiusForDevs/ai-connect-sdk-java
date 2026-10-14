FROM eclipse-temurin:21.0.12_8-jdk-noble@sha256:4d271cd5e0624598cf563342f47281b09cb364bc13acbbd7251f49f83470018d

ENV GRADLE_USER_HOME=/gradle-home \
    VINKIUS_SDK_BUILD=container

WORKDIR /workspace

CMD ["sh", "gradlew", "build", "--no-daemon"]
