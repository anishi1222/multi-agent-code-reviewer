# Dual-JDK Build Activation

This project uses two separate POMs requiring different JDKs — activation via JAVA_HOME is mandatory.

## What Happened

During t7, the two-POM split was discovered. During t19, the settled baseline moved to
`pom.xml` Java 28 while `pom-native.xml` retained release 25. `.sdkmanrc` had incorrectly
selected a stale GraalVM 25 patch, so `sdk env` could not compile the default POM. t19 made
Java 28 the SDKMAN default and documented explicit GraalVM 25 activation for native builds.

Project: anishi1222/multi-agent-code-reviewer / t7, t19

## Takeaway

Always set JAVA_HOME before running Maven builds:
- `pom.xml` → `.sdkmanrc` / `export JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open`
- `pom-native.xml` → `export JAVA_HOME=~/.sdkman/candidates/java/25.0.4-graal`

Also prepend `$JAVA_HOME/bin` to `PATH`. Keep both POMs on the same Micronaut parent and
BOM-managed dependency versions; only their Java target is intentionally different.

## Example

```bash
# main build
JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open \
PATH=~/.sdkman/candidates/java/28.ea.9-open/bin:$PATH \
./mvnw -B clean verify

# native-image build
JAVA_HOME=~/.sdkman/candidates/java/25.0.4-graal \
PATH=~/.sdkman/candidates/java/25.0.4-graal/bin:$PATH \
./mvnw -B clean verify -Pnative -f pom-native.xml
```

## History

- 2026-08-05 (anishi1222/t7): initial
- 2026-08-07 (anishi1222/t19): updated the main baseline to Java 28 and made `.sdkmanrc` select it
