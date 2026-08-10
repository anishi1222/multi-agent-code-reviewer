# Operational Runbook

This document covers day-to-day operations, troubleshooting, and maintenance procedures for Multi-Agent Code Reviewer.

---

## Table of Contents

1. [Environment Prerequisites](#environment-prerequisites)
2. [Doctor Check (Environment Validation)](#doctor-check)
3. [Build Verification](#build-verification)
4. [Release Procedure](#release-procedure)
5. [Rollback Procedure](#rollback-procedure)
6. [Structured Logging](#structured-logging)
7. [Troubleshooting](#troubleshooting)
8. [Dependency Audit](#dependency-audit)
9. [Security Considerations](#security-considerations)

---

## Environment Prerequisites

| Component         | Required Version                       | Verification Command       |
|-------------------|----------------------------------------|----------------------------|
| JVM build JDK     | Java 28 (preview enabled)              | `java --version`           |
| Native build JDK  | Oracle GraalVM 25.0.4 (Java 25 target) | `native-image --version`   |
| Maven Wrapper     | Maven 3.9.14                           | `./mvnw --version`         |
| GitHub CLI        | latest                                | `gh --version`             |
| Copilot CLI       | 0.0.407+                              | `gh copilot --version`     |
| SDKMAN (optional) | latest                                | `sdk version`              |

### Quick Setup with SDKMAN

```bash
sdk env install    # reads .sdkmanrc for correct JDK
sdk env            # activates Java 28 for pom.xml
java --version
```

### Toolchain Source of Truth

- `pom.xml` is the default JVM/release build and compiles for Java 28. `.sdkmanrc` intentionally
  selects this toolchain because `sdk env` is applied automatically by many developer shells.
- `pom-native.xml` is the native-image compatibility build and compiles for Java 25. A single
  `.sdkmanrc` cannot select both toolchains, so native builds must activate GraalVM explicitly:

  ```bash
  export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.4-graal"
  export PATH="$JAVA_HOME/bin:$PATH"
  java --version
  native-image --version
  ```

- Do not run `pom.xml` with GraalVM 25 or `pom-native.xml` with the Java 28 JDK. Both POMs use
  `micronaut-parent:5.1.0`; dependency versions are parent-managed to avoid manifest drift.

---

## Doctor Check

Run these commands to validate your environment before building or releasing.

### 1. JDK Version Match

Confirm the installed JDK matches `pom.xml` `<java.version>`:

```bash
# Expected: Java 28, matching pom.xml
java --version

# Cross-check pom.xml
grep '<java.version>' pom.xml
```

### 2. Maven Toolchain

```bash
./mvnw --version
# Confirm the wrapper resolved Maven 3.9.14 and that Java home points to the correct JDK
```

### 3. GitHub Authentication

```bash
gh auth status
gh copilot -- version   # or: copilot --version
```

If authentication fails:

```bash
gh auth login
gh copilot -- login     # or: copilot login
```

### 4. Supply Chain Validation

```bash
# Enforcer + checksum validation (no tests)
./mvnw -B -ntp -DskipTests validate
```

### 5. Full Build Smoke Test

```bash
./mvnw -B clean verify
```

---

## Build Verification

### JVM JAR Build

```bash
./mvnw -B clean verify
java --enable-preview -jar target/multi-agent-reviewer-*.jar --version
```

`verify` runs unit tests and a Failsafe integration test that starts the shaded JAR from an
isolated temporary directory with `--help`, `--version`, `list`, and `doctor --help`. This proves
the manifest, embedded templates, logging configuration, and Micronaut bootstrap path used by the
distributed artifact. `package -DskipTests` is diagnostic-only and is not release evidence.

### Native Image Build (Optional)

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.4-graal"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B clean verify -Pnative -f pom-native.xml
./target/review --version
```

### SBOM Generation

```bash
./mvnw -DskipTests cyclonedx:makeAggregateBom
# Output: target/sbom.json
```

### Artifact Checksums

```bash
cd target
sha256sum multi-agent-reviewer-*.jar sbom.json > SHA256SUMS.txt
cat SHA256SUMS.txt
```

---

## Release Procedure

### Release Channels

| Channel      | Tag Pattern               | GitHub Release Type | Example                          |
|--------------|---------------------------|---------------------|----------------------------------|
| Pre-release  | `v*-rc*`, `v*-alpha*`, `v*-beta*` | Pre-release  | `v2026.05.01-feature-rc1`        |
| Stable       | `v*` (no rc/alpha/beta)   | Release             | `v2026.05.01-feature`            |

### Automated Release (Recommended)

1. **Prepare release notes** in `RELEASE_NOTES_en.md` and `RELEASE_NOTES_ja.md`.
2. **Create and push a tag**:

```bash
# For stable release
git tag -a v2026.05.01-feature -m "Release v2026.05.01-feature"
git push origin v2026.05.01-feature

# For pre-release
git tag -a v2026.05.01-feature-rc1 -m "Pre-release v2026.05.01-feature-rc1"
git push origin v2026.05.01-feature-rc1
```

3. The `release.yml` workflow automatically:
   - Builds the JVM artifact with tests
   - Builds the native image (best-effort)
   - Generates SBOM (`sbom.json`)
   - Generates SHA-256 checksums
   - Publishes a GitHub Release with all artifacts

4. **Verify the release** on the GitHub Releases page.

### Promoting Pre-release to Stable

```bash
# On GitHub: edit the pre-release and uncheck "Set as a pre-release"
# Or create a new stable tag pointing to the same commit:
git tag -a v2026.05.01-feature <commit-sha>
git push origin v2026.05.01-feature
```

### Manual Release Checklist

If the automated workflow is unavailable, follow these steps:

- [ ] Run `./mvnw clean verify` — all tests must pass
- [ ] Generate SBOM: `./mvnw -DskipTests cyclonedx:makeAggregateBom`
- [ ] Generate checksums for all artifacts
- [ ] Update `RELEASE_NOTES_en.md` and `RELEASE_NOTES_ja.md`
- [ ] Update `README_en.md` and `README_ja.md` with release references
- [ ] Create annotated tag and push
- [ ] Create GitHub Release with artifacts, SBOM, and checksums attached

---

## Rollback Procedure

### Application Rollback

1. **Identify the last known-good release** on the GitHub Releases page.
2. **Download the artifact** from the release:

```bash
gh release download v2026.04.14-model-auth-check --pattern '*.jar'
```

3. **Verify the checksum**:

```bash
sha256sum -c SHA256SUMS.txt
```

4. **Deploy the verified artifact**.

### Git Rollback

If the latest commit introduces a regression:

```bash
# Revert the commit
git revert <bad-commit-sha>
git push origin main

# Or reset to a known-good tag (destructive — coordinate with team)
git reset --hard v2026.04.14-model-auth-check
git push --force-with-lease origin main
```

### CI Rollback

If a workflow change breaks CI:

1. Revert the workflow file change on a branch.
2. Open a PR and verify CI passes.
3. Merge to restore CI.

---

## Structured Logging

### Default (Human-Readable)

The default logging configuration (`src/main/resources/logback.xml`) produces human-readable output with MDC context:

```
2026-04-14 10:30:00 [main] INFO  d.l.reviewer.ReviewApp [exec:abc-123] [lifecycle:start] - Review started
```

### JSON Structured Logging

For log aggregation (ELK, Azure Monitor, Splunk), enable the JSON profile:

```bash
java --enable-preview \
  -Dlogback.configurationFile=src/main/resources/logback-json.xml \
  -jar target/multi-agent-reviewer-*.jar run --repo owner/repo --all
```

Output format:

```json
{"timestamp":"2026-04-14T10:30:00.000+09:00","level":"INFO","logger":"d.l.reviewer.ReviewApp","thread":"main","execution.id":"abc-123","event.category":"lifecycle","event.action":"start","message":"Review started"}
```

### Copilot SDK Log Level

Use the allowlisted `warning` level in production so SDK diagnostics remain available without
enabling verbose payload logging:

```bash
export COPILOT_SDK_LOG_LEVEL=warning
java --enable-preview -jar target/multi-agent-reviewer-*.jar --version
```

The application accepts only its configured log-level allowlist. Do not weaken the allowlist or
use `debug`/`trace` as a production workaround; those levels increase the chance that third-party
diagnostic messages contain sensitive request context. Sink-side masking remains mandatory for
both human-readable and JSON logging profiles.

### MDC Keys

| Key              | Description                          | Example        |
|------------------|--------------------------------------|----------------|
| `execution.id`   | Unique review execution identifier   | `abc-123`      |
| `event.category` | Event classification                 | `lifecycle`    |
| `event.action`   | Specific action within the category  | `start`        |

### Security Audit Log

Security-related events are written to both console and `logs/security-audit.log`:

- 14-day rotation, 100 MB cap
- Token patterns are automatically masked (GitHub PATs, Bearer tokens, etc.)
- Controlled by the `SECURITY_AUDIT` logger

---

## Troubleshooting

### Build Failures

#### `NoSuchMethodError` for synthetic methods

Stale compiled classes can cause `access$0` errors:

```bash
./mvnw clean test
```

#### `--release 28` compilation failure

Ensure JDK version matches `pom.xml`:

```bash
java --version        # must be 28
grep '<java.version>' pom.xml
```

#### SNAPSHOT dependency rejection

The Maven Enforcer plugin blocks SNAPSHOT dependencies. If you see this error during development:

```bash
# Use the enforcer skip for local development only
./mvnw -B package -Denforcer.skip=true
```

**Never skip enforcer in CI or release builds.**

### Runtime Issues

#### Copilot CLI authentication failure

```bash
# Re-authenticate
gh auth login
gh copilot -- login

# Verify
gh auth status
```

#### Token exposure concern

If tokens may have been logged or dumped:

1. Rotate the affected token immediately.
2. Check `logs/security-audit.log` — tokens should be masked.
3. Review any heap dumps for token strings (see Security Runtime Notes in README).

#### Agent timeout

Increase timeouts in `application.yml` or via CLI:

```yaml
reviewer:
  execution:
    timeouts:
      agent-timeout-minutes: 30    # default: 20
      orchestrator-timeout-minutes: 60  # default: 45
```

### CI Failures

#### Supply Chain Guard failure

- Check if `NVD_API_KEY` secret is configured in repository settings.
- OWASP dependency-check requires network access to NVD.

#### Native image build failure

- Native image verification is a required CI gate; do not bypass it with `continue-on-error`,
  `-DskipTests`, or a narrowed lifecycle.
- Confirm the active toolchain is Oracle GraalVM 25.0.4 and the command uses `-f pom-native.xml`.
- Review `reflect-config.json` and `resource-config.json` under `src/main/resources/META-INF/native-image/`.

---

## Dependency Audit

### Automated (CI)

- **On every PR**: `dependency-review.yml` scans for new vulnerabilities (severity ≥ moderate).
- **Weekly schedule**: `dependency-audit.yml` runs full OWASP dependency check.
- **On every push/PR**: `scorecard.yml` evaluates supply-chain security posture.

### Manual Audit

```bash
# Full OWASP dependency check (requires NVD API key)
mvn -Psecurity-audit verify -DskipTests -Dnvd_api_key=YOUR_KEY

# View SBOM
mvn -DskipTests cyclonedx:makeAggregateBom
cat target/sbom.json | python3 -m json.tool | head -50
```

### Dependabot

Dependabot is configured for both Maven and GitHub Actions dependencies with daily update checks. See `.github/dependabot.yml`.

---

## Security Considerations

### Secrets Management

| Secret                | Storage Location          | Usage                         |
|-----------------------|---------------------------|-------------------------------|
| `NVD_API_KEY`         | GitHub repository secrets | OWASP dependency check in CI  |
| GitHub tokens         | `gh auth` / env runtime   | Copilot SDK authentication    |

**Never** store secrets in:
- `application.yml` or any committed configuration file
- Log output (token masking is enforced by logback patterns)
- Environment variable defaults in committed files

### Supply Chain Controls

| Control                           | Enforcement Point          |
|-----------------------------------|----------------------------|
| Checksum verification             | Maven `<checksumPolicy>fail</checksumPolicy>` |
| SNAPSHOT dependency rejection     | Maven Enforcer plugin      |
| Dependency convergence            | Maven Enforcer plugin      |
| PR vulnerability scanning         | `dependency-review.yml`    |
| License deny-list                 | `dependency-review.yml`    |
| Scheduled vulnerability audit     | `dependency-audit.yml`     |
| CodeQL static analysis            | `codeql.yml`               |
| OSSF Scorecard                    | `scorecard.yml`            |
| SBOM generation                   | CycloneDX Maven plugin     |
| Action SHA pinning                | All workflow files          |
| Runner hardening                  | `step-security/harden-runner` |

### Configuration Precedence

Configuration values are resolved in this order (highest priority first):

1. **CLI arguments** (`--model`, `--timeout`, etc.)
2. **Environment variables** (`COPILOT_CLI_PATH`, `GH_CLI_PATH`, etc.)
3. **`application.yml`** defaults (committed to repository)

Sensitive values (tokens, API keys) should only be provided via environment variables or CLI arguments at runtime.
