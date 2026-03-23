## syntax=docker/dockerfile:1.7

# Reproducible JVM build artifact for Multi-Agent Code Reviewer.
# Default target builds a runnable fat JAR using Java 26 (preview enabled at runtime).

FROM container-registry.oracle.com/java/jdk:26-oraclelinux9 AS build

ARG MAVEN_VERSION=3.9.11
ARG MAVEN_BASE_URL=https://archive.apache.org/dist/maven/maven-3

# Keep the Oracle Linux base and install a pinned Maven 3.9.x toolchain.
RUN yum -y update \
	&& yum -y install curl tar gzip \
	&& curl -fsSL "${MAVEN_BASE_URL}/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" -o /tmp/maven.tar.gz \
	&& tar -xzf /tmp/maven.tar.gz -C /opt \
	&& ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn \
	&& rm -f /tmp/maven.tar.gz \
	&& yum clean all
WORKDIR /workspace

# Cache dependencies first for faster rebuilds
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests dependency:go-offline

# Build the JVM artifact (same command family as README/CI)
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM container-registry.oracle.com/java/jdk:26-oraclelinux9 AS runtime
WORKDIR /app

# Keep runtime image minimal; copy built artifact only
COPY --from=build /workspace/target/multi-agent-reviewer-*.jar /app/multi-agent-reviewer.jar

# Default JVM options preserve project requirements (preview features)
ENV JAVA_TOOL_OPTIONS="--enable-preview"

ENTRYPOINT ["java", "-jar", "/app/multi-agent-reviewer.jar"]
