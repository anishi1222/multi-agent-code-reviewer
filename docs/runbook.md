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

| Component        | Required Version        | Verification Command              |
|------------------|-------------------------|-----------------------------------|
| JDK              | 26 EA (preview enabled) | `java --version`                  |
| GraalVM          | 26 EA (native image only) | `native-image --version`        |
| Maven Wrapper    | Maven 3.9.14            | `./mvnw --version`                |
| GitHub CLI       | latest                  | `gh --version`                    |
| Copilot CLI      | 0.0.407+                | `gh copilot --version`            |
| SDKMAN (optional)| latest                  | `sdk version`                     |

### Quick Setup with SDKMAN

```bash
sdk env install    # reads .sdkmanrc for correct JDK
sdk env            # activates the JDK
```

---

## Doctor Check

Run these commands to validate your environment before building or releasing.

### 1. JDK Version Match

Confirm the installed JDK matches `pom.xml` `<jdk.version>`:

```bash
# Expected: matches <jdk.version> in pom.xml (currently 26)
java --version

# Cross-check pom.xml
grep '<jdk.version>' pom.xml
grep '<release.version>' pom.xml
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
./mvnw clean verify
```

---

## Build Verification

### JVM JAR Build

```bash
./mvnw clean package
java --enable-preview -jar target/multi-agent-reviewer-*.jar --version
```

### Native Image Build (Optional)

```bash
./mvnw clean package -Pnative
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

#### `--release 26` compilation failure

Ensure JDK version matches `pom.xml`:

```bash
java --version        # must be 26+
grep '<jdk.version>' pom.xml
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

- Native image build uses `continue-on-error: true` in CI — this is expected.
- Check GraalVM version compatibility with your dependencies.
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
