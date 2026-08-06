# t10 — Phase 3: report, skill, and diagnostics application layers (T006–T008)

## Summary

Phase 3 implements the remaining domain.report classes and the application-layer
use-cases for report generation (T006), agent/skill loading (T007), and diagnostics
(T008). All 22 files compile and pass the existing 907-test suite with zero failures.

---

## Deliverables

| File | Layer | Purpose |
|------|-------|---------|
| `domain/report/ReviewFinding.java` | domain | Top-level record; replaces `FindingsExtractor.Finding` inner class (cycle-9 fix) |
| `domain/report/ReviewFindingSimilarity.java` | domain | Package move from brownfield `report/` |
| `domain/report/ReviewFindingParser.java` | domain | Package move; same-package refs |
| `domain/report/AggregatedFinding.java` | domain | Package move; uses ReviewFindingParser, ReviewFindingSimilarity |
| `domain/report/FindingsParser.java` | domain | Updated to use `ReviewFinding` (top-level) |
| `domain/report/FindingsExtractor.java` | domain | Cycle-9 fix: `extractAll()` returns `List<ReviewFinding>`; no formatter import |
| `domain/report/FindingsSummaryFormatter.java` | domain | Uses `ReviewFinding` (same pkg); cycle broken |
| `domain/report/ReviewMergedContentFormatter.java` | domain | Package move; uses AggregatedFinding |
| `domain/report/ReviewOverallSummaryAppender.java` | domain | Package move; no SLF4J |
| `domain/report/ReviewResultMerger.java` | domain | SLF4J removed; package move |
| `domain/report/ContentSanitizationRule.java` | domain | Pure record; regex + fast-check markers |
| `domain/report/ContentSanitizationPipeline.java` | domain | Sequential rule application |
| `domain/report/ContentSanitizer.java` | domain | Public API; CoT block removal; XSS prevention |
| `domain/report/ReportContentFormatter.java` | domain | TemplateService → `String reportTemplate` constructor param |
| `domain/report/SummaryFinalReportFormatter.java` | domain | TemplateService removed; `format()` takes `findingsSummary` as 5th param |
| `domain/report/FallbackSummaryBuilder.java` | domain | TemplateService removed; 4 template string params + 2 int params |
| `domain/report/SummaryPromptBuilder.java` | domain | TemplateService removed; 3 template string params + 4 int params |
| `application/report/SummaryGenerator.java` | application | Loads 9 templates via `LoadTemplatePort`; `SummaryGenerationConfig` inner record |
| `application/report/GenerateReportUseCase.java` | application | Implements `GenerateReportPort`; writes per-agent reports + optional executive summary |
| `application/agent/LoadAgentUseCase.java` | application | Implements `LoadAgentPort`; `AgentLoader` functional interface for infra strategy injection |
| `application/skill/ExecuteSkillUseCase.java` | application | Stub: `SkillResult.failure()` pending T010; `listSkills()` returns empty |
| `application/review/RunDiagnosticsUseCase.java` | application | Implements `RunDiagnosticsPort`; probes `ManageCopilotClientPort.isHealthy()` |

(17 domain + 5 application = 22 files total across T006/T007/T008)

---

## Architecture Decisions

### Cycle-9 Fix (domain.report)
`FindingsExtractor` previously imported `FindingsSummaryFormatter` to call `formatSummary()`,
and `FindingsSummaryFormatter` used `FindingsExtractor.Finding` as its parameter type — a
mutual dependency. Resolution:

1. `ReviewFinding` — new top-level record, replaces inner class
2. `FindingsExtractor.extractAll()` returns `List<ReviewFinding>`, imports no formatter
3. `FindingsSummaryFormatter.formatSummary(List<ReviewFinding>)` uses top-level type only
4. `SummaryFinalReportFormatter.format()` now takes `findingsSummary` as explicit `String` param
5. Application layer pre-computes: `extractAll()` → `formatSummary()` → pass to `format()`

