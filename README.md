# Multi-Agent Code Reviewer

AI-powered parallel code review tool that orchestrates multiple specialized agents using the GitHub Copilot SDK for Java.

## Prerequisites

- Java: GraalVM 26 EA (Java 26, preview features enabled)
- Build: Maven 3.9+
- Auth: GitHub CLI (`gh`) and GitHub Copilot CLI (`github-copilot` or `copilot`)

This repository uses `.sdkmanrc` for local JDK alignment:

```bash
sdk env install
sdk env
```

## Quick Start

```bash
mvn clean package
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar run --repo owner/repo --all
```

## Latest Remediation

- 2026-03-18 (`v2026.03.18-auth`): Authentication policy aligned to OAuth device flow. Copilot runtime moved to logged-in user flow; `GITHUB_TOKEN`-centric guidance removed from CLI/runtime/docs.
- Validation: `mvn clean test` (743 tests passed, 0 failures)
- Release Notes: [RELEASE_NOTES_en.md](./RELEASE_NOTES_en.md), [RELEASE_NOTES_ja.md](./RELEASE_NOTES_ja.md)
- GitHub Release: https://github.com/anishi1222/multi-agent-code-reviewer/releases/tag/v2026.03.18-auth

## Architecture

Execution flow:

1. `ReviewApp` parses CLI arguments and dispatches commands.
2. `ReviewCommand` resolves target/agents/models/options.
3. `ReviewOrchestrator` runs each agent in parallel (virtual threads + structured concurrency).
4. `ReviewAgent` invokes Copilot SDK and generates per-agent review results.
5. `ReportGenerator` and `SummaryGenerator` build markdown outputs.

Main directories:

- `src/main/java/dev/logicojp/reviewer/cli`: command parsing and command handlers
- `src/main/java/dev/logicojp/reviewer/orchestrator`: parallel execution pipeline
- `src/main/java/dev/logicojp/reviewer/agent`: agent loading and prompt construction
- `templates/`: markdown templates used for report and summary generation
- `agents/`: built-in `.agent.md` definitions

## Configuration

Core configuration lives in `src/main/resources/application.yml`.

- `reviewer.execution.*`: parallelism, timeout, retry, buffer settings
- `reviewer.models.*`: review/report/summary model selection
- `reviewer.templates.*`: template directory and template filenames
- `reviewer.summary.*`: prompt sizing and fallback behavior
- `reviewer.skills.*`: global skill discovery and executor cache settings

Useful runtime environment variables:

- `COPILOT_CLI_PATH`: explicit path to Copilot CLI executable
- `GH_CLI_PATH`: explicit path to GitHub CLI executable

### Authentication (OAuth Device Flow)

This project expects CLI-based OAuth authentication by default.

```bash
gh auth login
gh copilot -- login
```

If your environment provides the standalone command, `copilot login` is also supported.

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

## Development

```bash
# Build fat jar
mvn clean package

# Build native image (GraalVM required)
mvn clean package -Pnative

# Run tests
mvn test

# Run one test class
mvn test -Dtest=ModelConfigTest
```

### Test Troubleshooting

If tests fail with `NoSuchMethodError` for synthetic methods such as `access$0`, run a clean rebuild to clear stale class outputs:

```bash
mvn clean test
```

## Container Build Artifact (Reproducible)

Multi-stage Docker build is available for reproducible packaging:

```bash
docker build -t multi-agent-reviewer:local .
docker run --rm multi-agent-reviewer:local --version
```

- Build stage: Maven + Java 26
- Runtime stage: Temurin JRE 26
- Default entrypoint preserves project requirement: `--enable-preview`

## Optional Structured Logging Profile

Default logging is human-readable (`src/main/resources/logback.xml`).
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

## Documentation

- English: [README_en.md](./README_en.md)
- 日本語: [README_ja.md](./README_ja.md)
- Release Notes (EN): [RELEASE_NOTES_en.md](./RELEASE_NOTES_en.md)
- リリースノート (JA): [RELEASE_NOTES_ja.md](./RELEASE_NOTES_ja.md)
