# Multi-Agent Code Reviewer

AI-powered parallel code review tool that orchestrates multiple specialized agents using the GitHub Copilot SDK for Java.

## Prerequisites

- Java: SDKMAN-managed JDK from `.sdkmanrc` (compile target release 27, preview features enabled)
- Build: Maven Wrapper (`./mvnw`, pinned to Maven 3.9.14)
- Auth: GitHub CLI (`gh`) and GitHub Copilot CLI (`github-copilot` or `copilot`)

This repository uses `.sdkmanrc` for local JDK alignment:

```bash
sdk env install
sdk env
```

## Quick Start

```bash
./mvnw clean package
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar run --repo owner/repo --all
```

## Latest Remediation

- 2026-07-21 (Unreleased): Simplified each agent to one standard review, retained default-enabled rubber-duck dialogue for additional perspectives, removed multi-pass/shared-session/checkpoint/merge code, added compact prompt budgets, injected safe agent-bound SKILL criteria, deduplicated Executive Summary findings across agents, and required evidence-based Good Points from every runtime and GitHub custom-agent definition.
- 2026-06-24 (`v2026.06.24-refactor-seams-tests`): Refactoring seam extraction and direct test coverage — split rubber-duck dialogue, review pass/session execution, summary AI transport/output writing, review CLI option model, agent definition parsing, template repository loading, and GitHub token resolution into focused collaborators; added direct unit tests for the extracted seams; fixed hybrid local-review source propagation and hardened `gh auth token` stdout/stderr draining with bounded stream collection. Verified with JDK 27 EA full clean test suite (871 tests, 0 failures).
- 2026-06-24 (`v2026.06.24-dependency-ci-hardening`): Dependency, CI, and module-structure hardening — upgraded `copilot-sdk-java` to 1.0.1 and Micronaut to 5.1.2, standardized JVM builds on JDK 27 and Native Image builds on GraalVM 25.0.3, added JDK 27 Dependency Submission, hardened OWASP Dependency Audit, constrained Jackson 2.x/3.x dependency management, and resolved all open `jackson-databind` Dependabot alerts.
- 2026-06-08 (`v2026.06.08-agent-model-defaults`): Agent model defaults documentation sync — removed model pins from GitHub Copilot custom-agent configuration references, clarified that review model overrides should be supplied via CLI/configuration instead of `.github/agents` frontmatter, refreshed model examples to the current runtime defaults, and updated the documented Copilot SDK dependency to `1.0.0-beta-10-java.5`.
- 2026-05-28 (`v2026.05.28-azure-skills-mcp`): Azure Skills and MCP configuration — added official `microsoft/azure-skills` project skills under `.agents/skills/`, tracked them in `skills-lock.json`, configured Azure MCP and Microsoft Learn MCP in `.vscode/mcp.json`, rewrote WAF skills to require Microsoft Learn MCP grounding, and documented Copilot CLI plugin install commands for users who have not installed Azure Skills yet.
- 2026-05-28 (`v2026.05.28-ci-release-hardening`): CI and release hardening — changed workflow defaults to `permissions: {}`, granted `contents: read` only to build jobs and `contents: write` only to the release-publishing job, aligned the release workflow JDK with compiler release 27, removed unnecessary release checkout by setting `GH_REPO`, eliminated duplicate OWASP Dependency Check execution from `Supply Chain Guard` so deep auditing runs in `Dependency Audit`, switched CodeQL Java/Kotlin analysis to `build-mode: none`, and refreshed GitHub Actions/Maven plugin dependencies.
- 2026-05-15 (`v2026.05.15-runtime-compat`): Runtime compatibility and report-accuracy fixes — aligned structured concurrency helpers with JDK 27 `StructuredTaskScope` generics, removed macOS `/bin/true` test-path dependency, expanded trusted CLI real-path directories for Homebrew `Cellar`/`Caskroom` (fixing `gh auth token` fallback and `copilot` discovery), normalized Copilot SDK log-level mapping (`warn` → `warning`), fixed permission deny result kind serialization (`REJECTED`), and excluded "no findings" placeholder blocks from overall finding counts. Verified by `mvn clean package` (830 tests passed).
- 2026-04-30 (`v2026.04.30-copilot-sdk-stable`): Upgraded GitHub Copilot SDK for Java from preview `0.3.0-java-preview.1` to stable `0.3.0-java.2`, normalized GitHub Actions `JDK_VERSION` from `26.0.1` to `26` across `ci.yml`/`codeql.yml`/`dependency-audit.yml`/`release.yml`, pinned the CycloneDX Maven plugin to `2.9.1` in the release workflow, and granted `contents: write` to the `publish-release` job so `gh release create` succeeds under the workflow-level least-privilege default (`contents: read`).
- 2026-04-30 (`v2026.04.30-micronaut5-snapshot`): Tracked Micronaut 5 by upgrading the parent BOM and platform version to `5.0.0-SNAPSHOT`, registered the Sonatype Central Snapshots repository, relaxed the SNAPSHOT enforcer rule (annotated TODO), and disabled `failOnNotPresent` in the new Micronaut 5 configuration validator to ignore the annotation processor `micronaut.processing.*` argument. Verified `mvn clean package` and 829 tests on Java 26 (Oracle 26.0.1).
- 2026-04-23 (`v2026.04.23-copilot-sdk-compat`): Upgraded GitHub Copilot SDK for Java to `0.3.0-java-preview.1` and aligned the codebase with SDK API changes.
- Compatibility fixes: switched event imports to `com.github.copilot.sdk.generated.*` and adjusted MCP server handoff for the new `setMcpServers(Map<String, McpServerConfig>)` signature.
- Release Notes: [RELEASE_NOTES_en.md](./RELEASE_NOTES_en.md), [RELEASE_NOTES_ja.md](./RELEASE_NOTES_ja.md)
- GitHub Release: https://github.com/anishi1222/multi-agent-code-reviewer/releases/tag/v2026.06.24-refactor-seams-tests

