# Multi-Agent Code Reviewer

A parallel code review application using multiple AI agents with GitHub Copilot SDK for Java!

## Features

- **Parallel Multi-Agent Execution**: Simultaneous review from security, code quality, performance, and best practices perspectives
- **GitHub Repository / Local Directory Support**: Review source code from GitHub repositories or local directories
- **Flexible Agent Definitions**: Define agents in GitHub Copilot format (.agent.md)
- **Agent Skill Support**: Agent-bound SKILL definitions are applied as mandatory criteria during standard and rubber-duck reviews
- **External Configuration Files**: Agent definitions can be swapped without rebuilding
- **Traceable Session ID Naming**: Review session IDs use `{agent}_{invocationTimestamp}`
- **LLM Model Selection**: Use different models for review, report generation, and summary generation
- **Structured Review Results**: Consistent format with Priority (Critical/High/Medium/Low)
- **Executive Summary Generation**: Management-facing report with deterministic cross-agent finding deduplication
- **GraalVM Support**: Native binary generation via Native Image
- **Reasoning Model Support**: Automatic reasoning effort configuration for Claude Opus, o3, o4-mini, etc.
- **Content Sanitization**: Automatic removal of LLM preamble text and chain-of-thought leakage from review output
- **Default Model Externalization**: Configure the default model in `application.yml` (changeable without rebuild)
- **Token Lifetime Minimization**: Runtime token handling is narrowed to execution boundaries to reduce in-memory exposure time
- **DI-Consistent Service Construction**: `CopilotService` is unified to DI constructor usage (no no-arg path)
- **Rubber-Duck Peer Discussion Mode**: Two-model dialogue per agent — a primary model and a peer model debate findings across configurable rounds before producing a synthesized final review
- **Optional Standard-Review Passes**: `reviewer.execution.concurrency.review-passes` emits one report per pass without an implicit merge stage
- **Enforced Ports & Adapters Boundaries**: Five named layers plus a layer-zero composition root are checked from compiled bytecode during `mvn verify`
- **Typed Agent Trust + Sink Redaction**: Agent-definition provenance selects the validation policy, while both Logback sinks mask secrets
- **Official Azure Skills + MCP Support**: Project-level fallback copy of `microsoft/azure-skills`, Azure MCP configuration, and WAF skills grounded through Microsoft Learn MCP

## Release Status

