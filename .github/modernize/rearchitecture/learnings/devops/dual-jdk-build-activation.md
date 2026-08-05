# Dual-JDK Build Activation

This project uses two separate POMs requiring different JDKs — activation via JAVA_HOME is mandatory.

## What Happened

During t7 (environment prep), discovered that `pom.xml` (java.version=27) and `pom-native.xml`
(release.version=25) require different JDKs. Neither POM uses the `maven-toolchains-plugin`, so
JAVA_HOME must be set explicitly before running each build. The default active SDKMAN JDK is
GraalVM 25.0.4, which cannot compile `--release 27`.

Project: anishi1222/multi-agent-code-reviewer / t7

## Takeaway

Always set JAVA_HOME before running Maven builds:
- `pom.xml` → `export JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open`
- `pom-native.xml` → `export JAVA_HOME=~/.sdkman/candidates/java/25.0.4-graal`

Running either POM with the wrong JDK fails silently at enforcer or loudly at compiler.

## Example

```bash
# main build
JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open ./mvnw -B clean verify

# native-image build
JAVA_HOME=~/.sdkman/candidates/java/25.0.4-graal ./mvnw -B clean verify -Pnative -f pom-native.xml
```

## History

- 2026-08-05 (anishi1222/t7): initial
