# Multi-Agent Code Reviewer

AI-powered parallel code review CLI that orchestrates specialized agents through the
[GitHub Copilot SDK for Java](https://github.com/github/copilot-sdk).

- English guide: [README_en.md](./README_en.md)
- 日本語ガイド: [README_ja.md](./README_ja.md)
- Release notes: [English](./RELEASE_NOTES_en.md) / [日本語](./RELEASE_NOTES_ja.md)

## Release State

The current repository tree is published as
[`v2026.08.10-layered-architecture`](https://github.com/anishi1222/multi-agent-code-reviewer/releases/tag/v2026.08.10-layered-architecture).

The 2026-08-10 release completes the in-place Ports & Adapters rewrite, upgrades the JVM build to
Java 28 and Micronaut 5.1.0, upgrades `copilot-sdk-java` to 1.0.8, hardens agent-definition trust
and log redaction, and validates both the shaded JAR and the GraalVM 25 native executable.

## Runtime and Toolchains

| Purpose | Required toolchain | Source of truth |
|---|---|---|
| JVM build and run | OpenJDK `28.ea.9-open`, preview enabled | `.sdkmanrc`, `pom.xml` |
| Native Image build | Oracle GraalVM `25.0.4` | `pom-native.xml`, `.github/workflows/ci.yml` |
| Maven | Wrapper-pinned Maven 3.9.14 | `.mvn/wrapper/maven-wrapper.properties` |

The JVM and native builds deliberately use different Java releases. Do not run the native build
with the Java 28 toolchain or silently downgrade the JVM build to Java 25.

Other runtime prerequisites:

- GitHub CLI (`gh`) authenticated with `gh auth login`
- GitHub Copilot CLI authenticated with `gh copilot -- login` or `copilot login`
- A GitHub Copilot entitlement for the authenticated user or organization

## Quick Start

```bash
# Activate the JVM toolchain declared by the repository
sdk env install
sdk env

# Full JVM build: unit tests, integration tests, architecture rules, and shaded-JAR checks
./mvnw -B clean verify

# Run all bundled agents against a GitHub repository
java --enable-preview \
  -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run --repo owner/repository --all

# Review a local directory
java --enable-preview \
  -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run --local ./my-project --all
```

The executable shaded JAR is written to
`target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar`.

## Features

- Parallel security, code-quality, performance, best-practices, and Azure WAF review agents
- GitHub repository and local-directory targets
- Runtime `.agent.md` definitions from `./agents`, `./.github/agents`, and explicit
  `--agents-dir` locations
- Agent-bound `SKILL.md` criteria with size, expansion, and trust validation
- Default-enabled two-model rubber-duck dialogue with configurable peer model and rounds
- Optional standard-review multi-pass execution through
  `reviewer.execution.concurrency.review-passes` (default `1`), producing distinct per-pass reports
- Compact prompt budgets, deterministic Executive Summary finding deduplication, and
  evidence-based Good Points
- Shaded JAR and GraalVM Native Image packaging
- Official Azure Skills fallback plus Azure MCP and Microsoft Learn MCP configuration

## Architecture

Production code uses **five named layers plus a layer-zero composition root** under
`src/main/java/dev/logicojp/reviewer/`:

| Area | Responsibility |
|---|---|
| `ReviewApp`, `ApplicationPortFactory`, `ReviewPortFactory` | Thin process entry and wiring only |
| `presentation/` | CLI parsing, commands, target/agent resolution, console formatting |
| `application/` | Framework-free use cases and orchestration |
| `application/port/inbound/` | 8 interfaces driven by presentation |
| `application/port/outbound/` | 15 interfaces implemented by infrastructure |
| `domain/` | Business rules and models; JDK and `shared` dependencies only |
| `infrastructure/` | Copilot SDK, filesystem, configuration, parsing, logging, and startup adapters |
| `shared/` | Pure cross-layer utilities and canonical defaults |

The current tree contains 201 production Java source files. Dependencies point inward:
presentation drives inbound ports, application owns the use cases, and infrastructure implements
outbound ports. `ReviewApp` starts the Micronaut CLI context; the two root factories bind
already-constructed adapters to application use cases without owning business policy.

`LayerDependencyRulesTest` enforces the import matrix and detects package/layer cycles by inspecting
compiled bytecode with the JDK `java.lang.classfile` API. This avoids a Java 28 class-file parser
dependency while making architecture violations fail `mvn verify`.

See:

- [ADR-0006: Ports & Adapters layering](./docs/adr/0006-ports-and-adapters-layering.md)
- [ADR-0007: Agent-definition trust and secret sink boundary](./docs/adr/0007-agent-definition-trust-model-and-secret-sink-boundary.md)
- [ADR-0008: Control scope must be visible at the call site](./docs/adr/0008-control-scope-must-be-visible-at-the-call-site.md)
- [Detailed diagrams (English)](./README_en.md#architecture)
- [詳細図（日本語）](./README_ja.md#アーキテクチャ)

## Configuration

Core configuration lives in `src/main/resources/application.yml`.

```yaml
reviewer:
  execution:
    concurrency:
      parallelism: 4
      review-passes: 1
    timeouts:
      orchestrator-timeout-minutes: 45
      agent-timeout-minutes: 20
      skill-timeout-minutes: 20
      summary-timeout-minutes: 20
    retry:
      max-retries: 2
  models:
    default-model: claude-sonnet-5
    review-model: gpt-5.3-codex
    report-model: claude-sonnet-5
    summary-model: claude-sonnet-5
    reasoning-effort: high
  rubber-duck:
    enabled: true
    dialogue-rounds: 3
    peer-model: ${RUBBER_DUCK_PEER_MODEL:gpt-5.6-sol}
```

`review-passes` is nested under `reviewer.execution.concurrency`; the retired
`reviewer.execution.review-passes` path does not control execution. Prompt-budget keys are optional:
their canonical defaults live in `shared/PromptBudget`, and `application.yml` documents the
available override names without duplicating those values.

Useful environment variables include `COPILOT_CLI_PATH`, `GH_CLI_PATH`,
`GH_AUTH_FALLBACK_ENABLED`, `COPILOT_SDK_LOG_LEVEL`, and `RUBBER_DUCK_PEER_MODEL`.

## Security Boundaries

- Agent-definition provenance is carried as typed `AgentSource` / `AgentSourceDirectory` data.
  Definitions discovered from configured repository directories receive the stricter
  `REPOSITORY_SUPPLIED` policy; explicitly selected directories retain `USER_SUPPLIED` provenance.
- Agent names, models, focus areas, prompts, MCP settings, and assigned skills are validated before
  they can reach a Copilot prompt. Model validation accepts the pinned provider families
  `claude-`, `gpt-`, `o3`, `o4-mini`, and `gemini-`.
- Unicode validation rejects unsafe control/default-ignorable characters while retaining an
  explicit bounded allowlist for legitimate formatting characters.
- Secret masking is enforced at both Logback output sinks. Object wrappers are not treated as a
  security boundary because arbitrary logging/stringification paths can bypass them.
- The final dependency-security gate evaluated 31 runtime coordinates and reported zero known CVEs;
  automated Dependency Review, OWASP audit, CodeQL, and OpenSSF Scorecard workflows remain enabled.

For JVM production runs that handle credentials, consider disabling runtime attach and automatic
heap dumps:

```bash
java --enable-preview \
  -XX:+DisableAttachMechanism \
  -XX:-HeapDumpOnOutOfMemoryError \
  -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run --repo owner/repository --all
```

## Packaging

### Shaded JAR

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/28.ea.9-open" \
PATH="$JAVA_HOME/bin:$PATH" \
./mvnw -B clean verify
```

The package phase builds the executable shaded JAR, includes all 28 Markdown templates, and verifies
the packaged `--help` and `--version` paths during Failsafe integration tests.

### GraalVM Native Image

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.4-graal" \
PATH="$JAVA_HOME/bin:$PATH" \
./mvnw -B clean verify -Pnative -f pom-native.xml

./target/review --version
```

Native metadata is maintained in both the generic and artifact-scoped
`META-INF/native-image/.../reachability-metadata.json` locations. The two copies must remain
byte-identical and use exact member registration for SDK/Jackson/Logback reflective paths.

This repository does **not** currently ship a `Dockerfile`; use the shaded JAR or `target/review`
instead of relying on an undocumented container build.

## Copilot SDK License and Service Terms

The project depends on `com.github:copilot-sdk-java:1.0.8`, whose code is distributed under the MIT
License. Calls to GitHub Copilot remain governed by the applicable GitHub Copilot product terms and
the authenticated user's or organization's entitlement. Do not share one login among unrelated
users or expose Copilot as a transparent multi-tenant backend without product/legal review.

## Operations and Release

- Human-readable logging defaults to `stderr`; use
  `-Dlogback.configurationFile=src/main/resources/logback-json.xml` for structured logging.
- `scripts/archive-reports.sh` creates compressed report archives for CI retention.
- [Operational runbook](./docs/runbook.md)
- [ADR index](./docs/adr/README.md)

Pushing a version tag triggers `.github/workflows/release.yml`, which builds the Java 28 JVM
artifact, generates an SBOM and checksums, and publishes a GitHub Release. The native release job is
currently disabled; `.github/workflows/ci.yml` separately builds and tests the Native Image with
GraalVM 25.0.4. Do not invent a release version in documentation before a corresponding tag exists.

## License

MIT License
