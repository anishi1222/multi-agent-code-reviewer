# Release Notes

## Update Rules (Template)

Reference checklist: `reports/anishi1222/multi-agent-code-reviewer/documentation_sync_checklist_2026-02-17.md`

1. Add a new date section with the same structure in both EN and JA files.
2. Create and push an annotated tag (for example: `vYYYY.MM.DD-notes`).
3. Publish a GitHub Release from the tag and include EN/JA summary notes.
4. Update `README_en.md` and `README_ja.md` with release references and URLs.

## 2026-03-05 (v2026.03.05-notes)

### Summary
- Applied performance and security review findings.
- Migrated to Java 26 and stabilized native runtime config binding.
- Removed structured-concurrency feature flag toggles and stabilized review execution path.
- Centralized session permission control in `CopilotPermissionHandlers`.
- Added pass-aware session naming and shared-session control for multi-pass reviews.
- Cleaned up obsolete custom instruction classes from the `instruction/` package.

### Highlights

#### Performance Improvements
- `LocalFileCandidateProcessor`: Added fast path using `Files.readString` to avoid double-buffering.
- `ReviewResultMerger`: Refactored `AggregatedFinding` to reduce object allocation cost.
- `ReviewContext` / `SharedCircuitBreaker`: Introduced shared static instance to avoid per-call instantiation.
- `application.yml`: Added memory/heap warning comment for parallelism tuning.
- `PlaceholderUtils`, `TemplateService`, `FindingsSummaryFormatter`: Various optimizations.

#### Security Improvements
- `GitHubTokenResolver`: Removed `GITHUB_TOKEN` env var from child process environment to prevent token propagation.
- `CliPathResolver`: Added `TRUSTED_DIRECTORIES` allowlist and `isInTrustedDirectory()` guard for `GH_CLI_PATH` validation.
- `FrontmatterParser`: Set nesting depth limit (10) and max aliases limit (50) for YAML DoS resistance.
- `SensitiveHeaderMasking`: Expanded sensitive pattern coverage (9 patterns including `api-key`, `secret`, `password`, `credential`, `cookie`, `x-api-key`).

#### Execution Path and Session Management
- Removed structured-concurrency feature flag toggles and fixed execution to the stabilized path.
- Added session naming format: `{agent}_{currentPass}of{totalPasses}_{invocationTimestamp}`.
- Added `--no-shared-session` to force isolated sessions per pass.
- Introduced `CopilotPermissionHandlers` to centralize session permission control across `ReviewSessionConfigFactory`, `SkillExecutor`, and `SummaryGenerator`.

