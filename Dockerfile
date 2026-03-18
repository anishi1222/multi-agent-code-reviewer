## syntax=docker/dockerfile:1.7

# Reproducible JVM build artifact for Multi-Agent Code Reviewer.
# Default target builds a runnable fat JAR using Java 26 (preview enabled at runtime).

FROM maven:3.9.9-eclipse-temurin-26 AS build
WORKDIR /workspace

# Cache dependencies first for faster rebuilds
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests dependency:go-offline

# Copy source and resources
COPY src ./src
COPY agents ./agents
COPY templates ./templates
COPY .github ./.github

# Build the JVM artifact (same command family as README/CI)
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM eclipse-temurin:26-jre AS runtime
WORKDIR /app

# Keep runtime image minimal; copy built artifact only
COPY --from=build /workspace/target/multi-agent-reviewer-*.jar /app/multi-agent-reviewer.jar

# Default JVM options preserve project requirements (preview features)
ENV JAVA_TOOL_OPTIONS="--enable-preview"

ENTRYPOINT ["java", "-jar", "/app/multi-agent-reviewer.jar"]
