# t5 — Phased Implementation Plan

## Phasing Rationale

Phases are ordered by the **dependency direction** rule (constitution §2): innermost layers first, outermost last. Within each phase, tasks split by **vertical business module** (per charter: "Split by vertical business module, NOT by technical layer"). Cycle-root types (`ReviewResult`, `AgentConfig`, `SharedCircuitBreaker`) move in Phase 1 to unblock all downstream moves.

---

## Phase 1: Foundation — Shared, Domain Types, and Port Interfaces

**Goal**: Establish the innermost layers that everything else depends on. Zero external framework dependencies in these files.

**Why first**: Per cycle hub analysis (t2), `TemplateService` causes 5 cycles, `AgentConfig`/`ReviewResult` cause 5 cycles, `SharedCircuitBreaker` causes 2 cycles. Moving these cycle-root domain types and defining port interfaces unblocks all subsequent file moves.

| Task | Module | Description | Req Traceability |
|------|--------|-------------|------------------|
| T001 | shared | Move/create all 10 `shared` utility files (PlaceholderUtils, ExecutorUtils, etc.). Remove `SharedCircuitBreaker` import from `RetryExecutor` (accept as parameter). | §1, §2, §7.1 |
| T002 | domain-core | Move cycle-root domain types: `AgentConfig`, `ReviewResult`, `SharedCircuitBreaker`, `SynthesisStrategy`, `SkillDefinition`, `SkillParameter`, `SkillResult`, `LocalFileCandidate`, `LocalFileSelectionConfig`, `ReviewTarget`, `PromptTexts`, `InstructionFrontmatter`, `CustomInstructionSafetyValidator`. Purify all (remove `@Nullable` → `Optional`, remove Micronaut/SLF4J imports). Create domain exceptions (`CopilotCliException`, `AgentValidationException`). | §1, §3, §7.5, §7.6, §7.7 |
| T003 | ports | Create all 12 port interfaces in `application.port.inbound` (5) and `application.port.outbound` (7) per t4-architect-ports.md. | §4, port catalog |

Phase 1 tasks are **sequential**: T001 → T002 → T003 (domain types reference `shared`; ports reference `domain` types).

---

## Phase 2: Agent & Review Domain + Application

**Goal**: Rebuild the agent subsystem (the largest decomposition: 30 files → 4 layers) and the review orchestration use-cases.

| Task | Module | Description | Req Traceability |
|------|--------|-------------|------------------|
| T004 | agent | Move agent domain models + pure logic (12 files): `AgentConfigValidator`, `AgentDefinitionPolicy`, `AgentPromptBuilder`, `AgentSectionParser`, `AgentFrontmatterMapper`, `ReviewSystemPromptFormatter`, `ReviewTargetInstructionResolver`, `RubberDuckPromptBuilder`, `ParsedAgentMetadata`, `DialogueRound`, `ReviewAgent`, `ReviewResultFactory`. Purify (remove SLF4J, replace FrontmatterParser with port call). Move `ReviewContext` to `domain.review` with SDK types extracted to port params. | AGT-01–13, §3 |
| T005 | review-orchestration | Create `application.review` use-cases: `ReviewOrchestrator` (implements `RunReviewPort`), `ReviewPassRunner`, `ReviewRetryExecutor`, `RubberDuckDialogueRunner`, `ReviewExecutionModeRunner`, `OrchestratorMetrics`, `ReviewResultPipeline`, `LocalSourcePrecomputer`, `AgentReviewExecutor`, `ExecutorResources`, `OrchestratorConfig`. All reference ports, not infrastructure. | ORC-01–10, RTY-01–04 |

T004 and T005 can run in **parallel** [P] — T004 writes domain, T005 writes application; no file overlap.

---

## Phase 3: Report & Skill Domain + Application

**Goal**: Rebuild report subsystem (22 files, 4 internal cycles) and skill subsystem.

| Task | Module | Description | Req Traceability |
|------|--------|-------------|------------------|
| T006 | report | Move report domain models + pure logic (16 files) to `domain.report`: all finding/formatter/sanitize/merger/summary builders. Remove SLF4J from `FallbackSummaryBuilder`. Break cycle 9 (finding ⇄ formatter) by removing `FindingsExtractor` → `FindingsSummaryFormatter` direct import (use data flow: Extractor produces `AggregatedFinding`, Formatter consumes it). Create `application.report`: `GenerateReportUseCase` (implements `GenerateReportPort`), `SummaryGenerator`. | OUT-01–09, §3 |
| T007 | skill | Create `application.skill.ExecuteSkillUseCase` (implements `ExecuteSkillPort`). `LoadAgentUseCase` in `application.agent` (implements `LoadAgentPort`). Domain types already moved in T002. | SKL-01–08 |
| T008 | diagnostics | Create `application.review.RunDiagnosticsUseCase` (implements `RunDiagnosticsPort`). Thin use-case delegating to `ManageCopilotClientPort`. | AUTH-09 |

T006, T007, T008 can run in **parallel** [P] — separate sub-packages, no file overlap.

---

## Phase 4: Infrastructure Adapters