The current repository tree is published as
[`v2026.08.10-layered-architecture`](https://github.com/anishi1222/multi-agent-code-reviewer/releases/tag/v2026.08.10-layered-architecture);
the release details are recorded in [RELEASE_NOTES_en.md](./RELEASE_NOTES_en.md).

- 2026-08-10 ([v2026.08.10-layered-architecture](https://github.com/anishi1222/multi-agent-code-reviewer/releases/tag/v2026.08.10-layered-architecture)): Layered-architecture completion — completed the five-layer Ports & Adapters rewrite with
  three layer-zero entry/wiring files, 8 inbound port interfaces, 15 outbound port interfaces, and
  mechanically enforced zero-cycle boundaries; preserved the CLI/review/report contracts while restoring
  standard multi-pass output through the live `reviewer.execution.concurrency.review-passes` key; upgraded
  to Java 28, Micronaut 5.1.0, and `copilot-sdk-java` 1.0.8; added typed agent-definition provenance,
  stricter repository-supplied policy, Unicode/default-ignorable validation, Logback sink redaction, and
  pinned model-family coverage; and validated the shaded JAR and GraalVM 25 Native Image packaging paths.
- 2026-07-21 ([v2026.07.21-review-contract](https://github.com/anishi1222/multi-agent-code-reviewer/releases/tag/v2026.07.21-review-contract)): Streamlined review execution and token usage — removed multi-pass/shared-session/checkpoint/merge behavior; kept rubber-duck enabled by default for additional perspectives; added compact prompt budgets and CLI controls; applied safe agent-bound SKILL criteria to standard/rubber-duck reviews; added deterministic cross-agent Executive Summary deduplication; and required evidence-based Good Points from every runtime/custom agent.
- 2026-06-24 (v2026.06.24-refactor-seams-tests): Refactoring seam extraction and direct test coverage — split rubber-duck dialogue orchestration (`RubberDuckPromptBuilder`, `RubberDuckDialogueRunner`, `SdkRubberDuckSessionFactory`), review pass/session execution (`ReviewPassRunner`, `ReviewSessionExecutor`), summary AI transport/output writing (`AiSummaryClient`, `SummaryReportWriter`), review CLI option model (`ReviewOptions`, `ReviewTargetSelection`, `ReviewAgentSelection`), agent definition parsing (`AgentFrontmatterMapper`, `AgentSectionParser`), template repository loading (`TemplateRepository`), and GitHub token resolution (`TokenInputReader`, `GhCliLocator`, `GhAuthTokenProvider`) into focused collaborators. Added direct unit tests for the extracted seams, fixed hybrid local-review source propagation, and hardened `gh auth token` stdout/stderr handling with bounded stream collection. Verified with JDK 27 EA full clean test suite (871 tests, 0 failures).
- 2026-06-24 (v2026.06.24-dependency-ci-hardening): Dependency, CI, and module-structure hardening — upgraded the runtime stack to `copilot-sdk-java` 1.0.1, Micronaut 5.1.2, JDK 27 JVM builds, and GraalVM 25.0.3 native-image builds; added native-image reachability metadata for Logback/Copilot SDK execution; added a self-managed JDK 27 `Dependency Submission` workflow to replace the GitHub-managed `submit-maven` workflow; hardened OWASP Dependency Audit with NVD API key propagation, cache restore, retry/backoff, and direct `dependency-check:check` execution; constrained transitive Jackson 2.x dependencies with `com.fasterxml.jackson:jackson-bom:2.22.0` while retaining the Jackson 3 `tools.jackson` override; resolved all open Dependabot `jackson-databind` alerts; and synchronized README / release notes / ADR module trees with the current workflow, skill, MCP, native-image, and Java package layout.
- 2026-06-08 (v2026.06.08-agent-model-defaults): Agent model defaults documentation sync — removed model pins from GitHub Copilot custom-agent configuration references, clarified that review model overrides should be supplied via CLI/configuration rather than `.github/agents` frontmatter, refreshed README model examples to the current runtime defaults (`claude-sonnet-4.6`, `gpt-5.3-codex`, `claude-opus-4.7-xhigh`), and updated the documented Copilot SDK dependency to `1.0.0-beta-10-java.5`.
- 2026-05-28 (v2026.05.28-azure-skills-mcp): Azure Skills and MCP configuration — added official `microsoft/azure-skills` project skills under `.agents/skills/`, tracked them in `skills-lock.json`, configured Azure MCP and Microsoft Learn MCP in `.vscode/mcp.json`, rewrote WAF skills to require Microsoft Learn MCP grounding, added setup instructions for unconfigured Copilot CLI users, and documented Copilot SDK MIT licensing plus Copilot service-term boundaries for server-side use.
- 2026-05-28 (v2026.05.28-ci-release-hardening): CI and release hardening — changed GitHub Actions workflow defaults to `permissions: {}`, granted `contents: read` only to build jobs and `contents: write` only to the release-publishing job, aligned the release workflow JDK with compiler release 27, removed unnecessary release checkout by setting `GH_REPO`, eliminated duplicate OWASP Dependency Check execution from `Supply Chain Guard` so deep auditing is owned by `Dependency Audit`, switched CodeQL Java/Kotlin analysis to `build-mode: none`, fixed dependency submission permissions, refreshed Actions/Maven plugin dependencies, and updated `CopilotCliPathResolver` tests for the latest constructor API.
- 2026-05-15 (v2026.05.15-runtime-compat): Runtime compatibility and report-accuracy fixes — aligned structured concurrency utilities with the JDK 27 `StructuredTaskScope<T, R, R_X>` signature while preserving await-all behavior, removed macOS-specific `/bin/true` test dependency, expanded trusted CLI real-path allowlist for Homebrew `Cellar`/`Caskroom` (fixing both `gh auth token` fallback and `copilot` discovery), normalized Copilot SDK log-level mapping (`warn` -> `warning`), fixed deny-all permission serialization by returning typed `REJECTED`, and excluded "no findings" placeholder blocks from findings aggregation. Verified with `mvn clean package` (830 tests passing).
- 2026-05-15 (v2026.05.15-sdk-refactor): Copilot SDK for Java native-feature alignment refactor (Phases 1–3) — typed MCP server handoff with `McpHttpServerConfig` instead of `Map<String, Object>` casts (Phase 1, commit `96df653`); replaced subprocess-based `CopilotCliHealthChecker` with SDK `client.getStatus()` / `getAuthStatus()` based `CopilotHealthProbe`, set `setAutoRestart(true)` and env-driven `setLogLevel(...)`, rewrote `DoctorCommand` on top of the SDK probe (Phase 2, commit `b657c64` + corrections in `a9310fc`); migrated `ReviewSessionMessageSender`, `ReviewAgent`, and `RubberDuckDialogueExecutor` from custom event subscription + idle-timeout watchdog to SDK `session.sendAndWait(MessageOptions, timeoutMs)` with a defensive `AssistantMessageEvent` accumulator for the empty-final-response edge case (Phase 3b, commit `868aafe`); deleted `IdleTimeoutScheduler`, `EventSubscriptions`, `ReviewSessionEvents`, `ContentCollector`, `SessionEventException` and pruned `sharedScheduler` plumbing from orchestrator/context, reducing `AgentTuningConfig` and `BufferSettings` to a single field (Phase 3c, commit `293f2e5`). Net change across phases: ~ -1,100 production LoC. Verified with `mvn test` (820 passing; one pre-existing `/bin/true` env-dependent error in `CliPathResolverTest`).
- 2026-04-30 (v2026.04.30-copilot-sdk-stable): Copilot SDK stable migration and CI alignment — upgraded `copilot.sdk.version` from preview `0.3.0-java-preview.1` to stable `0.3.0-java.2`, normalized GitHub Actions `JDK_VERSION` from `26.0.1` to `26` across `ci.yml`/`codeql.yml`/`dependency-audit.yml`/`release.yml`, pinned the CycloneDX Maven plugin to `2.9.1` in the release workflow with the SBOM step refactored for readability, and granted job-level `permissions: contents: write` to the `publish-release` job so `gh release create` succeeds under the workflow-level least-privilege default (`contents: read`). Verified with `mvn clean package` on Java 26
- 2026-04-30 (v2026.04.30-micronaut5-snapshot): Micronaut 5 SNAPSHOT tracking — upgraded `io.micronaut.platform:micronaut-parent` and `micronaut.version` to `5.0.0-SNAPSHOT`, added Sonatype Central Snapshots repository for both dependencies and plugins, temporarily disabled the SNAPSHOT-blocking enforcer rule with an annotated TODO, and configured `micronaut-maven-plugin` with `<configurationValidation><failOnNotPresent>false</failOnNotPresent></configurationValidation>` so the new Micronaut 5 strict validator does not misclassify `-Amicronaut.processing.*` annotation processor arguments as unknown configuration properties. Verified with `mvn clean package` (BUILD SUCCESS) and 829 passing tests on Java 26 (Oracle 26.0.1)
- 2026-04-23 (v2026.04.23-copilot-sdk-compat): Copilot SDK compatibility alignment — upgraded `copilot.sdk.version` to `0.3.0-java-preview.1`, migrated event imports from `com.github.copilot.sdk.events.*` to `com.github.copilot.sdk.generated.*`, and updated MCP server handoff to satisfy the new `setMcpServers(Map<String, McpServerConfig>)` type requirement. Verified with `./mvnw -q -DskipTests compile`
- 2026-04-14 (v2026.04.14-model-auth-check): Model/auth-check alignment — updated bundled agent frontmatter models from `GPT-5.3-Codex` to `claude-opus-4.6-1m`, kept default model alignment consistent across runtime config and bundled agents, and improved Copilot CLI auth pre-check fallback compatibility (PR #121)
- 2026-04-14 (v2026.04.14-rubber-duck): Rubber-duck peer discussion review mode added — introduced agent-level two-model dialogue with synthesized final output, CLI/config support (`--rubber-duck`, `--dialogue-rounds`, `--peer-model`, `reviewer.rubber-duck.*`), timeout/mode handling alignment for rubber-duck execution, and dependency update `org.owasp:dependency-check-maven` 12.2.1 (PRs #119/#118)
- 2026-03-18 (v2026.03.18-auth): OAuth device-flow alignment — switched Copilot auth to logged-in user flow, removed `GITHUB_TOKEN`-centric guidance from runtime/CLI/docs, and synchronized README EN/JA + release notes. Verified with `mvn clean test` (743 passing)
- 2026-03-05 (v2026.03.05-notes): Performance/security improvements and codebase cleanup — `LocalFileCandidateProcessor` double-buffering avoidance, `GitHubTokenResolver` child process token propagation prevention, `CliPathResolver` trusted directory validation, `FrontmatterParser` YAML DoS resistance, `SensitiveHeaderMasking` pattern expansion, Java 26 migration, structured concurrency path unification, `CopilotPermissionHandlers` centralization, obsolete custom instruction class removal. PRs #85/#86/#87/#88/#89/#90 merged
- 2026-03-05 (v2026.03.05): **Breaking change** — Discontinued custom instruction support, migrated to agent skills only. Removed `--instructions`/`--no-instructions`/`--no-prompts` CLI options. Created 4 new agent skills from custom instructions (java-best-practices, java-bug-patterns, spring-boot-review, vuejs3-review). Completed all 16 complexity refactorings (5 HIGH + 9 MEDIUM + 2 LOW). Aligned micronaut.version with parent 4.10.9
- 2026-03-04 (v2026.03.04):Security fixes & dependency updates — pinned jackson-core to 2.21.1 (GHSA-72hv-8253-57qq), replaced ReDoS-prone regex with loop (CodeQL alert #9), bumped Copilot SDK to 1.0.10, bumped actions/checkout to 6.0.2, introduced OWASP Dependency Check in CI. PRs #75/#76/#77/#78/#79/#80 merged
- 2026-03-03 (v2026.03.03):Report generation flow improvement — generate per-pass review reports without overall summary, merge after all passes complete, recount finding severity from the merged report content to append an accurate overall summary, and deduplicate identical findings across agents in the executive summary with review category listing. Code-quality remediation including DRY/responsibility separation/Optional/type-safety improvements. PRs #72/#73 merged
- 2026-03-02 (v2026.03.02-notes): Report merge remediation finalization — unified duplicate-finding merge behavior, enforced merged-findings-based overall summary generation across all report paths, completed post-merge summary behavior alignment in PRs #57/#58/#59, and updated Micronaut to 4.10.9
- 2026-02-19 (v12): Best-practices remediation — simplified `TemplateService` cache synchronization with deterministic LRU behavior, replaced `SkillService` manual executor-cache management with Caffeine eviction + close-on-evict, abstracted CLI token input handling (`CliParsing.TokenInput`) from direct system I/O, simplified `ContentCollector` joined-content cache locking, improved section parsing readability in `AgentMarkdownParser`, made multi-pass start logging in `ReviewExecutionModeRunner` accurate, completed delegation methods in `GithubMcpConfig` map wrappers, simplified `ReviewResult` default timestamp handling, removed FQCN utility usage in `SkillExecutor`, and clarified concurrency/threading design intent in `CopilotService` and `ReviewOrchestrator`
- 2026-02-19 (v11): Code quality remediation — centralized token hashing via shared `TokenHashUtils`, unified orchestrator failure-result generation with `ReviewResult.failedResults(...)`, extracted orchestrator nested types (`OrchestratorConfig`, `PromptTexts`, and collaborator interfaces/records) into top-level package types, refactored scoped-instruction loading to avoid stream-side-effect try/catch blocks, introduced grouped execution settings (`ConcurrencySettings`, `TimeoutSettings`, `RetrySettings`, `BufferSettings`) with factory access, removed dead code (`ReviewResultPipeline.collectFromFutures`) and unused similarity field, and added dedicated command tests for `ReviewCommand` / `SkillCommand`
- 2026-02-19 (v10): Performance + WAF security hardening — eliminated redundant finding-key extraction in merge flow, added prefix-indexed near-duplicate lookup, optimized local file read buffer sizing, precompiled fallback whitespace regex, introduced structured security audit logging, enforced SDK WARN level even in verbose mode, applied owner-only report output permissions on POSIX, added Maven `dependencyConvergence`, and added weekly OWASP dependency-audit workflow
- 2026-02-19 (v9): Security follow-up closure — expanded suspicious-pattern validation for agent definitions to all prompt-injected fields, strengthened MCP header masking paths (`entrySet`/`values` stringification), and reduced token exposure by deferring `--token -` stdin materialization to resolution time
- 2026-02-19 (v8): Naming-rule alignment — synchronized executive summary output to `reports/{owner}/{repo}/executive_summary_yyyy-mm-dd-HH-mm-ss.md` (CLI invocation timestamp) and aligned README EN/JA examples + tests
- 2026-02-19 (v7): Security report follow-up — synchronized `LocalFileConfig` fallback sensitive file patterns with resource defaults and added an opt-in `security-audit` Maven profile (`dependency-check-maven`)
- 2026-02-19 (v6): Release documentation rollup — published the 2026-02-19 daily rollup section in RELEASE_NOTES EN/JA
- 2026-02-19 (v5): Documentation refinement — added concise operations summary for the v2-v4 progression
- 2026-02-19 (v4): Documentation sync — refreshed Operational Completion Check to 2026-02-19 and recorded PR #76 completion
- 2026-02-19 (v3): Reliability remediation — tolerate idle-timeout scheduler shutdown to prevent `RejectedExecutionException` retry storms
- 2026-02-19 (v2): CI consistency remediation — aligned CodeQL workflow JDK from 26 to 25 to match Java 25.0.2 policy
- 2026-02-19 (v1): Multi-pass review performance remediation — reuse `CopilotSession` across passes in the same agent and refactor orchestration to per-agent pass execution
- 2026-02-18: Best practices review remediation — compact constructors & defensive copies, SLF4J stack trace logging improvements, config record extensions, SkillConfig.defaults() factory method
- 2026-02-17 (v2): PRs #34–#40 — Security, performance, code quality, best practices fixes + 108 new tests
- 2026-02-17 (v1): PRs #22–#27 — Final remediation (PR-1 to PR-5)
- Operations summary (2026-02-19 v2-v4): Java 25 CI alignment (PR #74) → idle-timeout scheduler resilience fix (PR #76) → operational completion checklist sync (PR #78)
- Release details: `RELEASE_NOTES_en.md`
- GitHub Release: unavailable — the historical `v2026.06.24-refactor-seams-tests` target is absent;
  no replacement tag was inferred.

## Operational Completion Check (2026-02-19)

- Last updated: 2026-02-19 (v12)

- [x] All review findings addressed
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
- [x] Orchestrator failure result creation unified (`ReviewResult.failed`)
- [x] `ReviewOrchestrator` nested collaborator/config types extracted to top-level package types
- [x] Scoped instruction loading refactored to explicit file loop + isolated IO handling
- [x] `ExecutionConfig` grouped settings/factory added to reduce positional constructor risk
- [x] Dead code removed (`ReviewResultPipeline.collectFromFutures`)
- [x] `ReviewCommand` and `SkillCommand` unit tests added (normal/help/error)
- [x] `TemplateService` cache synchronization simplified with deterministic LRU behavior preserved
- [x] `SkillService` executor cache moved to Caffeine with eviction-time executor close
- [x] CLI token input abstracted from direct system I/O (`CliParsing.TokenInput`)
- [x] `ContentCollector` joined-content cache locking simplified
- [x] `AgentMarkdownParser` section parsing readability improved (removed iterable-cast trick)
- [x] `GithubMcpConfig` map wrappers completed delegation methods (`isEmpty`/`containsValue`/`keySet`/`values`)
- [x] `ReviewResult` default timestamp handling simplified
- [x] `SkillExecutor` FQCN utility call removed (`ExecutorUtils` import)
- [x] Concurrency/threading design intent documentation reinforced (`CopilotService`, `ReviewOrchestrator`)
- [x] README EN/JA synchronized

## Release Update Procedure (Template)

Canonical procedure: [Release Procedure](./docs/runbook.md#release-procedure)

1. Add a new date section to `RELEASE_NOTES_en.md` and `RELEASE_NOTES_ja.md` with matching structure.
2. Create and push an annotated tag (for example: `vYYYY.MM.DD-notes`).
3. Publish a GitHub Release from the tag and include EN/JA notes summary.
4. Update `README_en.md` and `README_ja.md` with release references and URLs.

## Release Operation Checklist

- [ ] Add the same release date section to `RELEASE_NOTES_en.md` and `RELEASE_NOTES_ja.md`.
- [ ] Update release references in `README_en.md` and `README_ja.md`.
- [ ] Commit on a feature/docs branch and open a PR (do not push directly to `main`).
- [ ] Confirm required checks are green (Supply Chain Guard, Dependency Audit, Build and Test, Build Native Image, dependency-review, CodeQL).
- [ ] Merge the PR and fast-forward local `main`.
- [ ] Create and push an annotated tag: `git tag -a vYYYY.MM.DD-notes -m "Release notes update for YYYY-MM-DD"` then `git push origin vYYYY.MM.DD-notes`.
- [ ] Create GitHub Release from the tag with EN/JA summary notes.

## Requirements

- **OpenJDK 28 EA** (`28.ea.9-open` in `.sdkmanrc`; preview features enabled for JVM runtime commands)
- **Oracle GraalVM 25.0.4** (optional; required only for the dedicated `pom-native.xml` Native Image build)
- Maven Wrapper 3.9.14 (`./mvnw`)
- GitHub Copilot CLI with an authenticated Copilot entitlement
- GitHub CLI login (`gh auth login`) for repository access
- GitHub Copilot CLI login (`gh copilot -- login` or `copilot login`) for Copilot SDK access
- Optional for Azure skills/MCP work: Node.js 18+ with `npx`, Azure CLI, and `az login`

> The JVM and native builds intentionally use different Java releases. Do not run
> `pom-native.xml` with the Java 28 toolchain or downgrade the main JVM build to Java 25.

## Azure Skills Plugin and MCP Setup

For Azure-related review and implementation work, prefer the official Azure Skills Plugin. In Copilot CLI, users who have not installed it should run:

```text
/plugin marketplace add microsoft/azure-skills
/plugin install azure@azure-skills
```

Use `/plugin update azure@azure-skills` to refresh an existing installation, then verify with:

```text
/skills
/mcp show
```

This repository also includes a project-level fallback copy of the official Azure skills in `.agents/skills/`, locked by `skills-lock.json`. To recreate it manually:

```bash
npx skills add https://github.com/microsoft/azure-skills/tree/main/.github/plugins/azure-skills/skills -a github-copilot --skill '*' --copy -y
```

WAF review skills require Microsoft Learn MCP grounding. If `/mcp show` does not list `microsoft-learn`, install the Microsoft Docs MCP plugin:

```text
/plugin install microsoftdocs/mcp
```

The project MCP configuration is tracked in `.vscode/mcp.json` and includes Azure MCP Server (`npx -y @azure/mcp@latest server start`) and Microsoft Learn MCP Server (`https://learn.microsoft.com/api/mcp`).

## Copilot SDK License and Server-Side Use

This project depends on `com.github:copilot-sdk-java` (currently `1.0.8`). The SDK artifact and upstream repository declare the MIT License, which is generally permissive for server-side integration, modification, and redistribution.

The MIT license covers the SDK code only. Calls to GitHub Copilot are still governed by the applicable GitHub Copilot product terms and the authenticated user's or organization's Copilot entitlement. Avoid designs that share one Copilot login across unrelated end users or repackage Copilot as a transparent SaaS backend without legal/product-term review.

## Supply Chain Policy

This repository enforces dependency and build hygiene in both Maven and GitHub Actions.

- Maven validate/build fails when checksum verification fails for Central artifacts.
- SNAPSHOT dependencies/plugins are blocked by Maven Enforcer.
- PR dependency review fails on vulnerability severity `moderate` or higher.
- PR dependency review denies these licenses: `GPL-2.0`, `GPL-3.0`, `AGPL-3.0`, `LGPL-2.1`, `LGPL-3.0`.
- `Supply Chain Guard` runs Maven `validate` policy checks without duplicating OWASP vulnerability scanning.
- `Dependency Audit` owns OWASP `dependency-check-maven` execution via direct `org.owasp:dependency-check-maven:check` with NVD API key propagation, cache restore, and retry/backoff for transient NVD failures.
- The self-managed `Dependency Submission` workflow runs on JDK 28 so dependency graph submission remains compatible with the Micronaut Maven plugin.
- Jackson is constrained in two independent families: Jackson 3 (`tools.jackson.*`) at 3.1.5, and transitive Jackson 2 (`com.fasterxml.jackson.*`) through BOM 2.22.1.
- The final dependency-security gate evaluated 31 runtime coordinates and reported zero known CVEs, with positive controls proving the scan was non-vacuous.

Recommended branch protection required checks:

- `Supply Chain Guard`
- `Dependency Audit`
- `Build and Test`
- `Build Native Image`
- `Dependency Review`

### Installing the SDKMAN-managed JDK

Using SDKMAN:

```bash
sdk env install
sdk env

# Confirm active Java version
java -version
```

## Installation

```bash
# Clone the repository
git clone https://github.com/your-org/multi-agent-reviewer.git
cd multi-agent-reviewer

# Activate the Java 28 JVM toolchain and run the full verification gate
sdk env install
sdk env
./mvnw -B clean verify

# Build and verify the native executable with GraalVM 25.0.4 (optional)
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.4-graal" \
PATH="$JAVA_HOME/bin:$PATH" \
./mvnw -B clean verify -Pnative -f pom-native.xml
```

### Test Troubleshooting

If tests fail with `NoSuchMethodError` for synthetic methods such as `access$0`, run a clean rebuild to remove stale class outputs:

```bash
./mvnw clean test
```

## Usage

> Note: This project uses Java preview features. Run the JVM JAR with `--enable-preview`.

### Security Runtime Notes

When running on the JVM in production and handling GitHub tokens, consider:

```bash
java --enable-preview \
  -XX:+DisableAttachMechanism \
  -XX:-HeapDumpOnOutOfMemoryError \
  -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar run --repo owner/repository --all
```

- `-XX:+DisableAttachMechanism`: helps reduce token exposure via runtime attach/debug interfaces.
- `-XX:-HeapDumpOnOutOfMemoryError`: prevents automatic heap dumps that can contain token `String` data.
- If your operations require heap dumps, write them to a tightly access-controlled location and keep retention short.

### Agent-Definition Trust and Secret Boundary

The final security model is defined by
[ADR-0007](docs/adr/0007-agent-definition-trust-model-and-secret-sink-boundary.md):

- Provenance is preserved as typed `AgentSource` / `AgentSourceDirectory` data from discovery through
  validation. Configured repository directories use the stricter `REPOSITORY_SUPPLIED` profile;
  explicitly selected directories use `USER_SUPPLIED`.
- The single `AgentDefinitionPolicy` owner validates all prompt-bearing fields, MCP configuration,
  model identifiers, focus areas, and assigned skills. Repository-supplied definitions receive
  tighter character/size/schema limits.
- Unicode controls reject unsafe control and default-ignorable characters while retaining a small
  documented allowlist for legitimate formatting. Provider-family tests pin accepted model prefixes
  for `claude-`, `gpt-`, `o3`, `o4-mini`, and `gemini-`.
- Secret masking is a Logback **sink** responsibility and is present in both human-readable and
  structured profiles. Object-level masking wrappers were removed because arbitrary logging and
  stringification paths can bypass them.

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
| `--token` | - | GitHub token input (`-` for stdin only; direct value is rejected) | `gh auth token` |
| `--parallelism` | - | Number of parallel executions | 4 |
| `--no-summary` | - | Skip summary generation | false |
| `--rubber-duck` | - | Force-enable rubber-duck peer-discussion review mode when disabled in config | true |
| `--no-rubber-duck` | - | Disable rubber-duck peer-discussion review mode for this run | false |
| `--compact-prompts` | - | Enable compact prompt budgets for this run | false |
| `--no-shared-session` | - | Compatibility switch requesting isolated pass sessions; the current adapter already creates one session per pass call | false |
| `--dialogue-rounds` | - | Override rubber-duck dialogue rounds (1–10) | 3 |
| `--peer-model` | - | Override peer model for rubber-duck mode (must differ from review model) | `gpt-5.6-sol` |
| `--model` | - | Default model for all stages | `claude-sonnet-5` |
| `--review-model` | - | Model for review | `gpt-5.3-codex` |
| `--report-model` | - | Model for report generation | `claude-sonnet-5` |
| `--summary-model` | - | Model for summary generation | `claude-sonnet-5` |
| `--help` | `-h` | Show help | - |
| `--version` | `-V` | Show version | - |
| `--verbose` | `-v` | Enable verbose logging (debug level) | - |

### List Subcommand

Displays a list of available agents. Additional directories can be specified with `--agents-dir`.

### Environment Variables

| Variable | Description | Default |
|----------|-------------|--------|
| `COPILOT_CLI_PATH` | Path to the Copilot CLI binary | Auto-detected from PATH |
| `GH_CLI_PATH` | Path to the GitHub CLI binary | Auto-detected from PATH |
| `GH_AUTH_FALLBACK_ENABLED` | Enable fallback from stdin token to `gh auth token` | false |
| `COPILOT_SDK_LOG_LEVEL` | Copilot SDK/CLI log level (`none,error,warning,info,debug,all,default`; `warn/off/trace` aliases supported) | warning |
| `COPILOT_START_TIMEOUT_SECONDS` | Copilot client start timeout (seconds) | 60 |
| `COPILOT_CLI_HEALTHCHECK_SECONDS` | CLI health check timeout (seconds) | 10 |
| `COPILOT_CLI_AUTHCHECK_SECONDS` | CLI auth check timeout (seconds) | 15 |
| `RUBBER_DUCK_PEER_MODEL` | Default peer model for rubber-duck mode | `gpt-5.6-sol` |

Auto-detected CLI paths are revalidated against trusted real-path directories:
`/usr/bin`, `/usr/local/bin`, `/bin`, `/opt/homebrew/bin`, `/usr/local/Cellar`, `/opt/homebrew/Cellar`, `/usr/local/Caskroom`, `/opt/homebrew/Caskroom`.

```bash
gh auth login
gh copilot -- login
```

If available in your environment, `copilot login` can be used instead of `gh copilot -- login`.

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

> **Note**: Files up to 256 KB each are collected, with a 2 MB total limit. Files that may contain sensitive information (`application-prod`, `.env`, `keystore`, etc.) are automatically excluded.

### Review Inputs

Custom instruction inputs via CLI were removed in v2026.03.05. Use agent skills under `.github/skills/` to provide domain-specific review guidance.

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
  agents:
    directories:                      # Agent definition search directories
      - ./agents
      - ./.github/agents
  execution:
    gh-auth-fallback-enabled: false
    concurrency:
      parallelism: 4                    # Default parallel agent count
      review-passes: 1                  # Standard-review passes per agent
    timeouts:
      orchestrator-timeout-minutes: 45  # Orchestrator timeout (minutes)
      agent-timeout-minutes: 20          # Agent timeout (minutes)
      idle-timeout-minutes: 5            # Idle timeout (minutes)
      skill-timeout-minutes: 20          # Skill timeout (minutes)
      summary-timeout-minutes: 20        # Summary timeout (minutes)
      gh-auth-timeout-seconds: 30        # GitHub auth timeout (seconds)
    retry:
      max-retries: 2             # Max retry count on review failure
  local-files:
    max-file-size: 262144               # Max local file size (256KB)
    max-total-size: 2097152             # Max total local file size (2MB)
  # Optional overrides. Canonical defaults live in shared/PromptBudget and are
  # intentionally omitted from the repository's application.yml.
  prompt-budget:
    compact-prompts: false              # Preserve default prompts unless enabled by config or --compact-prompts
    peer-content-max-chars: 12000       # Max relayed peer response chars in rubber-duck mode
    synthesis-turn-max-chars: 6000      # Max chars per dialogue turn in synthesis history
    synthesis-history-max-chars: 50000  # Max chars of dialogue history in synthesis prompt
    local-source-max-chars: 1048576     # Max local source chars when compact prompts are enabled
    summary-content-per-agent-max-chars: 12000 # Max compact summary chars per agent
    summary-total-max-chars: 60000      # Max compact summary prompt result chars
    summary-fallback-max-chars: 2000    # Max fallback excerpt for unstructured compact summary entries
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
    default-model: claude-sonnet-5    # Default for all models (changeable without rebuild)
    review-model: gpt-5.3-codex       # Model for review
    report-model: claude-sonnet-5     # Model for report generation
    summary-model: claude-sonnet-5    # Model for summary generation
    reasoning-effort: high           # Reasoning effort level (low/medium/high)
  summary:
    max-content-per-agent: 50000     # Max characters per agent content for summary prompt
    max-total-prompt-content: 200000 # Max total prompt characters for summary generation
    fallback-excerpt-length: 180     # Excerpt length used by fallback summary formatter
  rubber-duck:
    enabled: true                    # Enable rubber-duck peer-discussion mode globally
    dialogue-rounds: 3               # Number of dialogue rounds (1–10)
    peer-model: ${RUBBER_DUCK_PEER_MODEL:gpt-5.6-sol}  # Peer model (env var or explicit)
    synthesis-strategy: last-responder  # last-responder | dedicated-session
```

`review-passes` belongs at `reviewer.execution.concurrency.review-passes`. The legacy
`reviewer.execution.review-passes` path changed only a banner in an older implementation and does
not control execution. Standard mode returns one `ReviewResult` and one report file per pass; it
does not run the removed pass-merge pipeline. Rubber-duck mode always produces one synthesized
result per agent.

### External Configuration Override

When running as a fat JAR or Native Image, you can override the built-in `application.yml` without rebuilding.

**Place `application.yml` in the working directory:**

```bash
# Fat JAR
cp application.yml ./
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar

# Native Image
cp application.yml ./
./target/review
```

**Or specify an explicit path via system property:**

```bash
# Fat JAR
java --enable-preview \
  -Dmicronaut.config.files=/path/to/application.yml \
  -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar

# Native Image
./target/review -Dmicronaut.config.files=/path/to/application.yml
```

**Or override individual properties via environment variables:**

```bash
export REVIEWER_MODELS_DEFAULT_MODEL=gpt-4
export REVIEWER_EXECUTION_CONCURRENCY_PARALLELISM=8
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar
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

1. **CLI review override** (`--review-model`) updates every loaded agent model for the run
2. **Agent frontmatter model** (`model`) is used when present in an agent definition
3. **Parser fallback** (`ModelConfig.DEFAULT_MODEL`) is used for agent definitions that omit `model`
4. **Stage model settings** (`review-model`, `report-model`, `summary-model`) control non-agent stages and CLI/config overrides
5. **Default model** (`default-model`) — fallback when no stage-specific setting is specified

### Retry Behavior

When an agent review fails due to timeout or empty response, it is automatically retried.

- **Timeout is per-attempt, not cumulative**: `agent-timeout-minutes` applies independently to each attempt. For example, with `agent-timeout-minutes: 20` and `max-retries: 2`, the agent will try up to 3 times (initial + 2 retries) × 20 minutes each = up to 60 minutes total
- **Returns immediately on success**: If any attempt succeeds, remaining retries are skipped
- **Set `max-retries: 0`** to disable retries
- Retried on: timeout (`TimeoutException`), empty response, SDK exceptions

### Rubber-Duck Peer Discussion Mode

Each agent runs in **rubber-duck mode by default**, where two different LLM models debate review findings in a multi-round dialogue before producing a synthesized final review. Set `reviewer.rubber-duck.enabled: false` in configuration to opt out.

```bash
# Rubber-duck mode is enabled by default; this example overrides the peer model
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --repo owner/repository \
  --all \
  --peer-model gpt-4.1

# With custom dialogue rounds
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar \
  run \
  --repo owner/repository \
  --all \
  --rubber-duck \
  --peer-model gpt-4.1 \
  --dialogue-rounds 3
```

**How it works:**
1. **Session A** (agent's model) performs the initial review.
2. **Session B** (peer model) provides a peer review of Session A's findings.
3. Subsequent rounds alternate counter-arguments between the two models.
4. After all rounds, a synthesis prompt merges both perspectives into a single unified review.

**Key constraints:**
- The **peer model must differ** from the agent's review model — same-model pairing is rejected.
- Rubber-duck mode produces one synthesized review per agent. In standard mode, use
  `reviewer.execution.concurrency.review-passes` when separate repeated passes are required.
- Rubber-duck is globally enabled by default with `gpt-5.6-sol` as the fallback peer model.
- Timeout is automatically scaled based on the number of dialogue rounds.
- Specifying `--peer-model` or `--dialogue-rounds` on the CLI **auto-enables** rubber-duck mode (no need to also pass `--rubber-duck`).

**Synthesis strategies** (configurable via `reviewer.rubber-duck.synthesis-strategy`):
- `last-responder` (default): The synthesis prompt is sent to the last active session (Session B).
- `dedicated-session`: A new third session is created specifically for synthesis.

**Per-agent configuration:** Rubber-duck settings can also be specified per-agent in `.agent.md` frontmatter (see Agent Definition File section). Keep `.github/agents` definitions free of hard-coded model pins when you want GitHub Copilot custom agents to use the caller-selected model; pass `--review-model` for this tool when a specific review model is required.

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

The `model` frontmatter key is optional. Omit it in `.github/agents` custom-agent definitions to avoid hard-coding a model for GitHub Copilot users; use `--review-model` to override review sessions in this tool. Additional frontmatter fields for rubber-duck mode:

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `peer-model` | string | *(global config)* | Per-agent peer model override |
| `rubber-duck` | boolean | `false` | Enable rubber-duck mode for this agent |
| `dialogue-rounds` | int | `0` (defer to global) | Per-agent dialogue rounds override |
| `language` | string | `ja` | Template language for rubber-duck prompts (`ja`/`en`) |

```markdown
---
name: security
description: "Security Review"
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

Each agent first outputs an evidence-based `Good Points` section for strengths within its assigned domain, followed by improvement findings. If no strengths are confirmed, the agent explicitly reports that no Good Point was found in the reviewed scope.

Each improvement finding is output in the following format:

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

SKILL definitions with `metadata.agent` matching a review agent are also appended to that agent's normal review instruction. Therefore, the same assigned SKILL criteria are used by the agent's initial review and its rubber-duck dialogue. Skills without `metadata.agent` remain available to the `skill` subcommand but are not automatically injected into every review agent.

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
| `--token` | - | GitHub token input (`-` for stdin only; direct value is rejected) | `gh auth token` |
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

Native packaging is isolated in `pom-native.xml` because the JVM build targets Java 28 while the
validated Native Image toolchain is Oracle GraalVM 25.0.4.

```bash
# Build, test, package, and verify with the exact native toolchain
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.4-graal" \
PATH="$JAVA_HOME/bin:$PATH" \
./mvnw -B clean verify -Pnative -f pom-native.xml

# Run the generated executable
./target/review run --repo owner/repository --all
./target/review --help
./target/review --version
```

The native build includes the same 28 Markdown templates as the shaded JAR. Reflection/resource
configuration is tracked in:

- `src/main/resources/META-INF/native-image/reachability-metadata.json`
- `src/main/resources/META-INF/native-image/dev.logicojp/multi-agent-reviewer/reachability-metadata.json`
- the artifact-scoped `reflect-config.json`, `resource-config.json`, and `native-image.properties`

The two reachability-metadata files are deliberately byte-identical and register exact members rather
than broad `allDeclared*` access. When the Copilot SDK, Jackson, Logback, or GraalVM changes, regenerate
discovery data with a compatible tracing-agent run, review it, update both copies, and rerun the full
native `clean verify` gate. Do not replace reviewed metadata blindly with raw tracing output.

This repository does not currently ship a `Dockerfile`; the supported distributable artifacts are the
shaded JAR and `target/review`.

## Architecture

The application is structured as **Ports & Adapters (hexagonal) layering**. The final tree contains
201 production Java sources across five named layers plus a layer-zero composition root. Dependencies
point inward and infrastructure attaches from the outside by implementing outbound ports. The full
decision record is [ADR-0006](docs/adr/0006-ports-and-adapters-layering.md).

| Layer | Responsibility | May depend on |
|---|---|---|
| layer zero (`ReviewApp`, `ApplicationPortFactory`, `ReviewPortFactory`) | Thin process entry and wiring of already-constructed ports/use cases; no business policy | every layer |
| `presentation` | CLI parsing, commands, request construction, target/agent resolution, console output | inbound ports, application DTOs, `domain`, `shared` |
| `application` | Framework/SDK-free use cases and review/report orchestration | `application.port.*`, `domain`, `shared`, JDK |
| `domain` | Business rules and models | `shared`, JDK only |
| `infrastructure` | Outbound adapters: Copilot SDK, filesystem, config, parsing, logging, startup | outbound ports, `domain`, `shared`, external libraries |
| `shared` | Pure cross-layer utilities and canonical defaults | JDK only |

The port contract currently has **8 inbound interfaces** implemented by application use cases and
**15 outbound interfaces** implemented by infrastructure adapters. `ReviewApp` only prepares startup,
starts the Micronaut CLI context, invokes `CliApplication`, and returns its exit code; the two root
factories perform wiring only.

Boundaries are enforced mechanically rather than by convention. `LayerDependencyRulesTest` inspects
compiled bytecode with the JDK `java.lang.classfile` API, checks every row of the import matrix, and
detects package/layer cycles. Any violation fails `mvn verify`.

### Layer structure

```mermaid
flowchart TB
    Root["layer zero
    ReviewApp
    ApplicationPortFactory
    ReviewPortFactory"]

    subgraph L1["presentation (CLI adapter)"]
        direction LR
        Cli["CliApplication + command/"]
        Prep["ReviewPreparationService
        target / agent / model resolution"]
        Fmt["parser/ + formatter/"]
    end

    In["8 inbound ports
    RunReviewPort / LoadAgentPort
    ExecuteSkillPort / GenerateReportPort
    ResolveTokenPort / RunDiagnosticsPort / ..."]

    subgraph L2["application (use cases)"]
        direction LR
        Review["review/
        ReviewOrchestrator
        ReviewExecutionModeRunner"]
        Other["agent/ auth/ report/
        skill/ startup/"]
    end

    Out["15 outbound ports
    RunCopilotSessionPort / RunRubberDuckSessionPort
    LoadTemplatePort / WriteReportPort
    ResolveReviewSettingsPort / ..."]

    subgraph L3["infrastructure (adapters)"]
        direction LR
        Cop["copilot/
        SDK lifecycle + session adapters"]
        Infra["auth/ config/ file/ logging/
        parsing/ startup/ template/"]
    end

    subgraph L4["domain (business rules, JDK only)"]
        direction LR
        Dom["agent/ instruction/ report/
        resilience/ review/ skill/"]
    end

    Shared["shared
    pure utilities (java.* only)"]

    Ext["GitHub Copilot API / GitHub MCP Server"]

    Root -.->|starts / wires| L1
    Root -.->|wires| L2
    Root -.->|wires| L3
    L1 -->|drives| In
    In --> L2
    L2 -->|calls| Out
    L3 -.->|implements| Out
    L1 --> L4
    L2 --> L4
    L3 --> L4
    L1 --> Shared
    L2 --> Shared
    L3 --> Shared
    L4 --> Shared
    L3 --> Ext
```

### Review execution flow

```mermaid
flowchart TB
    ReviewApp --> CliApplication
    CliApplication --> ReviewCommand
    ReviewCommand --> ReviewPreparationService
    ReviewCommand --> ReviewExecutionCoordinator
    ReviewExecutionCoordinator --> ReviewRunExecutor

    ReviewRunExecutor -->|RunReviewPort| ReviewOrchestrator
    ReviewOrchestrator --> LocalSourcePrecomputer
    ReviewOrchestrator -->|CreateReviewSessionPortsPort| SessionFactory["ReviewOrchestratorFactory
    (outbound adapter factory)"]
    ReviewOrchestrator --> ReviewExecutionModeRunner
    ReviewExecutionModeRunner --> AgentReviewExecutor
    AgentReviewExecutor -->|standard| ReviewPassRunner
    AgentReviewExecutor -->|rubber-duck| RubberDuckDialogueRunner
    ReviewExecutionModeRunner --> ReviewResultPipeline["ReviewResultPipeline
    collect / filter / log only"]

    SessionFactory --> ReviewSessionExecutor
    SessionFactory --> RubberDuckDialogueExecutor
    ReviewPassRunner -->|RunCopilotSessionPort| ReviewSessionExecutor
    RubberDuckDialogueRunner -->|RunRubberDuckSessionPort| RubberDuckDialogueExecutor

    ReviewRunExecutor -->|GenerateReportPort| GenerateReportUseCase
    GenerateReportUseCase -->|WriteReportPort| ReportFileWriter
    GenerateReportUseCase -->|GenerateAiSummaryPort| AiSummaryClient
    ReviewSessionExecutor -.-> Ext["GitHub Copilot API / GitHub MCP"]
    RubberDuckDialogueExecutor -.-> Ext
    AiSummaryClient -.-> Ext
```

Agents are forked with structured concurrency and correlation-context propagation. In standard mode,
agents run in parallel while each agent's configured passes run in order and remain separate results;
`ReviewResultPipeline` does not merge them. In rubber-duck mode, each agent runs one multi-round dialogue
and returns one synthesized result. `GenerateReportUseCase` writes per-result reports and produces the
cross-agent Executive Summary through outbound ports.

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
├── review-custom-instruction.md   # Review custom instruction
├── review-quality-constraints.md  # Review quality constraints
├── rubber-duck-initial-en.md      # Rubber-duck initial review prompt (EN)
├── rubber-duck-initial-ja.md      # Rubber-duck initial review prompt (JA)
├── rubber-duck-peer-review-en.md  # Rubber-duck peer review prompt (EN)
├── rubber-duck-peer-review-ja.md  # Rubber-duck peer review prompt (JA)
├── rubber-duck-counter-en.md      # Rubber-duck counter-argument prompt (EN)
├── rubber-duck-counter-ja.md      # Rubber-duck counter-argument prompt (JA)
├── rubber-duck-synthesis-en.md    # Rubber-duck synthesis prompt (EN)
└── rubber-duck-synthesis-ja.md    # Rubber-duck synthesis prompt (JA)
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

Templates support `${placeholder}` format placeholders. See each template file for available placeholders.

## Project Structure

The following tree is synchronized with the final Unreleased Ports & Adapters layout (see
[ADR-0006](docs/adr/0006-ports-and-adapters-layering.md)).

```
multi-agent-reviewer/
├── pom.xml                              # Java 28 JVM/shaded-JAR build and security profile
├── pom-native.xml                       # Java 25 / GraalVM Native Image build
├── .sdkmanrc                            # OpenJDK 28 EA selection
├── toolchains-template.xml              # Optional Maven toolchain template
├── skills-lock.json                     # Locked source/hash metadata for imported Azure skills
├── .vscode/mcp.json                     # Azure MCP + Microsoft Learn MCP workspace config
├── .agents/skills/                      # Project fallback copy of official microsoft/azure-skills
├── .github/
│   ├── agents/                          # GitHub Copilot custom-agent profiles
│   ├── instructions/                     # Repository instruction files
│   ├── skills/                          # Project-specific review skills (SKILL.md format)
│   ├── modernize/rearchitecture/         # Rearchitecture decisions, evidence, and task artifacts
│   └── workflows/                       # CI/CD and security workflows
│       ├── ci.yml                       # Supply-chain guard, audit, JVM build/test, native image
│       ├── codeql.yml                   # CodeQL analysis
│       ├── dependency-audit.yml         # Scheduled OWASP dependency audit
│       ├── dependency-review.yml        # PR dependency review
│       ├── dependency-submission.yml    # Self-managed Maven dependency graph submission
│       ├── release.yml                  # Tagged release build and publish workflow
│       └── scorecard.yml                # OpenSSF Scorecard
├── agents/                              # Runtime agent definitions loaded by the CLI app
├── docs/adr/                            # Architecture Decision Records (ADR-0001 through ADR-0008)
├── scripts/
│   ├── archive-reports.sh               # Report archive helper used by CI
│   └── diagnose-content-exclusion.sh     # Local-source exclusion diagnostic helper
├── templates/                           # 28 prompt/report/summary/rubber-duck Markdown templates
├── src/main/java/dev/logicojp/reviewer/
│   ├── ReviewApp.java                   # Thin process entry: startup, context, CliApplication, exit code
│   ├── ApplicationPortFactory.java      # Layer-zero inbound-port/use-case bindings
│   ├── ReviewPortFactory.java           # Layer-zero RunReviewPort binding
│   ├── presentation/                    # CLI adapter layer (must not reference infrastructure)
│   │   ├── command/                     # One class per command: review / list-agents / skill / doctor
│   │   ├── parser/                      # Hand-rolled argument parsing, usage text, exit codes
│   │   ├── formatter/                   # Console output formatting
│   │   └── ...                          # Command coordinators, option models, target/agent resolution
│   ├── application/                     # Use-case orchestration (no SDK types, no DI annotations)
│   │   ├── port/inbound/                # 8 interfaces driven by presentation
│   │   ├── port/outbound/               # 15 interfaces implemented by infrastructure
│   │   ├── agent/                       # Agent loading use case
│   │   ├── auth/                        # Token-resolution use case
│   │   ├── report/                      # Report generation and AI summary orchestration
│   │   ├── review/                      # Structured orchestration, passes, retries, result pipeline
│   │   ├── skill/                       # Skill execution use case
│   │   └── startup/                     # Logging-startup use case
│   ├── domain/                          # Business rules and models (shared + java.* only)
│   │   ├── agent/                       # Agent config, prompt building, validation
│   │   ├── instruction/                 # Instruction frontmatter model and safety validation
│   │   ├── report/                      # Review results, finding extraction, sanitization
│   │   ├── resilience/                  # Domain exceptions and retry semantics
│   │   ├── review/                      # Review context, target, local-file selection
│   │   └── skill/                       # Skill definition model
│   ├── infrastructure/                  # Adapters to the outside world (implement outbound ports)
│   │   ├── auth/                        # GitHub token resolution, gh CLI lookup, Copilot CLI path resolution
│   │   ├── config/                      # Micronaut @ConfigurationProperties records
│   │   ├── copilot/                     # Copilot SDK lifecycle and session adapters
│   │   ├── file/                        # Local-source collection and secure report writing
│   │   ├── logging/                     # Logback level/correlation/security-audit adapters
│   │   ├── parsing/                     # YAML/frontmatter/SKILL parsing and registries
│   │   ├── startup/                     # Process-environment adapter
│   │   └── template/                    # Template catalog loading
│   └── shared/                          # Cross-layer pure utilities and defaults (java.* only)
├── src/main/resources/
│   ├── application.yml                  # Default Micronaut reviewer configuration
│   ├── logback.xml / logback-json.xml   # Logging configuration and sink redaction
│   ├── META-INF/native-image/
│   │   ├── reachability-metadata.json   # Generic exact-member Native Image metadata
│   │   └── dev.logicojp/multi-agent-reviewer/ # Artifact-scoped metadata/configuration
│   ├── defaults/                        # Local-source defaults and sensitive-file filters
│   └── safety/
│       └── suspicious-patterns.txt      # Prompt-injection suspicious-pattern definitions
└── src/test/java/dev/logicojp/reviewer/
    └── architecture/                    # Import-matrix, cycle, composition-root, and control rules
```

## License

MIT License
