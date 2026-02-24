# Release Notes

## 2026-02-24

### Local File Orchestration Refactor + Regression Coverage (PR #37)

- extracted local file collection coordination into `LocalFileCollectionCoordinator`
- simplified local file provider flow and renamed `LocalFileContentReader` to `LocalFileReader`
- reduced merger complexity by extracting finding deduplication (`ReviewFindingDeduplicator`)
- consolidated retry/backoff loops across `SkillExecutor`, `SummaryGenerator`, and `CopilotService`
- strengthened config safety by switching `ExecutionConfig.withParallelism()` to builder-copy
- added broad regression tests for orchestrator/report/service/skill/target/util paths

PR: #37

### Reliability / Security / Performance Follow-up

	- timeout hierarchy warning in `ReviewOrchestrator`
	- bounded idle-timeout scheduler pool (replacing single-thread scheduler)
	- session-establishment retry path in `SummaryGenerator`
	- per-pass session isolation in `ReviewAgent` multi-pass flow
	- equal-jitter retry strategy in `BackoffUtils`
	- adaptive open-duration growth on repeated half-open failures in `ApiCircuitBreaker`
	- operation-specific default merge behavior in `ResilienceConfig`
	- named HTML entity decoding before sanitization in `ContentSanitizer`
	- secure checkpoint write path via owner-only permissions.
	- synchronized `HOWTO_en.md` and `HOWTO_ja.md`
	- updated examples/config values (`orchestrator-timeout-minutes: 200`) and completion checklist.

## 2026-02-20

### WAF Reliability Remediation

- Introduced half-open probe and per-operation isolation (review/summary/skill) in `ApiCircuitBreaker`
- Added transient-failure classification in `ReviewAgent` to skip non-retryable errors
- Added checkpoint recovery path in `ReviewOrchestrator`
- Eliminated unbounded `startClient()` wait in `CopilotService`
- Externalized resilience parameters via `ResilienceConfig` + `application.yml`
- Applied dedicated CB and retry settings to `SummaryGenerator` / `SkillExecutor`

PRs: #23 (implementation), #24 (report integration), #25 (README sync)
GitHub Release: https://github.com/anishi1222/multi-agent-code-reviewer-v2/releases/tag/v2026.02.20-reliability

## 2026-02-19

### v12 — Best-Practices Remediation

- Simplified `TemplateService` cache synchronization with deterministic LRU behavior
- Replaced `SkillService` manual executor-cache management with Caffeine eviction + close-on-evict
- Abstracted CLI token input handling (`CliParsing.TokenInput`) from direct system I/O
- Simplified `ContentCollector` joined-content cache locking
- Improved section parsing readability in `AgentMarkdownParser`
- Made multi-pass start logging in `ReviewExecutionModeRunner` accurate
- Completed delegation methods in `GithubMcpConfig` map wrappers
- Simplified `ReviewResult` default timestamp handling
- Removed FQCN utility usage in `SkillExecutor`
- Clarified concurrency/threading design intent in `CopilotService` and `ReviewOrchestrator`

### v11 — Code Quality Remediation

- Centralized token hashing via shared `TokenHashUtils`
- Unified orchestrator failure-result generation with `ReviewResult.failedResults(...)`
- Extracted orchestrator nested types (`OrchestratorConfig`, `PromptTexts`, collaborator interfaces/records) into top-level package types
- Refactored scoped-instruction loading to avoid stream-side-effect try/catch blocks
- Introduced grouped execution settings (`ConcurrencySettings`, `TimeoutSettings`, `RetrySettings`, `BufferSettings`) with factory access
- Removed dead code (`ReviewResultPipeline.collectFromFutures`) and unused similarity field
- Added dedicated command tests for `ReviewCommand` / `SkillCommand`

### v10 — Performance + WAF Security Hardening

- Eliminated redundant finding-key extraction in merge flow
- Added prefix-indexed near-duplicate lookup
- Optimized local file read buffer sizing
- Precompiled fallback whitespace regex
- Introduced structured security audit logging
- Enforced SDK WARN level even in verbose mode
- Applied owner-only report output permissions on POSIX
- Added Maven `dependencyConvergence`
- Added weekly OWASP dependency-audit workflow

### v9 — Security Follow-up Closure

- Expanded suspicious-pattern validation for agent definitions to all prompt-injected fields
- Strengthened MCP header masking paths (`entrySet`/`values` stringification)
- Reduced token exposure by deferring `--token -` stdin materialization to resolution time

### v8 — Naming-Rule Alignment

- Synchronized executive summary output to `reports/{owner}/{repo}/executive_summary_yyyy-mm-dd-HH-mm-ss.md` (CLI invocation timestamp)
- Aligned README EN/JA examples + tests

### v7 — Security Report Follow-up

- Synchronized `LocalFileConfig` fallback sensitive file patterns with resource defaults
- Added an opt-in `security-audit` Maven profile (`dependency-check-maven`)

### v6 — Release Documentation Rollup

- Published the 2026-02-19 daily rollup section in RELEASE_NOTES EN/JA

### v5 — Documentation Refinement

- Added concise operations summary for the v2-v4 progression

### v4 — Documentation Sync

- Refreshed Operational Completion Check to 2026-02-19 and recorded PR #76 completion

### v3 — Reliability Remediation

- Tolerate idle-timeout scheduler shutdown to prevent `RejectedExecutionException` retry storms

### v2 — CI Consistency Remediation

- Aligned CodeQL workflow JDK from 26 to 25 to match Java 25.0.2 policy

### v1 — Multi-Pass Review Performance Remediation

- Reuse `CopilotSession` across passes in the same agent
- Refactor orchestration to per-agent pass execution

Operations summary (v2–v4): Java 25 CI alignment (PR #74) → idle-timeout scheduler resilience fix (PR #76) → operational completion checklist sync (PR #78)

## 2026-02-18

### Best Practices Review Remediation

- Compact constructors & defensive copies
- SLF4J stack trace logging improvements
- Config record extensions
- `SkillConfig.defaults()` factory method

## 2026-02-17

### v2 — PRs #34–#40

- Security, performance, code quality, best practices fixes
- 108 new tests added

### v1 — PRs #22–#27

- Final remediation (PR-1 to PR-5)