## Architecture

Execution flow:

1. `ReviewApp` parses CLI arguments and dispatches commands.
2. `ReviewCommand` resolves target/agents/models/options.
3. `ReviewOrchestrator` runs each agent in parallel (virtual threads + structured concurrency).
4. `ReviewAgent` performs one standard review or a per-agent rubber-duck dialogue, applying explicitly assigned SKILL criteria.
5. Each agent emits evidence-based Good Points and improvement findings in one synthesized `ReviewResult`.
6. `SummaryGenerator` receives all agent results plus a deterministic cross-agent deduplicated finding list and creates the Executive Summary.

Main directories:

- `src/main/java/dev/logicojp/reviewer/cli`: command parsing, command handlers, and review option model
- `src/main/java/dev/logicojp/reviewer/orchestrator`: parallel execution pipeline
- `src/main/java/dev/logicojp/reviewer/agent`: agent loading, SKILL-scoped prompt construction, single-review/session execution, rubber-duck dialogue
- `src/main/java/dev/logicojp/reviewer/report/summary`: summary prompt, AI transport, fallback, and secure summary writing
- `src/main/java/dev/logicojp/reviewer/service`: template catalog and repository loading
- `src/main/java/dev/logicojp/reviewer/util`: token input, gh CLI lookup/auth, retry, permissions, and security helpers
- `templates/`: markdown templates used for report and summary generation
- `agents/`: built-in `.agent.md` definitions

## Configuration

Core configuration lives in `src/main/resources/application.yml`.

- `reviewer.execution.*`: parallelism, timeout, retry, buffer settings
- `reviewer.rubber-duck.*`: default-enabled peer model, dialogue rounds, and synthesis strategy
- `reviewer.prompt-budget.*`: optional compact prompt budgets for peer, synthesis, local-source, and summary content
- `reviewer.models.*`: review/report/summary model selection
- `reviewer.templates.*`: template directory and template filenames
- `reviewer.summary.*`: prompt sizing and fallback behavior
- `reviewer.skills.*`: global skill discovery and executor cache settings

Behavioral guarantees:

- SKILL definitions with matching `metadata.agent` are safety/size checked and added to that agent's standard and rubber-duck review instructions.
- Every bundled `agents/` and `.github/agents/` definition outputs evidence-based `Good Points` before improvement findings.
- Executive Summary findings are deduplicated across agents using normalized title, summary, and location; all source agents/categories are retained and the highest reported severity is used.
- `--compact-prompts` reduces repeated context while preserving Good Points and structured findings.
- `--no-rubber-duck` disables rubber-duck for a run; otherwise rubber-duck is enabled by default.

Useful runtime environment variables:

- `COPILOT_CLI_PATH`: explicit path to Copilot CLI executable
- `GH_CLI_PATH`: explicit path to GitHub CLI executable
- `GH_AUTH_FALLBACK_ENABLED`: enable fallback from stdin token to `gh auth token` (`false` by default)
- `COPILOT_SDK_LOG_LEVEL`: Copilot CLI/SDK log level (`warning` by default; `warn` alias supported)

Auto-detected CLI paths are revalidated against trusted real-path directories:
`/usr/bin`, `/usr/local/bin`, `/bin`, `/opt/homebrew/bin`, `/usr/local/Cellar`, `/opt/homebrew/Cellar`, `/usr/local/Caskroom`, `/opt/homebrew/Caskroom`.

### Authentication (OAuth Device Flow)

This project expects CLI-based OAuth authentication by default.

```bash
gh auth login
gh copilot -- login
```

If your environment provides the standalone command, `copilot login` is also supported.

### Azure Skills Plugin and MCP

For Azure-related review and implementation work, prefer the official Azure Skills Plugin. In Copilot CLI, users who have not installed it should run:

```text
/plugin marketplace add microsoft/azure-skills
/plugin install azure@azure-skills
```

Use `/plugin update azure@azure-skills` to refresh an existing installation, then verify with `/skills` and `/mcp show`.