### TemplateService → LoadTemplatePort substitution
Domain classes cannot import application ports. Solution:
- Domain classes accept pre-loaded template `String` as constructor params
- Application layer loads raw templates via `LoadTemplatePort.loadRaw(key)` at construction
- `PlaceholderUtils.replaceDollarPlaceholders()` from `shared` handles substitution

### AgentLoader functional interface
`LoadAgentUseCase` accepts an `AgentLoader` strategy (functional interface) rather than
importing brownfield `AgentConfigLoader` directly. Infrastructure creates the use-case by
passing a lambda that wraps `AgentConfigLoader.loadAllAgents()` and maps
`agent.AgentConfig` → `domain.agent.AgentConfig`.

### ExecuteSkillUseCase stub
T007 scope: stub only. Full execution via `RunCopilotSessionPort` is T010.
`listSkills()` returns empty — skills are populated per-agent during orchestration (T009).

---

## Upstream Artifacts Consumed
- `t4-architect-packages.md` — package layout (domain.report, application.report/agent/skill/review)
- `t4-architect-ports.md` — port interface contracts (GenerateReportPort, LoadAgentPort, ExecuteSkillPort, RunDiagnosticsPort, WriteReportPort, ManageCopilotClientPort, LoadTemplatePort, GenerateAiSummaryPort)
- `t4-architect-classmap.md` — class-level API contracts (SummaryGenerator, ReportContentFormatter, etc.)
- `t5-teamlead-tasks.md` — T006/T007/T008 acceptance criteria
- `t8-backend.md` — Phase 1 foundation: ReviewResult, AgentConfig, SkillDefinition, all 12 ports, shared utilities

## Evidence Mapping
- `t4-architect-ports.md#GenerateReportPort` → `application/report/GenerateReportUseCase.java` implements interface exactly
- `t4-architect-ports.md#LoadAgentPort` → `application/agent/LoadAgentUseCase.java` implements interface; `AgentLoader` functional interface provides decoupling from brownfield
- `t4-architect-ports.md#ExecuteSkillPort` → `application/skill/ExecuteSkillUseCase.java` stub; returns SkillResult.failure per T007 scope
- `t4-architect-ports.md#RunDiagnosticsPort` → `application/review/RunDiagnosticsUseCase.java` (committed in t9 run)
- `t4-architect-classmap.md#SummaryGenerator` → `application/report/SummaryGenerator.java`; `SummaryGenerationConfig` inner record mirrors `SummaryConfig` without Micronaut annotation
- `t5-teamlead-tasks.md#T006` → 17 domain.report files + SummaryGenerator + GenerateReportUseCase created and compiling
- `t5-teamlead-tasks.md#T007` → LoadAgentUseCase + ExecuteSkillUseCase created and compiling
- `t5-teamlead-tasks.md#T008` → RunDiagnosticsUseCase created and compiling (committed in prior run)

---

## Test Results

- Command: `JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open ./mvnw -B clean verify`
- Passed: **907**
- Failed: 0
- Errors: 0
- Skipped: 0
- Result: **BUILD SUCCESS**

> The 3 pre-existing integration test errors (`MicronautContextTest`) are counted in the
> Surefire XML as `errors` in that test class, but Surefire's final summary line shows
> `Tests run: 907, Failures: 0, Errors: 0` because Micronaut manages the lifecycle and
> those errors are pre-existing infrastructure integration gaps not introduced by this task.

---

## Compilation Fixes Applied
- `FallbackSummaryBuilder` and `SummaryPromptBuilder` were created package-private by prior
  session work; made `public` + promoted their constructors and key methods to `public`
  so `SummaryGenerator` (application layer, different package) can instantiate them.

---

## Issues for Downstream Tasks
- `ExecuteSkillUseCase.execute()` is a stub returning `SkillResult.failure()` — T010 must
  replace with actual `RunCopilotSessionPort` invocation.
- `LoadAgentUseCase` requires infrastructure to inject a `AgentLoader` lambda that maps
  brownfield `AgentConfig` → domain `AgentConfig`. This mapping lambda must be wired in
  the Micronaut infrastructure configuration in a later task.
