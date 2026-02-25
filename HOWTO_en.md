# Multi-Agent Code Reviewer

A parallel code review application using multiple AI agents with GitHub Copilot SDK for Java!

## Features

- **Parallel Multi-Agent Execution**: Simultaneous review from security, code quality, performance, and best practices perspectives
- **GitHub Repository / Local Directory Support**: Review source code from GitHub repositories or local directories
- **Custom Instructions**: Incorporate project-specific rules and guidelines into the review
- **Flexible Agent Definitions**: Define agents in GitHub Copilot format (.agent.md)
- **Agent Skill Support**: Define individual skills for agents to execute specific tasks
- **External Configuration Files**: Agent definitions can be swapped without rebuilding
- **Reusable Prompt Files**: Load GitHub Copilot format (.prompt.md) reusable prompts as supplementary instructions
- **LLM Model Selection**: Use different models for review, report generation, and summary generation
- **Structured Review Results**: Consistent format with Priority (Critical/High/Medium/Low)
- **Executive Summary Generation**: Management-facing report aggregating all review results
- **GraalVM Support**: Native binary generation via Native Image
- **Reasoning Model Support**: Automatic reasoning effort configuration for Claude Opus, o3, o4-mini, etc.
- **Multi-Pass Review**: Each agent performs multiple review passes and merges results for improved coverage
- **Content Sanitization**: Automatic removal of LLM preamble text and chain-of-thought leakage from review output
- **Default Model Externalization**: Configure the default model in `application.yml` (changeable without rebuild)
- **Instruction Sandboxing**: User-provided instructions are injected with structured boundaries and explicit system-instruction precedence
- **Token Lifetime Minimization**: Runtime token handling is narrowed to execution boundaries to reduce in-memory exposure time
- **DI-Consistent Service Construction**: `CopilotService` is unified to DI constructor usage (no no-arg path)

## Latest Remediation Status

All review findings from 2026-02-16 through 2026-02-25 have been fully addressed.

For the full change log, see [`RELEASE_NOTES.md`](RELEASE_NOTES.md).