This repository also includes a project-level fallback copy of the official Azure skills in `.agents/skills/`, locked by `skills-lock.json`. WAF review skills require Microsoft Learn MCP grounding; if `/mcp show` does not list `microsoft-learn`, install it with:

```text
/plugin install microsoftdocs/mcp
```

The project MCP configuration is tracked in `.vscode/mcp.json` and includes:

- Azure MCP Server via `npx -y @azure/mcp@latest server start`
- Microsoft Learn MCP Server at `https://learn.microsoft.com/api/mcp`

## Security Runtime Notes

For production JVM runs that handle GitHub tokens, consider enabling these flags:

```bash
java --enable-preview \
	-XX:+DisableAttachMechanism \
	-XX:-HeapDumpOnOutOfMemoryError \
	-jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar run --repo owner/repo --all
```

- `-XX:+DisableAttachMechanism`: reduces token exposure via live attach diagnostics.
- `-XX:-HeapDumpOnOutOfMemoryError`: avoids automatic heap dumps that may contain token `String` values.
- If heap dumps are required operationally, store them in a protected location with strict access control and short retention.

## Copilot SDK License and Server-Side Use

This project depends on `com.github:copilot-sdk-java:1.0.0-beta-10-java.5`. The SDK artifact and upstream repository declare the MIT License, which is generally permissive for server-side integration, modification, and redistribution.

The MIT license covers the SDK code only. Calls to GitHub Copilot are still governed by the applicable GitHub Copilot product terms and the authenticated user's or organization's Copilot entitlement. Avoid designs that share one Copilot login across unrelated end users or repackage Copilot as a transparent SaaS backend without legal/product-term review.

## Development

```bash
# Build fat jar
./mvnw clean package

# Build native image (GraalVM required)
./mvnw clean package -Pnative

# Run tests
./mvnw test

# Run one test class
./mvnw test -Dtest=ModelConfigTest
```

### Test Troubleshooting

If tests fail with `NoSuchMethodError` for synthetic methods such as `access$0`, run a clean rebuild to clear stale class outputs:

```bash
./mvnw clean test
```

## Container Build Artifact (Reproducible)

Multi-stage Docker build is available for reproducible packaging:

```bash
docker build -t multi-agent-reviewer:local .
docker run --rm multi-agent-reviewer:local --version
```

- Build stage: Maven + digest-pinned OpenJDK 26 on Oracle Linux 9
- Runtime stage: digest-pinned OpenJDK 26 on Oracle Linux 9
- Built-in agents, templates, and skills are bundled into the runtime image
- Default entrypoint preserves project requirement: `--enable-preview`

## Optional Structured Logging Profile

Default logging is human-readable (`src/main/resources/logback.xml`) and emitted on `stderr` so report output on `stdout` stays machine-friendly.
If you need structured (JSON-like) logs for log shipping/aggregation:

```bash
java --enable-preview \
  -Dlogback.configurationFile=src/main/resources/logback-json.xml \
  -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar run --repo owner/repo --all
```

This mode keeps existing MDC keys (`event.category`, `event.action`) and token masking behavior.

## Report Archive Utility

To reduce artifact transfer/storage size for generated reports:

```bash
scripts/archive-reports.sh reports/<owner>/<repo>/<timestamp>
```

This creates a compressed `.tar.gz` archive for CI upload or retention.

## Architecture Decision Records (ADR)

Architecture decisions are documented in `docs/adr/`.

- ADR index: `docs/adr/README.md`
- Template: `docs/adr/0000-adr-template.md`
- Initial decisions:
  - `0001-custom-cli-parser.md`
  - `0002-micronaut-di.md`
  - `0003-virtual-thread-orchestration.md`
  - `0004-release-channels.md`
  - `0005-azure-skills-and-microsoft-learn-mcp.md`

## Operational Runbook

See `docs/runbook.md` for:

- Environment prerequisites and doctor check
- Build verification procedures
- Release and rollback procedures
- Structured logging configuration
- Troubleshooting guide
- Dependency audit procedures
- Security considerations and configuration precedence

## Release Channels

| Channel      | Tag Pattern                        | GitHub Release Type |
|--------------|------------------------------------|---------------------|
| Pre-release  | `v*-rc*`, `v*-alpha*`, `v*-beta*`  | Pre-release         |
| Stable       | `v*` (without rc/alpha/beta)       | Release             |

Pushing a tag triggers the `release.yml` workflow which builds artifacts, generates SBOM and checksums, and publishes a GitHub Release. See `docs/runbook.md` for the full release procedure and `docs/adr/0004-release-channels.md` for the design rationale.

## Documentation

- English: [README_en.md](./README_en.md)
- 日本語: [README_ja.md](./README_ja.md)
- Release Notes (EN): [RELEASE_NOTES_en.md](./RELEASE_NOTES_en.md)
- リリースノート (JA): [RELEASE_NOTES_ja.md](./RELEASE_NOTES_ja.md)
