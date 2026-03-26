## syntax=docker/dockerfile:1.7

# Reproducible JVM build artifact for Multi-Agent Code Reviewer.
# Default target builds a runnable fat JAR using Java 26 (preview enabled at runtime).

FROM openjdk:26-jdk-oraclelinux9@sha256:3e3792bd10b348c336f0a1a91df24ba4d9aed995f6346cf36bb68b2e990353ce AS build

ARG MAVEN_VERSION=3.9.11
ARG MAVEN_BASE_URL=https://archive.apache.org/dist/maven/maven-3

# Use a public digest-pinned OpenJDK 26 base on Oracle Linux 9 and install a pinned Maven toolchain.
RUN microdnf update -y \
	&& microdnf install -y curl tar gzip \
	&& curl -fsSL "${MAVEN_BASE_URL}/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" -o /tmp/maven.tar.gz \
	&& tar -xzf /tmp/maven.tar.gz -C /opt \
	&& ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn \
	&& rm -f /tmp/maven.tar.gz \
	&& microdnf clean all
WORKDIR /workspace

# Cache dependencies first for faster rebuilds
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests dependency:go-offline

COPY src ./src

# Build the JVM artifact (same command family as README/CI)
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM openjdk:26-jdk-oraclelinux9@sha256:3e3792bd10b348c336f0a1a91df24ba4d9aed995f6346cf36bb68b2e990353ce AS runtime
WORKDIR /app

# Bundle the repository defaults used by the CLI at runtime.
COPY agents /app/agents
COPY templates /app/templates
COPY .github/agents /app/.github/agents
COPY .github/skills /app/.github/skills

# Keep runtime image minimal; copy built artifact only
COPY --from=build /workspace/target/multi-agent-reviewer-*.jar /app/multi-agent-reviewer.jar

# Default JVM options preserve project requirements (preview features)
ENV JAVA_TOOL_OPTIONS="--enable-preview"

ENTRYPOINT ["java", "-jar", "/app/multi-agent-reviewer.jar"]
