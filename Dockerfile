FROM eclipse-temurin:24.0.2_12-jdk-noble@sha256:dacac8e9a0df0d2bd24e702b4431132875c249930b70555ebd7ca285b5bee684

ENV GRADLE_USER_HOME=/gradle-home \
    VINKIUS_SDK_BUILD=container

WORKDIR /workspace

CMD ["sh", "gradlew", "build", "--no-daemon"]