**Goal**: Move all infrastructure concerns (SDK, file I/O, config, auth, parsing, logging, template) and wire port implementations.

| Task | Module | Description | Req Traceability |
|------|--------|-------------|------------------|
| T009 | infra-copilot | Move to `infrastructure.copilot`: `CopilotService` (implements `ManageCopilotClientPort`), `CopilotClientStarter`, `CopilotHealthProbe`, `ReviewSessionConfigFactory`, `ReviewSessionExecutor` (implements `RunCopilotSessionPort`), `ReviewSessionMessageSender`, `ReviewMessageFlow`, `RubberDuckDialogueExecutor` (implements `RunRubberDuckSessionPort`), `SdkRubberDuckSessionFactory`, `ReviewContextFactory`, `ReviewOrchestratorFactory`, `ReportGeneratorFactory`, `CopilotPermissionHandlers`, `SkillExecutor`, `AiSummaryClient` (implements `GenerateAiSummaryPort`), `CircuitBreakerFactory`. | AUTH-01–08, AUTH-10–11, ORC-02–03, ORC-06, ORC-08 |
| T010 | infra-support | Move `infrastructure.config` (11 config records), `infrastructure.template` (`TemplateRepository` implements `LoadTemplatePort`), `infrastructure.file` (7 files, `LocalFileProvider` implements `CollectLocalSourcePort`, `ReportFileWriter` implements `WriteReportPort`), `infrastructure.auth` (7 files), `infrastructure.logging` (2 files), `infrastructure.parsing` (5 files). | TGT-01–09, OUT-02–03–06, §7.6, §7.7 |

T009 and T010 can run in **parallel** [P] — separate sub-packages.

---

## Phase 5: Presentation Layer + ArchUnit Tests

**Goal**: Move CLI layer, wire DI, and enforce boundaries.

| Task | Module | Description | Req Traceability |
|------|--------|-------------|------------------|
| T011 | presentation | Move all 28 `cli` files + `ReviewApp` to `presentation` with sub-packages (`command`, `formatter`, `parser`). Replace direct service calls with inbound port calls. DI wiring (`@Inject` on port implementations) stays in presentation. | §1, §8, all CLI behaviors |
| T012 | archunit | Create `src/test/java/dev/logicojp/reviewer/architecture/LayerDependencyRulesTest.java`. Rules: (1) domain imports only java.*/shared/domain, (2) shared imports only java.*, (3) no layer imports presentation, (4) infrastructure imports only port.outbound/domain/shared, (5) zero package cycles, (6) no SDK outside infrastructure, (7) no Micronaut/Jakarta in domain/shared. | §2, §3, §6 |

T011 → T012 **sequential** (ArchUnit validates the presentation wiring).

---

## Phase 6: Test Migration, Build Verification, and Smoke Test

**Goal**: Migrate existing 148 test files to new packages, verify full build, run all tests.

| Task | Module | Description | Req Traceability |
|------|--------|-------------|------------------|
| T013 | test-migration | Migrate test files to mirror new package structure. Update imports. Preserve all test logic — no behavior changes. | §7.3 |
| T014 | build-verify | Full Maven build: `mvn -B clean verify`. Verify shade JAR. Verify native-image build: `mvn -B clean verify -Pnative` (if GraalVM available). | §7.2 |
| T015 | regression | Run full test suite. All 148 test files must pass. ArchUnit tests (T012) must pass. Zero test regressions. | §7.3, §7.8 |
| T016 | smoke-test | Start the built application, run `review doctor`, verify exit code 0. Run `review list` and verify agent listing. Verify `review --help` output. | AUTH-09, AGT-01 |

T013 → T014 → T015 → T016 **strictly sequential**.

---

## Requirement Traceability Matrix

| Requirement Source | Covered By Tasks |
|---|---|
| Constitution §1 (layer model) | T001–T012 (all placement) |
| Constitution §2 (dep direction) | T012 (ArchUnit enforcement) |
| Constitution §3 (domain purity) | T002, T004, T006, T012 |
| Constitution §4 (port convention) | T003 |
| Constitution §6 (boundary enforcement) | T012 |
| Constitution §7.1 (in-place rewrite) | All tasks |
| Constitution §7.2 (build preservation) | T014 |
| Constitution §7.3 (test preservation) | T013, T015 |
| Constitution §7.5 (zero cycles) | T012 |
| Constitution §7.6 (SDK isolation) | T009, T012 |
| Constitution §7.7 (framework isolation) | T009, T010, T012 |
| Constitution §7.8 (behavior preserved) | T015, T016 |
| PM behaviors AGT-01–13 | T004, T005, T015 |
| PM behaviors SKL-01–08 | T007, T015 |
| PM behaviors INS-01–05 | T002 (domain.instruction), T015 |
| PM behaviors TGT-01–09 | T010, T015 |
| PM behaviors ORC-01–10 | T005, T009, T015 |
| PM behaviors AUTH-01–11 | T009, T010, T016 |
| PM behaviors RTY-01–04 | T005, T015 |
| PM behaviors OUT-01–09 | T006, T010, T015 |