- Latest: [v2026.02.24-notes](https://github.com/anishi1222/multi-agent-code-reviewer/releases/tag/v2026.02.24-notes) — includes 2026-02-25 PR #39 follow-up and 2026-02-24 remediation rollup
- Related: [v2026.02.20-notes](https://github.com/anishi1222/multi-agent-code-reviewer/releases/tag/v2026.02.20-notes), [v2026.02.20-reliability](https://github.com/anishi1222/multi-agent-code-reviewer/releases/tag/v2026.02.20-reliability)

## Operational Completion Check (2026-02-20)

- Last updated: 2026-02-25

- [x] All review findings addressed (including 7 WAF Reliability items)
- [x] Full test suite passing (0 failures)
- [x] Reliability fix PR merged: #76 (idle-timeout scheduler shutdown fallback)
- [x] Sensitive-pattern fallback sync completed (`LocalFileConfig`)
- [x] Executive summary filename aligned to naming convention (`executive_summary_yyyy-mm-dd-HH-mm-ss.md`)
- [x] Agent definition suspicious-pattern validation expanded to all prompt-injected fields
- [x] MCP auth header masking reinforced for `entrySet` / `values` stringification paths
- [x] `--token -` handling deferred to token-resolution boundary to minimize in-memory token lifetime
- [x] Merge-path redundant normalization/regex extraction removed (`findingKeyFromNormalized` reuse)
- [x] Near-duplicate detection narrowed with priority+title-prefix index before similarity matching
- [x] Local file reader pre-sizes `ByteArrayOutputStream` based on expected file size
- [x] Fallback summary whitespace regex switched to precompiled pattern
- [x] Structured `SECURITY_AUDIT` logging added for auth/trust/instruction-validation events
- [x] `--verbose` mode keeps Copilot SDK logger at `WARN`
- [x] Report output directories/files use owner-only POSIX permissions where supported
- [x] Weekly scheduled OWASP dependency audit workflow added
- [x] SHA-256 token hashing unified via shared utility (`TokenHashUtils`)
- [x] Orchestrator failure results unified (`ReviewResult.failedResults`)
- [x] `ReviewOrchestrator` nested collaborator/config types extracted to top-level package types
- [x] Scoped instruction loading refactored to explicit file loop + isolated IO handling
- [x] `ExecutionConfig` grouped settings/factory added to reduce positional constructor risk
- [x] Dead code removed (`ReviewResultPipeline.collectFromFutures`, unused `ReviewFindingSimilarity.WHITESPACE`)
- [x] `ReviewCommand` and `SkillCommand` unit tests added (normal/help/error)
- [x] `TemplateService` cache synchronization simplified with deterministic LRU behavior preserved
- [x] `SkillService` executor cache moved to Caffeine with eviction-time executor close
- [x] CLI token input abstracted from direct system I/O (`CliParsing.TokenInput`)
- [x] `ContentCollector` joined-content cache locking simplified
- [x] `AgentMarkdownParser` section parsing readability improved (removed iterable-cast trick)
- [x] `ReviewExecutionModeRunner` multi-pass start logging made execution-accurate
- [x] `GithubMcpConfig` map wrappers completed delegation methods (`isEmpty`/`containsValue`/`keySet`/`values`)
- [x] `ReviewResult` default timestamp handling simplified
- [x] `SkillExecutor` FQCN utility call removed (`ExecutorUtils` import)
- [x] Concurrency/threading design intent documentation reinforced (`CopilotService`, `ReviewOrchestrator`)
- [x] README EN/JA synchronized
- [x] `ApiCircuitBreaker` half-open probe introduced for gradual recovery
- [x] Circuit breaker isolated per operation type (review/summary/skill)
- [x] `ReviewAgent` `isRetryable()` added to retry only transient failures
- [x] `ReviewOrchestrator` checkpoint reuse removed; every run re-reviews latest source
- [x] `CopilotService.startClient()` unbounded wait eliminated (always bounded timeout)
- [x] `ResilienceConfig` + `application.yml` `reviewer.resilience` externalized resilience parameters
- [x] `SummaryGenerator` / `SkillExecutor` use dedicated CB and retry settings
- [x] Executive summary filename now always uses application invocation timestamp (directory timestamp aligned)
- [x] `SummaryGenerator` retries session establishment failures (not only send failures)
- [x] `ReviewAgent` multi-pass execution switched to per-pass session isolation for transient session failures
- [x] `BackoffUtils` retry strategy changed to Equal Jitter to guarantee minimum wait
- [x] `ApiCircuitBreaker` open duration now increases after repeated half-open probe failures
- [x] `ReviewOrchestrator` idle-timeout scheduler switched from single-thread to bounded pool
- [x] `ReviewOrchestrator` validates timeout hierarchy and logs misconfiguration warnings at startup
- [x] `ContentSanitizer` now decodes named HTML entities before sanitization to block XSS bypass
- [x] Intermediate checkpoint writes now use secure owner-only file permissions

## Release Update Procedure (Template)

Reference checklist: `reports/anishi1222/multi-agent-code-reviewer/documentation_sync_checklist_2026-02-17.md`

1. Add a new date section to `RELEASE_NOTES.md` (English-only consolidated file).
2. Create and push an annotated tag (for example: `vYYYY.MM.DD-notes`).
3. Publish a GitHub Release from the tag and include EN/JA notes summary.
4. Update `HOWTO_en.md` and `HOWTO_ja.md` with release references and URLs.

## Release Operation Checklist

- [ ] Add the new release date section to `RELEASE_NOTES.md`.
- [ ] Update release references in `HOWTO_en.md` and `HOWTO_ja.md`.
- [ ] Commit on a feature/docs branch and open a PR (do not push directly to `main`).
- [ ] Confirm required checks are green (Supply Chain Guard, Build and Test, Build Native Image, dependency-review, submit-maven).
- [ ] Merge the PR and fast-forward local `main`.
- [ ] Create and push an annotated tag: `git tag -a vYYYY.MM.DD-notes -m "Release notes update for YYYY-MM-DD"` then `git push origin vYYYY.MM.DD-notes`.
- [ ] Create GitHub Release from the tag with EN/JA summary notes.

## Requirements

- **GraalVM 26** (Java 26) — `.sdkmanrc` manages the version via SDKMAN
- GitHub Copilot CLI 0.0.407 or later
- GitHub token (for repository access)

## Supply Chain Policy

This repository enforces dependency and build hygiene in both Maven and GitHub Actions.

- Maven validate/build fails when checksum verification fails for Central artifacts.
- SNAPSHOT dependencies/plugins are blocked by Maven Enforcer.
- PR dependency review fails on vulnerability severity `moderate` or higher.
- PR dependency review denies these licenses: `GPL-2.0`, `GPL-3.0`, `AGPL-3.0`, `LGPL-2.1`, `LGPL-3.0`.
- CI workflow runs `validate`, `compile`, and `test` as required checks.
- Optional local/CI deep audit is available via `mvn -Psecurity-audit verify` (OWASP `dependency-check-maven`).

Recommended branch protection required checks:

- `Supply Chain Guard`
- `Build and Test`
- `Dependency Review`

### Installing GraalVM

Using SDKMAN:

```bash
sdk install java 26.ea.13-graal
sdk use java 26.ea.13-graal

# Auto-switch in project directory
cd multi-agent-reviewer  # GraalVM is automatically selected via .sdkmanrc
```

## Installation

```bash
# Clone the repository
git clone https://github.com/your-org/multi-agent-reviewer.git
cd multi-agent-reviewer

# Build (JAR file)
mvn clean package

# Build native image (optional)
mvn clean package -Pnative
```

## Usage

> Note: This project uses Java preview features. Run the JVM JAR with `--enable-preview`.

### Basic Usage

```bash
# Run review with all agents (GitHub repository)
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --repo owner/repository \
  --all

# Review a local directory
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --local ./my-project \
  --all

# Run only specific agents
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --repo owner/repository \
  --agents security,performance

# Explicitly specify LLM models
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --repo owner/repository \
  --all \
  --review-model gpt-4.1 \
  --summary-model claude-sonnet-4

# Review with custom instructions
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --local ./my-project \
  --all \
  --instructions ./my-instructions.md

# List available agents
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  list
```

### Run Options

| Option | Short | Description | Default |
|--------|-------|-------------|---------|
| `--repo` | `-r` | Target GitHub repository (exclusive with `--local`) | - |
| `--local` | `-l` | Target local directory (exclusive with `--repo`) | - |
| `--agents` | `-a` | Agents to run (comma-separated) | - |
| `--all` | - | Run all agents | false |
| `--output` | `-o` | Output base directory | `./reports` |
| `--agents-dir` | - | Additional agent definition directory | - |
| `--token` | - | GitHub token input (`-` for stdin only; direct value is rejected) | `$GITHUB_TOKEN` |
| `--parallelism` | - | Number of parallel executions | 4 |
| `--no-summary` | - | Skip summary generation | false |
| `--model` | - | Default model for all stages | - |
| `--review-model` | - | Model for review | Agent config |
| `--report-model` | - | Model for report generation | review-model |
| `--summary-model` | - | Model for summary generation | default-model |
| `--instructions` | - | Custom instruction file (can be specified multiple times) | - |
| `--no-instructions` | - | Disable automatic loading of custom instructions | false |
| `--no-prompts` | - | Disable loading `.github/prompts/*.prompt.md` | false |
| `--trust` | - | Trust and load instructions from the review target | false |
| `--help` | `-h` | Show help | - |
| `--version` | `-V` | Show version | - |
| `--verbose` | `-v` | Enable verbose logging (debug level) | - |

### List Subcommand

Displays a list of available agents. Additional directories can be specified with `--agents-dir`.

### Environment Variables

| Variable | Description | Default |
|----------|-------------|--------|
| `GITHUB_TOKEN` | GitHub auth token | - |
| `COPILOT_CLI_PATH` | Path to the Copilot CLI binary | Auto-detected from PATH |
| `COPILOT_START_TIMEOUT_SECONDS` | Copilot client start timeout (seconds) | 60 |
| `COPILOT_CLI_HEALTHCHECK_SECONDS` | CLI health check timeout (seconds) | 10 |
| `COPILOT_CLI_AUTHCHECK_SECONDS` | CLI auth check timeout (seconds) | 15 |

```bash
export GITHUB_TOKEN=your_github_token
```

### Feature Flags (Structured Concurrency)

Enable structured concurrency globally or for skills only using env vars or JVM properties:

```bash
# Global structured concurrency
export REVIEWER_STRUCTURED_CONCURRENCY=true

# Skills-only structured concurrency
export REVIEWER_STRUCTURED_CONCURRENCY_SKILLS=true

# JVM properties (alternative)
java -Dreviewer.feature-flags.structured-concurrency=true -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar run --repo owner/repo --all
java -Dreviewer.feature-flags.structured-concurrency-skills=true -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar skill --list
```

### Local Directory Review

You can review source code from a local directory even when you cannot access a GitHub repository.

```bash
# Review a local project
java -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --local /path/to/project \
  --all
```

Supported file extensions:
- JVM: `.java`, `.kt`, `.kts`, `.groovy`, `.scala`, `.clj`
- Web: `.js`, `.jsx`, `.ts`, `.tsx`, `.mjs`, `.cjs`, `.vue`, `.svelte`
- Systems: `.c`, `.cpp`, `.cc`, `.cxx`, `.h`, `.hpp`, `.rs`, `.go`, `.zig`
- Scripting: `.py`, `.rb`, `.php`, `.pl`, `.pm`, `.lua`, `.r`
- Shell: `.sh`, `.bash`, `.zsh`, `.fish`, `.ps1`, `.psm1`
- .NET: `.cs`, `.fs`, `.vb`
- Mobile: `.swift`, `.m`, `.mm`
- Data/Config: `.sql`, `.graphql`, `.gql`, `.proto`, `.yaml`, `.yml`, `.json`, `.toml`, `.xml`, `.properties`
- Build: `.gradle`, `.cmake`, `.makefile`
- Docs: `.md`, `.rst`, `.adoc`

> **Note**: Files up to 256 KB each are collected, with a 2 MB total limit. Files named `Makefile`, `Dockerfile`, `Rakefile`, or `Gemfile` (without extension) are also collected as source. Files that may contain sensitive information (`application-prod`, `.env`, `keystore`, etc.) are automatically excluded.

### Custom Instructions

You can incorporate project-specific rules and guidelines into the review.

```bash
# Specify instruction files
java -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --local ./my-project \
  --all \
  --instructions ./coding-standards.md \
  --instructions ./security-guidelines.md

# Disable automatic loading
java -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --local ./my-project \
  --all \
  --no-instructions
```

#### Auto-detected Instruction Files

When the `--trust` flag is specified, custom instructions are automatically loaded from the review target directory at the following paths (in order of priority):

1. `.github/copilot-instructions.md`
2. `.copilot/instructions.md`
3. `copilot-instructions.md`
4. `INSTRUCTIONS.md`
5. `.instructions.md`
6. `.github/instructions/*.instructions.md` (GitHub Copilot per-scope instructions)
7. `.github/prompts/*.prompt.md` (GitHub Copilot reusable prompt files)

#### GitHub Copilot Per-scope Instructions

The `.github/instructions/*.instructions.md` format used by GitHub Copilot is supported.  
YAML frontmatter can specify `applyTo` (target file glob pattern) and `description`.

```markdown
---
applyTo: '**/*.java'
description: 'Java coding standards'
---
- Use UpperCamelCase for class names
- Write Javadoc for all public methods
- Prefer immutable objects where possible
```

Multiple instruction files can be placed in the `.github/instructions/` directory:

```
.github/
├── copilot-instructions.md          # Repository-wide instructions
├── instructions/
│   ├── java.instructions.md         # Java-specific rules
│   ├── typescript.instructions.md   # TypeScript-specific rules
│   └── security.instructions.md     # Security guidelines
└── prompts/
    ├── java-junit.prompt.md         # JUnit test generation prompt
    └── java-docs.prompt.md          # Javadoc generation prompt
```

#### GitHub Copilot Reusable Prompt Files

The `.github/prompts/*.prompt.md` format used by GitHub Copilot is supported.
Prompt files are injected into the agent system prompt as supplementary instructions.

YAML frontmatter can specify `description` and `agent` (target agent).

```markdown
---
description: 'JUnit 5 best practices'
agent: 'agent'
---
# JUnit 5 Best Practices
Your goal is to help write effective unit tests...
```

Use `--no-prompts` to disable loading prompt files.

### Output Example

Reports are generated under the output base directory in a subdirectory corresponding to the review target plus the CLI invocation timestamp.

**GitHub repository** (`--repo owner/repository`):
```
./reports/
└── owner/
    └── repository/
      ├── executive_summary_2026-02-19-18-38-42.md
      └── 2026-02-19-18-38-42/
        ├── security-report.md
        ├── code-quality-report.md
        ├── performance-report.md
        └── best-practices-report.md
```

**Local directory** (`--local /path/to/my-project`):
```
./reports/
└── my-project/
  ├── executive_summary_2026-02-19-18-38-42.md
  └── 2026-02-19-18-38-42/
    ├── security-report.md
    ├── code-quality-report.md
    ├── performance-report.md
    └── best-practices-report.md
```

Use `-o` / `--output` to change the output base directory (default: `./reports`).

## Configuration

Customize application behavior via `application.yml`.

```yaml
reviewer:
  copilot:
    cli-path: ${COPILOT_CLI_PATH:}                   # Optional explicit Copilot CLI path
    github-token: ${GITHUB_TOKEN:}                  # Optional GitHub token used at startup
    healthcheck-seconds: ${COPILOT_CLI_HEALTHCHECK_SECONDS:10} # CLI --version timeout
    authcheck-seconds: ${COPILOT_CLI_AUTHCHECK_SECONDS:15}     # CLI auth status timeout
    start-timeout-seconds: ${COPILOT_START_TIMEOUT_SECONDS:60} # Copilot client startup timeout
  agents:
    directories:                      # Agent definition search directories
      - ./agents
      - ./.github/agents
  execution:
    parallelism: 4              # Default parallel execution count
    review-passes: 3            # Number of review passes per agent (multi-pass review)
    orchestrator-timeout-minutes: 200  # Orchestrator timeout (minutes)
    agent-timeout-minutes: 20   # Agent timeout (minutes)
    idle-timeout-minutes: 5     # Idle timeout (minutes) — auto-terminate when no events
    skill-timeout-minutes: 20   # Skill timeout (minutes)
    summary-timeout-minutes: 20 # Summary timeout (minutes)
    gh-auth-timeout-seconds: 30 # GitHub auth timeout (seconds)
    max-retries: 2              # Max retry count on review failure
    checkpoint-directory: reports/.checkpoints # Intermediate checkpoint output directory (write-only, no reuse)
    summary:
      max-content-per-agent: 50000     # Max characters per agent content for summary prompt
      max-total-prompt-content: 200000 # Max total prompt characters for summary generation
      fallback-excerpt-length: 180     # Excerpt length used by fallback summary formatter
  feature-flags:
    structured-concurrency: false       # Enable Structured Concurrency
    structured-concurrency-skills: false # Enable Structured Concurrency for skills only
  local-files:
    max-file-size: 262144               # Max local file size (256KB)
    max-total-size: 2097152             # Max total local file size (2MB)
  templates:
    directory: templates              # Template directory
    output-constraints: output-constraints.md  # Output constraints (CoT suppression, language)
  skills:
    filename: SKILL.md                    # Skill definition filename
    directory: .github/skills             # Skill definitions directory
  mcp:
    github:
      type: http
      url: https://api.githubcopilot.com/mcp/
      tools:
        - "get_file_contents"
        - "search_code"
        - "list_commits"
        - "get_commit"
      auth-header-name: Authorization
      auth-header-template: "Bearer {token}"
  models:
    default-model: claude-opus-4.6    # Default for all models (changeable without rebuild)
    review-model: GPT-5.3-Codex      # Model for review
    report-model: claude-opus-4.6-fast  # Model for report generation
    summary-model: claude-sonnet-4.5 # Model for summary generation
    reasoning-effort: high           # Reasoning effort level (low/medium/high)
```

### External Configuration Override

When running as a fat JAR or Native Image, you can override the built-in `application.yml` without rebuilding.

**Place `application.yml` in the working directory:**

```bash
# Fat JAR
cp application.yml ./
java -jar multi-agent-code-reviewer.jar

# Native Image
cp application.yml ./
./multi-agent-code-reviewer
```

**Or specify an explicit path via system property:**

```bash
# Fat JAR
java -Dmicronaut.config.files=/path/to/application.yml -jar multi-agent-code-reviewer.jar

# Native Image
./multi-agent-code-reviewer -Dmicronaut.config.files=/path/to/application.yml
```

**Or override individual properties via environment variables:**

```bash
export REVIEWER_MODELS_DEFAULT_MODEL=gpt-4
export REVIEWER_EXECUTION_PARALLELISM=8
java -jar multi-agent-code-reviewer.jar
```

**Or use environment-specific profile files (`application-dev.yml`, `application-ci.yml`, `application-prod.yml`):**

```bash
# Development profile
java -Dmicronaut.environments=dev -jar multi-agent-code-reviewer.jar

# CI profile
java -Dmicronaut.environments=ci -jar multi-agent-code-reviewer.jar

# Production profile
java -Dmicronaut.environments=prod -jar multi-agent-code-reviewer.jar
```

> **Note:** The external `application.yml` only needs to contain the properties you want to override — you do not need to copy the entire file.

Configuration is resolved in the following priority order (highest first):

1. CLI options (`--review-model`, `--parallelism`, etc.)
2. System properties (`-Dreviewer.models.default-model=...`)
3. Environment variables (`REVIEWER_MODELS_DEFAULT_MODEL=...`)
4. External `application.yml` (working directory or `-Dmicronaut.config.files`)
5. Built-in `application.yml` (inside the JAR / Native Image)
6. Hardcoded defaults in record constructors

### Model Configuration Priority

Models are resolved in the following priority order:

1. **Individual model settings** (`review-model`, `report-model`, `summary-model`) take highest priority
2. **Default model** (`default-model`) — fallback when no individual setting is specified
3. **Hardcoded constant** (`ModelConfig.DEFAULT_MODEL`) — final fallback when nothing is configured in YAML

### Multi-Pass Review

Each agent can perform multiple review passes, merging the results to catch issues that a single pass might miss.

- **`review-passes`** controls the number of review passes per agent (default: `1`)
- All passes are submitted concurrently to the Virtual Thread pool, with `parallelism` controlling the maximum concurrent tasks
- Example: 4 agents × 2 passes = 8 tasks queued in parallel; with `parallelism=4`, up to 4 run concurrently
- Duplicate findings within the same agent are aggregated into a single deduplicated report
- Aggregated output can include pass-detection information to preserve traceability for repeated findings
- If some passes fail, results from the successful passes are still used
- The executive summary is generated from the merged, multi-pass results

### Retry Behavior

When an agent review fails due to timeout or empty response, it is automatically retried.

- **Timeout is per-attempt, not cumulative**: `agent-timeout-minutes` applies independently to each attempt. For example, with `agent-timeout-minutes: 20` and `max-retries: 2`, the agent will try up to 3 times (initial + 2 retries) × 20 minutes each = up to 60 minutes total
- **Returns immediately on success**: If any attempt succeeds, remaining retries are skipped
- **Set `max-retries: 0`** to disable retries
- Retried on: timeout (`TimeoutException`), empty response, SDK exceptions

### Agent Directories

The following directories are automatically searched:

- `./agents/` - Default directory
- `./.github/agents/` - Alternative directory

Additional directories can be specified with the `--agents-dir` option.

### Agent Definition File (`.agent.md`)

Following the GitHub Copilot Custom Agent format, all section names are in English. Recognized sections:

| Section | Description |
|---------|-------------|
| `## Role` | Agent role / system prompt |
| `## Instruction` | Review instruction prompt |
| `## Focus Areas` | List of review focus areas |
| `## Output Format` | Output format specification |

In `Instruction`, you can use placeholders: `${repository}`, `${displayName}`, `${focusAreas}`.

```markdown
---
name: security
description: "Security Review"
model: claude-sonnet-4
---

# Security Review Agent

## Role

You are a security-focused code reviewer.
As an experienced security engineer, you identify vulnerabilities in the code.

## Instruction

Please perform a code review of the following GitHub repository.

**Target Repository**: ${repository}

Analyze all source code in the repository and identify issues from your specialty perspective (${displayName}).

Pay special attention to the following points:
${focusAreas}

## Focus Areas

- SQL Injection
- XSS Vulnerabilities
- Authentication/Authorization Issues

## Output Format

Please output the review results in the following format.
```

### Default Agents

| Agent | Description |
|-------|-------------|
| `security` | Security vulnerabilities, authentication/authorization, secrets |
| `code-quality` | Readability, complexity, SOLID principles, tests |
| `performance` | N+1 queries, memory leaks, algorithm efficiency |
| `best-practices` | Language/framework-specific best practices |
| `waf-reliability` | Azure WAF Reliability — retry, circuit breaker, timeout, disaster recovery |
| `waf-security` | Azure WAF Security — managed identity, Key Vault, zero trust, RBAC |
| `waf-cost-optimization` | Azure WAF Cost Optimization — SKU selection, autoscaling, idle resources |
| `waf-operational-excellence` | Azure WAF Operational Excellence — IaC, CI/CD, structured logging, Application Insights |
| `waf-performance-efficiency` | Azure WAF Performance Efficiency — caching, async messaging, connection pooling |

## Review Result Format

Each finding is output in the following format:

| Field | Description |
|-------|-------------|
| Title | Concise title describing the issue |
| Priority | Critical / High / Medium / Low |
| Summary | Description of the problem |
| Impact if Not Fixed | Risk if left unaddressed |
| Location | File path and line numbers |
| Recommended Action | Specific fix (including code examples) |
| Benefit | Improvement from the fix |

### Priority Criteria

- **Critical**: Security vulnerabilities, data loss, production outages. Immediate action required
- **High**: Serious bugs, performance issues. Prompt action needed
- **Medium**: Code quality issues, reduced maintainability. Address in planned manner
- **Low**: Style issues, minor improvement suggestions. Fix when time permits

## Agent Skill

Agents can have individual skills defined to execute specific tasks.

### skill Subcommand

```bash
# List available skills
java -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  skill --list

# Execute a skill
java -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  skill sql-injection-check \
  --param target=owner/repository

# Execute a skill with parameters
java -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  skill secret-scan \
  --param repository=owner/repository \
  --model claude-sonnet-4
```

### skill Options

| Option | Short | Description | Default |
|--------|-------|-------------|---------|
| `--list` | - | List available skills | - |
| `--param` | `-p` | Parameter (key=value format) | - |
| `--token` | - | GitHub token input (`-` for stdin only; direct value is rejected) | `$GITHUB_TOKEN` |
| `--model` | - | LLM model to use | default-model |
| `--agents-dir` | - | Agent definitions directory | - |

### Skill Definition (`SKILL.md` format)

Skills are defined as standalone `SKILL.md` files placed in `.github/skills/<skill-name>/` directories. Each skill is a separate directory containing a `SKILL.md` file with YAML frontmatter and a Markdown body.

```
.github/skills/
├── sql-injection-check/
│   └── SKILL.md
├── secret-scan/
│   └── SKILL.md
├── complexity-analysis/
│   └── SKILL.md
└── ...
```

#### SKILL.md Format

```markdown
---
name: secret-scan
description: Detects hardcoded secrets in code such as API keys, tokens, passwords, private keys, and cloud credentials.
metadata:
  agent: security
---

# Secret Scan

Analyze the following code for secret leakage.

**Target Repository**: ${repository}

Look for these patterns:
- API keys, tokens
- Passwords
- Private keys
- Database connection strings
- AWS/Azure/GCP credentials

Report discovered secrets and recommend proper management approaches.
```

| Field | Description |
|-------|-------------|
| `name` | Skill name (defaults to directory name if omitted) |
| `description` | Skill description |
| `metadata.agent` | Agent to bind this skill to (e.g. `security`, `code-quality`). If omitted, available to all agents |
| Body | The prompt template. Supports `${paramName}` placeholders substituted at runtime |

## GraalVM Native Image

To build as a native binary:

```bash
# Build native image
mvn clean package -Pnative

# Run
./target/review run --repo owner/repository --all
```

### Generating Reflection Configuration (First Build / After Dependency Updates)

The Copilot SDK internally uses Jackson Databind for JSON-RPC communication. Because GraalVM Native Image restricts reflection, reflection configuration must be registered in advance for the SDK's internal DTO classes.

If the configuration is missing, the Native Image binary will time out when communicating with the Copilot CLI (this does not occur with the FAT JAR). This happens because Jackson performs JSON serialization/deserialization via reflection, and in a Native Image environment, metadata for unregistered classes is inaccessible. Exceptions are silently caught inside the SDK, leaving `CompletableFuture` instances permanently incomplete.

Use the GraalVM **tracing agent** to automatically collect the required reflection information from an actual execution.

```bash
# 1. Build the FAT JAR first
mvn clean package -DskipTests

# 2. Run with the tracing agent to auto-generate reflection configuration
#    Use config-merge-dir to merge with existing configuration
java -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image \
     -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
     run --repo owner/repository --all

# 3. Verify the generated configuration
ls src/main/resources/META-INF/native-image/
# reflect-config.json, resource-config.json, proxy-config.json, etc. are generated/updated

# 4. Rebuild as Native Image
mvn clean package -Pnative -DskipTests
```

> **Note**: Use `config-merge-dir` instead of `config-output-dir` to merge with existing configurations (e.g., Logback) rather than overwriting them. Also, run all agents (security, performance, etc.) to exercise all code paths and generate complete configuration.

> **Tip**: Re-run the tracing agent whenever you update dependencies such as the Copilot SDK or Jackson.

## Architecture

```mermaid
flowchart TB
    %% ── CLI ──
    ReviewApp["ReviewApp
    (Entry Point)"]
    ReviewApp --> ReviewCommand
    ReviewApp --> ListAgentsCommand
    ReviewApp --> SkillCommand

    %% ── Review flow ──
    subgraph ReviewFlow["Review Flow"]
        direction TB
        ReviewCommand --> CustomInstructionLoader
        ReviewCommand --> ReviewOrchestratorFactory
        ReviewOrchestratorFactory --> ReviewOrchestrator

        subgraph Orchestrator["ReviewOrchestrator"]
            direction TB
            ReviewAgent["ReviewAgent
            Per-agent review + timeout"]
            ContentSanitizer["ContentSanitizer
            LLM preamble / CoT removal"]
            ReviewResultMerger["ReviewResultMerger
            Multi-pass deduplication"]

            ReviewAgent --> ContentSanitizer
            ReviewAgent --> ReviewResultMerger
        end

        ReviewCommand --> ReportService
        ReportService --> ReportGenerator
        ReportService --> SummaryGenerator["SummaryGenerator
        AI-powered summary"]
    end

    %% ── List flow ──
    ListAgentsCommand --> AgentService

    %% ── Skill flow ──
    subgraph SkillFlow["Skill Flow"]
        direction TB
        SkillCommand --> SkillService
        SkillService --> SkillRegistry
        SkillService --> SkillExecutor["SkillExecutor
        Structured Concurrency"]
    end

    %% ── Review Target ──
    subgraph Target["Review Target (sealed)"]
        direction LR
        GitHubTarget
        LocalTarget --> LocalFileProvider
    end
    ReviewOrchestrator --> Target

    %% ── Shared services ──
    subgraph Shared["Shared Services"]
        direction LR
        CopilotService["CopilotService
        SDK lifecycle management"]
        TemplateService
        SecurityAuditLogger["SecurityAuditLogger
        Structured security audit logging"]
    end

    ReviewCommand --> CopilotService

    %% ── External ──
    subgraph External["External"]
        direction LR
        CopilotAPI["GitHub Copilot API
        (LLM)"]
        GitHubMCP["GitHub MCP Server"]
    end

    CopilotService --> CopilotAPI
    ReviewAgent -.-> CopilotAPI
    ReviewAgent -.-> GitHubMCP
    SkillExecutor -.-> CopilotAPI
    SkillExecutor -.-> GitHubMCP
    SummaryGenerator -.-> CopilotAPI
```

## Template Customization

Report and summary formats are externalized in template files.

### Template Directory

By default, templates in the `templates/` directory are used.

```
templates/
├── agent-focus-areas-guidance.md   # Agent focus areas guidance
├── summary-system.md              # Summary generation system prompt
├── summary-prompt.md              # Summary generation user prompt
├── summary-result-entry.md        # Summary result entry (success)
├── summary-result-error-entry.md  # Summary result entry (failure)
├── default-output-format.md       # Default output format
├── output-constraints.md          # Output constraints (CoT suppression, language)
├── report.md                      # Individual report template
├── report-link-entry.md           # Report link entry
├── executive-summary.md           # Executive summary template
├── fallback-summary.md            # Fallback summary template
├── fallback-agent-row.md          # Fallback table row
├── fallback-agent-success.md      # Fallback success detail
├── fallback-agent-failure.md      # Fallback failure detail
├── local-review-content.md        # Local review content
├── local-review-result-request.md # Local review result request
├── local-source-header.md         # Local source header
├── custom-instruction-section.md  # Custom instruction section
└── review-custom-instruction.md   # Review custom instruction
```

### Template Configuration

You can customize template paths in `application.yml`:

```yaml
reviewer:
  templates:
    directory: templates                    # Template directory
    default-output-format: default-output-format.md
    output-constraints: output-constraints.md  # Output constraints (CoT suppression, language)
    report: report.md
    local-review-content: local-review-content.md
    summary:
      system-prompt: summary-system.md       # Summary system prompt
      user-prompt: summary-prompt.md         # Summary user prompt
      executive-summary: executive-summary.md # Executive summary
    fallback:
      summary: fallback-summary.md           # Fallback summary
```

### Placeholders

Templates support `{{placeholder}}` format placeholders. See each template file for available placeholders.

## Project Structure

The following tree is synchronized with the current source layout.

```
multi-agent-reviewer/
├── pom.xml                              # Maven configuration
├── .sdkmanrc                            # SDKMAN GraalVM configuration
├── .github/
│   ├── workflows/                       # CI/CD workflows
│   │   ├── ci.yml                       # Build and test
│   │   ├── codeql.yml                   # CodeQL analysis
│   │   ├── dependency-audit.yml         # Weekly OWASP dependency audit
│   │   ├── dependency-review.yml        # PR dependency review
│   │   └── scorecard.yml               # OpenSSF Scorecard
│   └── skills/                          # Skill definitions (SKILL.md format)
│       ├── sql-injection-check/
│       ├── secret-scan/
│       └── ...
├── agents/                              # Agent definitions (.agent.md format)
│   ├── security.agent.md
│   ├── code-quality.agent.md
│   ├── performance.agent.md
│   ├── best-practices.agent.md
│   ├── waf-reliability.agent.md
│   ├── waf-security.agent.md
│   ├── waf-cost-optimization.agent.md
│   ├── waf-operational-excellence.agent.md
│   └── waf-performance-efficiency.agent.md
├── templates/                           # Template files
│   ├── summary-system.md
│   ├── summary-prompt.md
│   ├── report.md
│   └── ...
└── src/main/java/dev/logicojp/reviewer/
    ├── LogbackLevelSwitcher.java        # Runtime log level switching
    ├── ReviewApp.java                   # CLI entry point
    ├── agent/
    │   ├── AgentConfig.java             # Config model
    │   ├── AgentConfigLoader.java       # Config loader
    │   ├── AgentMarkdownParser.java     # .agent.md parser
    │   ├── AgentPromptBuilder.java      # Agent prompt builder
    │   ├── ContentCollector.java        # Review content collector
    │   ├── ReviewAgent.java             # Review agent
    │   ├── ReviewContext.java           # Shared review context
    │   └── SessionEventException.java   # Session event exception
    ├── cli/
    │   ├── CliOutput.java               # CLI output utilities
    │   ├── CliParsing.java              # CLI option parsing
    │   ├── CliValidationException.java  # CLI input validation exception
    │   ├── ExitCodes.java               # Exit code constants
    │   ├── ListAgentsCommand.java       # list subcommand
    │   ├── ReviewCommand.java           # run subcommand
    │   └── SkillCommand.java            # skill subcommand
    ├── config/
    │   ├── CopilotConfig.java         # Copilot CLI/startup timeout config
    │   ├── ConfigDefaults.java          # Shared default normalization helpers
    │   ├── ExecutionConfig.java         # Execution config
    │   ├── GithubMcpConfig.java         # GitHub MCP config
    │   ├── ModelConfig.java             # LLM model config
    │   ├── ResilienceConfig.java         # Resilience config (CB, retry, backoff)
    │   ├── ReviewerConfig.java          # Unified config (agents, local-files, skills)
    │   └── TemplateConfig.java          # Template config
    ├── instruction/
    │   ├── CustomInstruction.java       # Custom instruction model
    │   ├── CustomInstructionLoader.java # Instruction loader
    │   └── CustomInstructionSafetyValidator.java # Instruction safety validator
    ├── orchestrator/
    │   ├── ReviewOrchestrator.java      # Parallel execution control
    │   ├── ReviewOrchestratorFactory.java # Orchestrator factory
    │   └── ReviewResultMerger.java      # Multi-pass result merger
    ├── report/
    │   ├── ContentSanitizer.java        # LLM preamble / CoT removal
    │   ├── FindingsExtractor.java       # Findings extraction
    │   ├── ReportGenerator.java         # Individual report generation
    │   ├── ReportService.java           # Report generation service
    │   ├── ReviewResult.java            # Result model
    │   └── SummaryGenerator.java        # Summary generation
    ├── service/
    │   ├── AgentService.java            # Agent management
    │   ├── CopilotCliException.java     # Shared Copilot CLI exception type
    │   ├── CopilotService.java          # Copilot SDK integration
    │   ├── SkillService.java            # Skill management
    │   └── TemplateService.java         # Template loading
    ├── skill/
    │   ├── SkillDefinition.java         # Skill definition model
    │   ├── SkillExecutor.java           # Skill executor
    │   ├── SkillMarkdownParser.java     # Skill markdown parser
    │   └── SkillRegistry.java           # Skill registry
    ├── target/
    │   ├── LocalFileProvider.java       # Local file collector
    │   └── ReviewTarget.java            # Review target (sealed interface)
    └── util/
        ├── ApiCircuitBreaker.java       # Lightweight circuit breaker (half-open support)
        ├── BackoffUtils.java            # Retry backoff + jitter utility
        ├── CliPathResolver.java         # CLI path resolver
        ├── FrontmatterParser.java       # YAML frontmatter parser
        ├── GitHubTokenResolver.java     # GitHub token resolution
        ├── SecurityAuditLogger.java     # Structured security audit logging
        ├── StructuredConcurrencyUtils.java # Structured Concurrency utilities
        └── TokenHashUtils.java          # SHA-256 token hash utility

└── src/main/resources/
    ├── defaults/
    │   ├── ignored-directories.txt      # Default ignored directories for local collection
    │   ├── source-extensions.txt        # Default source extensions
    │   ├── sensitive-file-patterns.txt  # Default sensitive filename patterns
    │   └── sensitive-extensions.txt     # Default sensitive extensions
    └── safety/
        └── suspicious-patterns.txt      # Prompt-injection suspicious pattern definitions
```

## License

MIT License