#### Codebase Cleanup
- Unified structured concurrency execution path and stabilized tests (PR #89).
- Removed obsolete instruction classes: `CustomInstruction`, `CustomInstructionLoader`, `InstructionSource`, `PromptLoader`, `ScopedInstructionLoader`.
- Removed `cli/ReviewCustomInstructionResolver`.
- Removed `util/FeatureFlags`.
- Moved pass-level intermediate reports under `.checkpoints/passes` with automatic cleanup at CLI exit.

#### PR Chain
- [#85](https://github.com/anishi1222/multi-agent-code-reviewer/pull/85): Java 26 migration and native runtime config binding stabilization
- [#86](https://github.com/anishi1222/multi-agent-code-reviewer/pull/86): remove feature flags and stabilize session execution path
- [#87](https://github.com/anishi1222/multi-agent-code-reviewer/pull/87): add per-pass session naming, no-shared-session mode, and checkpoint cleanup
- [#88](https://github.com/anishi1222/multi-agent-code-reviewer/pull/88): sync README and release notes for session mode updates
- [#89](https://github.com/anishi1222/multi-agent-code-reviewer/pull/89): structured concurrency path unification and test stabilization
- [#90](https://github.com/anishi1222/multi-agent-code-reviewer/pull/90): apply performance and security improvements

### Validation
- `mvn clean package` — build succeeded
- `java --enable-preview -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar run --repo anishi1222/multi-agent-code-reviewer --all` — run succeeded

---

## 2026-03-05 (v2026.03.05)

### Summary
- Discontinued custom instruction support and migrated to agent skills only (**breaking change**).
- Created 4 new agent skills from custom instructions (java-best-practices, java-bug-patterns, spring-boot-review, vuejs3-review).
- Completed all 16 complexity-based refactorings (5 HIGH + 9 MEDIUM + 2 LOW).
- Aligned `micronaut.version` with `micronaut-parent:4.10.9`.

### Breaking Changes
- **Custom instruction support removed**: Deleted `--instructions`, `--no-instructions`, `--no-prompts` CLI options. Removed auto-loading from `.github/instructions/` and `.github/prompts/`. Agent skills (`.github/skills/`) are now the only supported extensibility mechanism.

### Highlights

#### New Agent Skills
- `java-best-practices`: Java best practices (Records, pattern matching, immutability, Optional, naming)
- `java-bug-patterns`: Bug patterns & code smells (resource management, equality, cognitive complexity)
- `spring-boot-review`: Spring Boot best practices (DI, configuration, service layer, logging)
- `vuejs3-review`: Vue.js 3 best practices (Composition API, TypeScript, component design)

#### Complexity Refactoring (16 items)
- HIGH: FrontmatterParser (CC 10→6), ReviewResultMerger (59→32 lines), SkillExecutor (nesting 5→2), ReviewAgent (53→17 lines), SummaryGenerator (10→7 params)
- MEDIUM: SkillExecutor.executeSync, SummaryGenerator (runSummaryAttempt/buildSummaryWithAI), FrontmatterParser.parseFieldsManually, ReviewResultMerger.normalizeSingleResult, ReviewAgent.executeReviewPassesFallback, ReviewExecutionModeRunner (3 methods)
- LOW: AgentConfigLoader.firstSuspiciousField (CC 7→2), ReviewOrchestrator.assembleCollaborators (55→25 lines)

#### Dependencies
- Changed `micronaut.version` from `4.10.16` to `4.10.9` to align with `micronaut-parent`.

### Validation
- `mvn test` — 731 tests passed, 0 failures
- `mvn clean package` — build succeeded

---

## 2026-03-04 (v2026.03.04)

### Summary
- Applied security vulnerability fixes (jackson-core GHSA-72hv-8253-57qq, CodeQL ReDoS alert #9).
- Updated dependencies (Copilot SDK 1.0.10, actions/checkout 6.0.2).
- Introduced OWASP Dependency Check with NVD API key integration in CI.

### Highlights

#### Security Fixes
- Pinned jackson-core to 2.21.1 to address GHSA-72hv-8253-57qq (PRs #76, #80).
- Replaced ReDoS-prone `TRAILING_SEPARATOR` regex with a loop-based implementation (CodeQL alert #9, PR #77).

#### Dependency Updates
- Bumped `io.github.copilot-community-sdk:copilot-sdk` to 1.0.10 (PR #79).
- Bumped `actions/checkout` from 4.2.2 to 6.0.2 (PR #78).

#### CI Improvements
- Introduced OWASP Dependency Check with NVD API key integration for automated dependency vulnerability scanning.

#### Documentation Fix
- Corrected release tag references to v2026.03.03 (PR #75).

#### PR Chain
- [#75](https://github.com/anishi1222/multi-agent-code-reviewer/pull/75): fix release tag URL references
- [#76](https://github.com/anishi1222/multi-agent-code-reviewer/pull/76): jackson-core security fix
- [#77](https://github.com/anishi1222/multi-agent-code-reviewer/pull/77): ReDoS regex fix
- [#78](https://github.com/anishi1222/multi-agent-code-reviewer/pull/78): actions/checkout update
- [#79](https://github.com/anishi1222/multi-agent-code-reviewer/pull/79): Copilot SDK update
- [#80](https://github.com/anishi1222/multi-agent-code-reviewer/pull/80): jackson-core pinning

### Validation
- `mvn test` — 838 tests passed, 0 failures
- `mvn clean package` — build succeeded

---

## 2026-03-03 (v2026.03.03)

### Summary
- Reworked the report generation pipeline so that severity counts in the overall summary match the actual merged findings.
- Code-quality report remediation: DRY, responsibility separation, Optional propagation, type-safe priority counts, constructor reduction, and targeted test additions.

### Highlights

#### Report Generation Flow Improvement
- Generate per-pass review reports without embedding an overall summary.
- After all passes complete, merge per-pass results by agent using `ReviewResultMerger`.
- Recount finding severity from merged report content and append an accurate overall summary via new `ReviewOverallSummaryAppender`.
- Executive summary now deduplicates identical findings across agents and lists review categories.
- `ReviewResultPipeline` no longer performs merge; merge and summary are handled by `ReviewRunExecutor`.

#### Code-Quality Remediation
- Reduced duplication in `ReviewAgent` review-pass parameter resolution (`ResolvedReviewParams`).
- Replaced nullable `LocalSourcePrecomputer` returns with `Optional` and propagated through orchestrator.
- Introduced `FindingPriority` enum and `EnumMap`-based counting in `ReviewResultMerger`.
- Split `AgentConfigLoader.loadAgentsInternal` into focused helper methods.
- Added retry/backoff deduplication in `CopilotClientStarter`.
- Added `ExecutorResources.shutdownGracefully()` for centralized cleanup.
- New tests: `RetryExecutorTest`, `CircuitBreakerFactoryTest`, `ExecutorResourcesTest`, `OrchestratorConfigTest`, `ReviewOverallSummaryAppenderTest`.

#### PR Chain
- [#72](https://github.com/anishi1222/multi-agent-code-reviewer/pull/72): code-quality report remediations
- [#73](https://github.com/anishi1222/multi-agent-code-reviewer/pull/73): fix report generation flow for accurate severity counts

### Validation
- `mvn test` — 838 tests passed, 0 failures
- `mvn clean package` — build succeeded

---

## 2026-03-02 (v2026.03.02-notes)

### Summary
- Finalized the review-merge remediation sequence to ensure report summaries are generated from merged findings.
- Completed merge behavior alignment for duplicate findings and overall summary generation across all report paths.
- Updated Micronaut to 4.10.9 in `pom.xml` and validated build/runtime execution.

### Highlights

#### Report Merge and Summary Alignment
- Unified duplicate-finding merge behavior in the report pipeline.
- Ensured the overall summary is appended as a single post-merge section based on merged findings.
- Removed reliance on pass-level summary aggregation in favor of merged-report-level summary generation.

#### PR Chain
- [#57](https://github.com/anishi1222/multi-agent-code-reviewer/pull/57): fix duplicate findings and post-merge summary generation
- [#58](https://github.com/anishi1222/multi-agent-code-reviewer/pull/58): include merged overall summary in all review reports
- [#59](https://github.com/anishi1222/multi-agent-code-reviewer/pull/59): generate report overall summary from merged findings

#### Dependency Baseline Update
- Updated Micronaut version to `4.10.9` in `pom.xml`.

### Validation
- Local build succeeded: `mvn clean package`.
- End-to-end review run succeeded with Native Image tracing agent enabled.

---

## 2026-02-19 Daily Rollup

### Executive Summary
- Completed a same-day, staged remediation/documentation rollout from v1 through v5.
- Final state is fully aligned across code, CI workflows, release notes, and README EN/JA references.

### Progression (v1 → v5)
- v1: Multi-pass review performance remediation (shared `CopilotSession` reuse).
- v2: Java 25 policy alignment in CodeQL workflow (JDK 26 → 25).
- v3: Reliability hardening for idle-timeout scheduler shutdown/rejection path.
- v4: Operational completion checklist synchronization (2026-02-19 baseline, PR #76 traceability).
- v5: Concise operations summary line added to README EN/JA for v2-v4 handoff context.

### Key PR Chain
- Core reliability/code path: [#74](https://github.com/anishi1222/multi-agent-code-reviewer/pull/74), [#76](https://github.com/anishi1222/multi-agent-code-reviewer/pull/76)
- Documentation/release synchronization: [#78](https://github.com/anishi1222/multi-agent-code-reviewer/pull/78), [#79](https://github.com/anishi1222/multi-agent-code-reviewer/pull/79), [#80](https://github.com/anishi1222/multi-agent-code-reviewer/pull/80), [#81](https://github.com/anishi1222/multi-agent-code-reviewer/pull/81)

---

## 2026-02-19 (v12)

### Summary
- Best-practices remediation cycle completing the v9–v12 progression.
- Simplified cache management, abstracted CLI I/O, improved logging accuracy, and reinforced concurrency design documentation.

### Highlights

#### PR #95: Best-Practices Remediation v12
- `TemplateService`: Simplified cache synchronization while preserving deterministic LRU eviction behavior.
- `SkillService`: Replaced manual executor-cache management with Caffeine library (eviction-time executor close).
- `CliParsing`: Introduced `TokenInput` interface to abstract CLI token reading from direct system I/O.
- `ContentCollector`: Simplified joined-content cache locking.
- `AgentMarkdownParser`: Improved section parsing readability (removed iterable-cast trick).
- `ReviewExecutionModeRunner`: Made multi-pass start logging accurate to actual execution state.
- `GithubMcpConfig`: Completed Map wrapper delegation methods (`isEmpty`/`containsValue`/`keySet`/`values`).
- `ReviewResult`: Simplified default timestamp handling.
- `SkillExecutor`: Removed FQCN utility call (direct `ExecutorUtils` import).
- `CopilotService` / `ReviewOrchestrator`: Reinforced concurrency/threading design intent documentation.
- Added Caffeine dependency to `pom.xml`.
- Added `CliParsingTest` tests for `TokenInput` abstraction.

#### PR #96: Documentation Sync
- Updated project structure tree and architecture Mermaid diagram in README EN/JA from v7 to v12.
- Added `.github/workflows/` section, 8 new orchestrator files, `SecurityAuditLogger`, and `TokenHashUtils`.

### Validation
- 756 tests, 0 failures.
- CI 5/5 green on both PRs.

### Merged PRs
- [#95](https://github.com/anishi1222/multi-agent-code-reviewer/pull/95): fix: remediate best-practices findings v12
- [#96](https://github.com/anishi1222/multi-agent-code-reviewer/pull/96): docs: sync project structure and architecture diagram to v12

---

## 2026-02-19 (v11)

### Summary
- Code quality remediation — extracted nested types, unified shared utilities, removed dead code, and added command unit tests.

### Highlights

#### PR #94: Code Quality Remediation v11
- Centralized SHA-256 token hashing via shared `TokenHashUtils`.
- Unified orchestrator failure-result generation with `ReviewResult.failedResults(...)`.
- Extracted `ReviewOrchestrator` nested types (`OrchestratorConfig`, `PromptTexts`, collaborator interfaces/records) to top-level package types.
- Refactored `ScopedInstructionLoader` to explicit file loop + isolated IO handling.
- Introduced `ExecutionConfig` grouped settings (`ConcurrencySettings`, `TimeoutSettings`, `RetrySettings`, `BufferSettings`) with factory access.
- Removed dead code: `ReviewResultPipeline.collectFromFutures`, unused `ReviewFindingSimilarity.WHITESPACE`.
- Added `ReviewCommand` and `SkillCommand` dedicated unit tests.

### Validation
- 756 tests, 0 failures.
- CI 5/5 green.

### Merged PRs
- [#94](https://github.com/anishi1222/multi-agent-code-reviewer/pull/94): fix: resolve code-quality remediation findings v11

---

## 2026-02-19 (v10)

### Summary
- Performance optimization and WAF security hardening across merge flow, file I/O, regex handling, logging, and build governance.

### Highlights

#### PR #93: Performance + WAF Security Hardening v10
- Eliminated redundant finding-key extraction in merge flow (`findingKeyFromNormalized` reuse).
- Added priority+title-prefix index for near-duplicate detection before similarity matching.
- Optimized `ByteArrayOutputStream` initial capacity for local file reads.
- Precompiled fallback summary whitespace normalization regex.
- Introduced structured `SECURITY_AUDIT` logging for auth/trust/instruction-validation events.
- Enforced Copilot SDK logger at `WARN` even in `--verbose` mode.
- Applied owner-only POSIX permissions for report output directories/files.
- Added Maven `dependencyConvergence` enforcer rule.
- Added weekly OWASP dependency audit GitHub Actions workflow (`dependency-audit.yml`).

### Validation
- 756 tests, 0 failures.
- CI 5/5 green.

### Merged PRs
- [#93](https://github.com/anishi1222/multi-agent-code-reviewer/pull/93): fix: resolve performance and WAF security findings

---

## 2026-02-19 (v9)

### Summary
- Security follow-up closure — expanded prompt-injection validation, strengthened MCP header masking, and reduced token exposure window.

### Highlights

#### PR #92: Security Follow-up Closure v9
- Expanded suspicious-pattern validation for agent definitions to all prompt-injected fields (not just instructions).
- Strengthened MCP auth header masking for `entrySet`/`values` stringification paths.
- Deferred `--token -` stdin materialization to token-resolution boundary, minimizing in-memory token lifetime.

### Validation
- Full test suite passed.
- CI 5/5 green.

### Merged PRs
- [#92](https://github.com/anishi1222/multi-agent-code-reviewer/pull/92): fix: security follow-up closure

---

## 2026-02-19 (v8)

### Summary
- Aligned executive summary output naming with the report-file naming policy.
- Replaced timestamped executive summary output filename with fixed `executive-summary.md`.
- Updated related test stub and README EN/JA output examples for naming consistency.

### Highlights

#### PR #90: Executive Summary Filename Alignment
- `SummaryGenerator` now writes the executive summary file as `executive-summary.md`.
- Updated `ReviewRunExecutorTest` stubbed summary path to `executive-summary.md`.
- Updated output examples in `README_en.md` and `README_ja.md` to match the new naming rule.

### Validation
- PR #90 required checks passed: `CI/Build Native Image`, `CI/Build and Test`, `CI/Supply Chain Guard`, `Dependency Review`, `Automatic Dependency Submission`

### Merged PRs
- [#90](https://github.com/anishi1222/multi-agent-code-reviewer/pull/90): align executive summary filename with report naming convention

---

## 2026-02-19 (v7)

### Summary
- Followed up the latest security report with targeted remediations and documentation synchronization.
- Synchronized fallback sensitive file patterns in `LocalFileConfig` with resource defaults to keep secret-file filtering safe even on fallback paths.
- Added an opt-in OWASP dependency audit profile and refreshed README EN/JA (including architecture diagram and project structure sync note).

### Highlights

#### Sensitive Pattern Fallback Consistency
- Updated `FALLBACK_SENSITIVE_FILE_PATTERNS` in `LocalFileConfig` to include generic config filenames already present in `defaults/sensitive-file-patterns.txt`:
  - `application.yml`, `application.yaml`
  - `config.json`, `settings.json`, `settings.yaml`
- This preserves sensitive-file filtering behavior even when resource loading falls back to in-code defaults.

#### Regression Guard
- Added `LocalFileConfigTest#fallbackSensitivePatternsContainResourcePatterns`.
- The test validates that fallback sensitive-pattern definitions always include all resource-defined patterns, preventing future drift.

#### Supply-Chain Audit Operation
- Added Maven property `dependency.check.version` and optional profile `security-audit` in `pom.xml`.
- Running `mvn -Psecurity-audit verify` enables OWASP `dependency-check-maven` without making every default build heavier.

#### Documentation Synchronization
- Updated `README_en.md` and `README_ja.md` to record v7 remediation.
- Updated architecture diagram to include `CopilotClientStarter` and `CopilotCliHealthChecker` relations.
- Updated project structure section note to indicate synchronization with the current source layout.

### Validation
- Full test suite passed locally: 971 tests, 0 failures.

---

## 2026-02-19 (v6)

### Summary
- Applied remediations for four deduplicated security themes from the 2026-02-19 security review.
- Hardened token handling, prompt-injection resilience, TOCTOU safety for local file reads, and sanitizer URI filtering.
- Merged all changes to `main` via PR #85.

### Highlights

#### PR #85: Security Hardening (Token Handling, Normalization, TOCTOU, XSS)
- `CopilotService`:
  - Replaced long-lived raw token comparison state with SHA-256 fingerprint state.
- `CustomInstructionSafetyValidator`:
  - Expanded homoglyph normalization coverage (major Greek confusables in addition to Cyrillic).
- `LocalFileCandidateProcessor`:
  - Replaced bulk `readString` with bounded streaming read and enforced `maxFileSize` / `maxTotalSize` during reads.
  - Added skip/stop behavior for stale-size TOCTOU over-limit paths.
- `ContentSanitizer`:
  - Added `vbscript:` URI scheme filtering to dangerous URI detection.
- Regression tests added/updated:
  - `CopilotServiceTest`
  - `CustomInstructionSafetyValidatorTest`
  - `LocalFileCandidateProcessorTest`
  - `ContentSanitizerTest`

### Validation
- Focused tests passed: `mvn -Dtest=CopilotServiceTest,CustomInstructionSafetyValidatorTest,LocalFileCandidateProcessorTest,ContentSanitizerTest test`
- Result: 33 tests run, 0 failures, 0 errors
- PR #85 required checks passed: `CI/Build Native Image`, `CI/Build and Test`, `CI/Supply Chain Guard`, `Dependency Review`, `Automatic Dependency Submission`

### Merged PRs
- [#85](https://github.com/anishi1222/multi-agent-code-reviewer/pull/85): security hardening for token handling, sanitizer URI filtering, and TOCTOU checks

---

## 2026-02-19 (v5)

### Summary
- Added a concise operations summary for the 2026-02-19 v2-v4 sequence to README EN/JA.
- Captured the remediation progression in one line for quick operational handoff visibility.
- Merged the documentation update via PR #80.

### Highlights

#### PR #80: v2-v4 Operations Summary in README
- Added a one-line operations summary to `README_en.md` and `README_ja.md`.
- Summary explicitly tracks the progression: PR #74 (Java 25 CI alignment) → PR #76 (idle-timeout scheduler resilience) → PR #78 (operational completion check sync).

### Validation
- PR #80 required checks passed: `Supply Chain Guard`, `dependency-review`, `submit-maven`, `Build and Test`, `Build Native Image`

### Merged PRs
- [#80](https://github.com/anishi1222/multi-agent-code-reviewer/pull/80): add v2-v4 operations summary to README

---

## 2026-02-19 (v4)

### Summary
- Updated the operational completion checklist in README EN/JA to reflect 2026-02-19 completion status.
- Recorded PR #76 reliability remediation explicitly in the checklist section for clearer traceability.
- Merged the documentation synchronization via PR #78.

### Highlights

#### PR #78: Operational Completion Check Refresh
- Updated `README_en.md` and `README_ja.md` section date from 2026-02-18 to 2026-02-19.
- Replaced fixed test-count wording with full-suite passing status.
- Added explicit checklist item for PR #76 (idle-timeout scheduler shutdown fallback).

### Validation
- PR #78 required checks passed: `Supply Chain Guard`, `dependency-review`, `submit-maven`, `Build and Test`, `Build Native Image`

### Merged PRs
- [#78](https://github.com/anishi1222/multi-agent-code-reviewer/pull/78): refresh operational completion check for 2026-02-19

---

## 2026-02-19 (v3)

### Summary
- Fixed repeated review failures caused by idle-timeout scheduling on a terminated shared scheduler.
- Added resilient fallback behavior so review execution continues without idle watchdog when scheduling is unavailable.
- Added a regression test for the shutdown scheduler path and merged the fix via PR #76.

### Highlights

#### PR #76: Idle Timeout Scheduler Resilience
- Updated `IdleTimeoutScheduler.schedule(...)` to return a no-op `ScheduledFuture` when the scheduler is already shut down or rejects scheduling.
- Added warning logs for fallback path to preserve observability without failing review execution.
- Added test case in `IdleTimeoutSchedulerTest` for shutdown scheduler behavior.

### Validation
- Focused test passed: `mvn -Dtest=IdleTimeoutSchedulerTest test`
- Full package build passed: `mvn clean package`
- PR #76 required checks passed: `Supply Chain Guard`, `dependency-review`, `submit-maven`, `Build and Test`, `Build Native Image`

### Merged PRs
- [#76](https://github.com/anishi1222/multi-agent-code-reviewer/pull/76): tolerate idle-timeout scheduler shutdown during reviews

---

## 2026-02-19 (v2)

### Summary
- Aligned the CodeQL workflow Java setup with the project Java 25.0.2 policy by switching the workflow JDK from 26 to 25.
- Removed remaining CI/runtime JDK drift after `pom.xml` and main CI workflow had already been unified to Java 25.
- Merged the workflow alignment via PR #74.

### Highlights

#### PR #74: CodeQL Workflow JDK Alignment
- Updated `.github/workflows/codeql.yml` JDK setup from `26` to `25`.
- Kept repository-wide Java policy consistent across build files and GitHub Actions workflows.

### Validation
- Local package validation succeeded: `mvn clean package`
- PR #74 required checks passed: `Supply Chain Guard`, `dependency-review`, `submit-maven`, `Build and Test`, `Build Native Image`

### Merged PRs
- [#74](https://github.com/anishi1222/multi-agent-code-reviewer/pull/74): align CodeQL workflow JDK with Java 25

---

## 2026-02-19

### Summary
- Addressed the performance finding on multi-pass review session reuse by ensuring passes for the same agent reuse a single `CopilotSession`.
- Refactored orchestration from pass-granular execution to agent-granular execution while preserving multi-pass merge behavior.
- Added regression tests to verify single reviewer instance reuse and updated orchestrator tests for the new multi-pass contract.
- Merged all changes via PR #67.

### Highlights

#### PR #67: Multi-Pass Session Reuse and Orchestrator Refactor
- Added `ReviewAgent.reviewPasses(...)` to execute multiple passes with one shared session per agent.
- Updated `ReviewExecutionModeRunner` to execute one task per agent and collect per-pass results from that task.
- Updated `AgentReviewExecutor` to execute `reviewPasses(...)` with timeout handling and per-pass failure mapping.
- Updated `ReviewOrchestrator` wiring to use the new pass-aware reviewer execution flow.
- Added/updated tests:
  - `AgentReviewExecutorTest`: verifies single reviewer instance + single `reviewPasses` call for multi-pass
  - `ReviewExecutionModeRunnerTest`: validates async/structured collection with new list-based pass executor contract

### Validation
- CI checks passed on PR #67: `Supply Chain Guard`, `dependency-review`, `submit-maven`, `Build and Test`, `Build Native Image`
- Local compile validation: `mvn -q -DskipTests compile` succeeded

### Merged PRs
- [#67](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/67): reuse `CopilotSession` across passes and sync related updates

---

## 2026-02-18

### Summary
- Executed a third full review cycle (best-practices, multiple rounds) and addressed all findings across PRs #41–#65.
- Split the monolithic Azure WAF agent (`azure-waf.agent.md`) into 5 pillar-specific agents aligned with the Well-Architected Framework.
- Refactored the report package into sub-packages for improved modularity and visibility control.
- Applied StepSecurity hardening and added Dependabot configuration for GitHub Actions.
- Fixed `SkillService` thread-safety issue by using `Collections.synchronizedMap`.
- Test count increased from 722 to 730+ with additional nullable/defensive-copy tests.

### Highlights

#### PR #65: WAF Agent Split by Pillar
- Replaced single `azure-waf.agent.md` with 5 pillar-specific agents:
  - `waf-reliability.agent.md`, `waf-security.agent.md`, `waf-cost-optimization.agent.md`, `waf-operational-excellence.agent.md`, `waf-performance-efficiency.agent.md`
- Updated all SKILL.md agent references to point to the corresponding pillar agent

#### PR #44: Report Package Sub-Package Split
- Split `report/` package into `report/finding/`, `report/summary/`, `report/util/` sub-packages
- Optimized class visibility across 36 files

#### PRs #42, #43, #46, #47, #48: Best Practices Review Remediation (Rounds 1–5)
- Unified CLI output via `@Factory` + `@Named` PrintStream beans
- Added `LinkedHashMap` for stable agent ordering in `AgentConfigLoader`
- Grouped `OrchestratorConfig` prompt fields into `PromptTexts` record
- Grouped `ReviewContext` fields into `TimeoutConfig` and `CachedResources` records
- Added volatile to `ContentCollector` cache fields
- Added virtual thread name prefixes for observability
- Narrowed `FrontmatterParser` catch clause; used `loadAs()` for type safety
- Changed `ReviewResult` timestamp from `LocalDateTime` to `Instant`; injected `Clock` for testability
- Added `Locale.ROOT` to all `toLowerCase` calls
- Converted string concatenation to text blocks in `AgentPromptBuilder` and `CustomInstruction`
- Removed redundant code: unused `CommandExecutor` overload, `CopilotService` initialized flag, empty `FeatureFlags` constructor

#### PRs #61, #62: Best Practices Follow-Up Remediation
- Added compact constructor + builder to `ReviewCommand.ParsedOptions`
- Made `SkillService` LRU idiomatic via `LinkedHashMap.removeEldestEntry`
- Replaced generic `RuntimeException` with `SessionEventException` in `ContentCollector`
- Migrated `SkillResult` timestamp to `Instant` with `Clock`-based overloads
- Added `SkillConfig.defaults()` factory method
- Added exception object as final SLF4J argument in all catch-block logs (14 locations)

#### PR #56: SkillService Thread-Safety Fix
- Replaced `ConcurrentHashMap` with `Collections.synchronizedMap` for `SkillService.executorCache` to avoid `computeIfAbsent` deadlock risk

#### PR #49: StepSecurity Hardening
- Pinned GitHub Actions to SHA-based references in CI workflow
- Added `dependabot.yml` for automated GitHub Actions version updates

#### PR #60: CODEOWNERS and CI Hardening
- Pinned `oracle-actions/setup-java` in CodeQL workflow

#### PR #63: Missing Unit Tests
- Added unit tests for nullable and defensive-copy changes across 12 files (+203 insertions)

#### PR #64: README Fix
- Removed duplicate image reference from README files

### Validation
- Test count: 730+ tests (116 test classes, 0 failures, 0 errors)
- CI: All required checks passed on every PR

### Merged PRs
- [#41](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/41)–[#49](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/49), [#56](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/56), [#60](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/60)–[#65](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/65)

---

## 2026-02-17 (v2)

### Summary
- Executed a second full review cycle (best-practices / code-quality / performance / security) with the multi-agent code reviewer itself.
- Addressed all findings across 7 PRs (#34–#40), covering security hardening, performance optimization, code quality, best practices, and test coverage.
- Test count increased from 614 to 722 (+108 tests).

### Highlights

#### PR #34: Initial Review Findings (Security, Performance, Best Practices, Tests)
- Added `DANGEROUS_HTML_PATTERN` to `ContentSanitizer` for XSS prevention
- Added homoglyph normalization and delimiter injection detection to safety validator
- Added prompt injection validation for `.agent.md` files
- Fixed `ReviewResultMerger` O(N²) near-duplicate detection
- Reduced `ContentCollector` lock contention
- Added `@Serial serialVersionUID` to `CopilotCliException`
- Injected `CopilotTimeoutResolver` into `CopilotCliHealthChecker` (DRY)
- Added 91 new tests across 17 test files

#### PR #35: Best Practices Review
- Replaced volatile lazy init with Holder idiom in `LocalFileConfig`
- `SkillExecutor` now implements `AutoCloseable`
- `CustomInstructionLoader` supports Micronaut DI injection
- `ReviewTarget.isLocal()` uses exhaustive `switch` expression
- `GithubMcpConfig` uses named `MaskedToStringMap` class
- `SkillService` uses `LinkedHashMap(accessOrder=true)` for LRU cache
- Added `CliValidationException` cause-chain constructor
- `ReviewResult.Builder` marked `final`

#### PR #36: Code Quality Review
- Fixed `AgentMarkdownParser.extractFocusAreas()` immutable list bug (High)
- Restored `AggregatedFinding` record immutability (`addPass()` → `withPass()`)
- Removed dead `focusAreas() == null` check in `AgentConfigValidator`
- Consolidated `DEFAULT_LOCAL_REVIEW_RESULT_PROMPT` in `AgentPromptBuilder`
- Added `SummaryCollaborators.withDefaults()` method
- Flattened `AgentReviewExecutor` nested try-catch

#### PR #37: Performance Review
- **Critical**: Fixed `SkillService` `computeIfAbsent` deadlock risk
- Replaced 7-step `String.replace()` with single-pass `char[]` in `normalizeText()`
- Merged CoT+HTML patterns in `ContentSanitizer` (3 passes → 2)
- Added `find()` pre-check in `ContentSanitizationRule`
- Reduced `ContentCollector` StringBuilder from 64KB to 4KB

#### PR #38: Security Review
- Wrapped source code in `<source_code trust_level="untrusted">` delimiters
- Added `--trust` audit logging
- Extended `sensitive-file-patterns.txt` with generic config files
- Added `javascript:` and `data:base64` URI detection
- Windows `.exe`/`.cmd` support in `CliPathResolver`

#### PR #40: Test Alignment
- Updated `SkillExecutorTest` from deprecated `shutdown()` to `close()`
- Added 7 HTML/XSS sanitization tests to `ContentSanitizerTest`
- Added `LogbackLevelSwitcherTest` (new file)
- Added tests for cause-chain constructor, `generateReports()`, source code delimiters

### Validation
- Full test suite: 722 tests, 0 failures, 0 errors
- CI: All 5 checks passed on every PR (Build and Test, Native Image, Supply Chain Guard, Dependency Review, Dependency Submission)
- Runtime: `mvn clean package` + `run --repo ... --all` exit code 0

### Merged PRs
- [#34](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/34)–[#40](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/40)

---

## 2026-02-17

### Summary
- Completed the final remediation split workstream (PR-1 through PR-5).
- Closed remaining security/DI consistency backlog items and marked the final checklist as complete.
- Performed focused regression tests, full test validation, and end-to-end execution verification.

### Highlights
- PR-1: Changed `GithubMcpConfig.buildMcpServers()` to `Optional<Map<String,Object>>` (removed null-return contract).
- PR-2: Removed `resolvedToken` from `ReviewRunRequest`; token is now passed only at the execution boundary.
- PR-3: Introduced structured sandbox boundaries for `CustomInstruction` and explicitly enforced system-instruction precedence.
- PR-4: Added `LongSupplier` clock injection to `ContentCollector` and switched joined-cache validation to version-based invalidation.
- PR-5: Removed no-arg constructor from `CopilotService` and standardized on DI constructor usage.

### Validation
- Focused tests passed: `CopilotServiceTest`, `ReportServiceTest`, `SkillServiceTest`.
- Full suite validation: `mvn -q -DskipITs test`; confirmed 0 failures/errors in Surefire reports.
- Runtime validation: confirmed exit code 0 for `mvn clean package` and `run --repo ... --all` execution.

### References
- Final checklist: `reports/anishi1222/multi-agent-code-reviewer/final_remediation_checklist_2026-02-16.md`
- Final summary: `reports/anishi1222/multi-agent-code-reviewer/final_remediation_summary_2026-02-17.md`
- Release notes EN/JA alignment guide: `reports/anishi1222/multi-agent-code-reviewer/release_notes_bilingual_alignment_2026-02-17.md`
- Release tag: `v2026.02.17-notes`
- GitHub Release: https://github.com/anishi1222/multi-agent-code-reviewer-java/releases/tag/v2026.02.17-notes

## 2026-02-16

### Summary
- Continued the large-class decomposition track and completed modular splits across CLI, target collection, orchestrator, report pipeline, and Copilot startup flow.
- Preserved behavior while reducing command/service class responsibility density and improving testability through focused collaborators.
- Verified full regression safety after the latest `CopilotClientStarter` extraction.

### Highlights
- CLI refactoring:
  - `ReviewCommand` now delegates parsing/resolution/preparation/request-build/execution to dedicated classes.
  - `SkillCommand` now delegates parsing/preparation/execution/output formatting to dedicated classes.
  - Explicitly rejects direct token values for `--token` (stdin/env based flow only).
- Local source collection refactoring:
  - `LocalFileProvider` responsibilities split into candidate collection, candidate processing, content formatting, and normalized selection config.
- Orchestrator/report refactoring:
  - Added dedicated execution/result pipeline/context/precompute components in orchestrator.
  - Split report/summary internals into parser/formatter/builder helpers.
- Copilot startup refactoring:
  - `CopilotService` now delegates CLI path resolution, health checks, timeout resolution, startup error formatting, and startup execution to dedicated components.
  - Added `CopilotClientStarter` to isolate start-time timeout/cause mapping and safe close behavior.

### Validation
- Focused tests: 101 run, 0 failures, 0 errors
- Full test suite: 760 run, 0 failures, 0 errors

## 2026-02-14

### Summary
- Enforced supply-chain guardrails in Maven and GitHub Actions.
- Added CI required checks and validated branch protection behavior with a real PR.
- Documented dependency and CI policies in both English and Japanese READMEs.

### Highlights
- Maven policy hardening:
  - Checksum verification for Central artifacts is enforced.
  - SNAPSHOT dependencies/plugins are blocked via Maven Enforcer.
- Dependency Review policy hardening:
  - PRs fail on vulnerabilities with severity `moderate` or higher.
  - Denied licenses: `GPL-2.0`, `GPL-3.0`, `AGPL-3.0`, `LGPL-2.1`, `LGPL-3.0`.
- CI enforcement:
  - Added CI workflow jobs `Supply Chain Guard` and `Build and Test`.
  - Confirmed required checks are correctly recognized by branch protection.

### Branch Protection (main)
- Required checks:
  - `Supply Chain Guard`
  - `Build and Test`
  - `dependency-review`
- Additional protections:
  - `strict` status checks enabled
  - `enforce_admins` enabled
  - `required_conversation_resolution` enabled

### Merged PRs
- [#10](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/10): enforce dependency policy and required checks

### Affected Files (PR #10)
- `.github/workflows/dependency-review.yml`
- `README_en.md`
- `README_ja.md`

## 2026-02-15

### Summary
- Addressed all 26 findings (High 4 / Medium 10 / Low 12) reported by the 4 reviews (best-practices / code-quality / performance / security).
- Delivered improvements across security, maintainability, performance, and DI/config consistency.
- Merged PR #13 into `main` using squash merge.

### Highlights
- Security hardening:
  - Strengthened `CustomInstructionSafetyValidator` with regex-based detection and Unicode normalization (NFKC).
  - Applied safety validation to auto-loaded custom instructions.
  - Switched `TemplateService` template-name validation to allowlist + normalized path containment checks.
  - Masked auth header values in `GithubMcpConfig` string representation.
- Code quality and design:
  - Removed 3 legacy duplicate command classes (dead code).
  - Unified Micronaut DI usage for `FeatureFlags` and `SkillRegistry`.
  - Reduced complexity in `ReviewCommand.applyOption` by splitting into focused handlers.
  - Replaced wildcard imports with explicit imports.
- Performance:
  - Added one-pass local source collection API (`collectAndGenerate`) in `LocalFileProvider`.
  - Reused shared `ReviewContext`, converted `FindingsExtractor` to single-pass parsing, and reduced template expansion overhead in `SummaryGenerator`.

### Validation
- `mvn -B -ntp clean package`: BUILD SUCCESS
- Test result: 431 run, 0 failures, 0 errors

### Merged PRs
- [#13](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/13): fix: resolve all findings from 2026-02-15 reports

### Reference
- Detailed checklist: `reports/anishi1222/multi-agent-code-reviewer/remediation_checklist_2026-02-15.md`

## 2026-02-15 (v0.03)

### Summary
- Added multi-pass review capability allowing each agent to perform multiple review passes and merge results into a single report.
- Applied additional fixes for all review findings (High / Medium / Low) and strengthened test coverage.
- Improved CI workflow dependencies and added default configuration values for stability.

### Highlights

#### New Feature: Multi-Pass Review
- Each agent now performs `review-passes` iterations (default: 1), with results merged by `ReviewResultMerger`.
- All passes are submitted concurrently to a Virtual Thread pool, governed by `Semaphore(parallelism)` for concurrency control.
- Merge is string-concatenation only (no additional AI calls).
- Configurable via `reviewer.execution.review-passes` in `application.yml`.

#### Security Hardening
- Strengthened path validation in `ReviewTarget`.
- Added CLI path safety checks in `CliPathResolver`.
- Expanded prompt-injection detection and normalization in `CustomInstructionSafetyValidator`.
- Improved MCP header masking behavior in `GithubMcpConfig`.

#### Performance & Stability
- Improved retry behavior and timeout handling in `ReviewAgent`.
- Enhanced startup cleanup and summary fallback quality in `ReviewOrchestrator`.
- Added per-agent duplicate finding elimination in `ReviewResultMerger`.
- Refactored local file collection logic in `LocalFileProvider` for efficiency.
- Improved retry and error handling in `CopilotService`.

#### Configuration
- Added `AgentPathConfig` to externalize agent path settings.
- Added default values and `@Nullable` annotations to `LocalFileConfig`.
- Added default `reviewer.local-files` settings in `application.yml`.

#### Testing
- Added `ReviewResultMergerTest` (9 test cases).
- Added/expanded `AgentPathConfigTest`, `LocalFileConfigTest`, `ReviewTargetTest`, `LocalFileProviderTest`, `CommandExecutorTest`, `CliPathResolverTest`.
- Total tests: 453 (0 failures, 0 errors).

#### Documentation
- Updated README (EN/JA) with multi-pass review feature description, configuration examples, and architecture diagram.

### Merged PRs
- [#16](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/16): fix: add default reviewer.local-files settings
- [#17](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/17): fix: update build-native-image job dependencies
- [#18](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/18): fix: remediate 2026-02-15 report findings (all severities)
- [#19](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/19): feat: multi-pass review
- [#20](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/20): docs: add multi-pass review to README
- [#21](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/21): fix: all review findings and strengthen coverage

### Validation
- `mvn test`: 453 run, 0 failures, 0 errors
- CI: Build and Test / Native Image Build / Supply Chain Guard / Dependency Review all passed
