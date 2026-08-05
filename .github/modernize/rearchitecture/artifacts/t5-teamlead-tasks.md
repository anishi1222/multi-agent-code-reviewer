# t5 — Task Breakdown

Each task is independently executable by one agent in one session. Dependencies are explicit.

---

## T001: Create shared layer utilities

- **Role**: backend
- **Module**: shared
- **Depends on**: none
- **Parallel**: no (foundation)
- **Files to create/move** (10 files):
  - `shared/PlaceholderUtils.java` ← `util/PlaceholderUtils`
  - `shared/ExecutorUtils.java` ← `util/ExecutorUtils`
  - `shared/StructuredConcurrencyUtils.java` ← `util/StructuredConcurrencyUtils`
  - `shared/RetryExecutor.java` ← `util/RetryExecutor` — **CRITICAL: remove `SharedCircuitBreaker` import; accept circuit breaker behavior as `java.util.function.Predicate<Exception>` parameter**
  - `shared/RetryPolicyUtils.java` ← `util/RetryPolicyUtils`
  - `shared/TokenHashUtils.java` ← `util/TokenHashUtils`
  - `shared/ExecutionCorrelation.java` ← `util/ExecutionCorrelation`
  - `shared/ReportFilenameUtils.java` ← `report/util/ReportFilenameUtils`
  - `shared/ConfigDefaults.java` ← `config/ConfigDefaults`
  - `shared/SensitiveHeaderMasking.java` ← `config/SensitiveHeaderMasking`
- **Acceptance criteria**:
  - All files import only `java.*`
  - No DI annotations, no framework imports
  - Existing tests for these classes updated to new package and pass
- **Source**: t4-architect-classmap.md §11 (util) + §4 (config)
- **REQ**: §1, §2, §7.1

---

## T002: Create domain core types and purify

- **Role**: backend
- **Module**: domain-core
- **Depends on**: T001
- **Parallel**: no
- **Files to create/move** (22+ files across 6 domain sub-packages):
  - `domain.agent/AgentConfig` — remove `@Nullable` → `Optional`, remove Micronaut imports
  - `domain.agent/ReviewAgent`, `domain.agent/ParsedAgentMetadata`, `domain.agent/DialogueRound`
  - `domain.report/ReviewResult` — remove `@Nullable` → `Optional`, remove Micronaut imports; **cycle root — move first**
  - `domain.report/AggregatedFinding`
  - `domain.skill/SkillDefinition`, `domain.skill/SkillParameter`, `domain.skill/SkillResult`
  - `domain.instruction/CustomInstructionSafetyValidator`, `domain.instruction/InstructionFrontmatter`
  - `domain.review/LocalFileCandidate`, `domain.review/LocalFileSelectionConfig`, `domain.review/ReviewTarget`, `domain.review/PromptTexts`, `domain.review/ReviewContext` — extract SDK types to DTOs
  - `domain.resilience/SharedCircuitBreaker`, `domain.resilience/SynthesisStrategy`
  - `domain.resilience/CopilotCliException`, `domain.resilience/CopilotStartupErrorFormatter`, `domain.resilience/CopilotTimeoutResolver`
- **Acceptance criteria**:
  - Zero imports of `io.micronaut.*`, `jakarta.*`, `com.github.copilot.*`, `org.slf4j.*`, `org.yaml.*` in any domain file
  - All domain files import only `java.*`, `shared.*`, other `domain.*` types
  - Tests updated and pass
- **Source**: t4-architect-classmap.md §2,§5,§7,§8,§9,§10; t2-architect-leakage.md
- **REQ**: §1, §3, §7.5, §7.6, §7.7, INS-01–05

---

## T003: Create port interfaces

- **Role**: backend
- **Module**: ports
- **Depends on**: T002
- **Parallel**: no
- **Files to create** (12 new files):
  - `application.port.inbound/RunReviewPort`
  - `application.port.inbound/LoadAgentPort`
  - `application.port.inbound/ExecuteSkillPort`
  - `application.port.inbound/GenerateReportPort`
  - `application.port.inbound/RunDiagnosticsPort`
  - `application.port.outbound/LoadTemplatePort`
  - `application.port.outbound/RunCopilotSessionPort`
  - `application.port.outbound/RunRubberDuckSessionPort`
  - `application.port.outbound/ManageCopilotClientPort`
  - `application.port.outbound/CollectLocalSourcePort`
  - `application.port.outbound/WriteReportPort`
  - `application.port.outbound/GenerateAiSummaryPort`
- **Acceptance criteria**:
  - Signatures match t4-architect-ports.md §2–§3 exactly
  - Ports import only `domain.*`, `shared.*`, `java.*`
  - Supporting domain DTOs (e.g., `ReviewRequest`, `SessionRequest`, `ReportOptions`, `ReportOutput`, `DiagnosticResult`, `RubberDuckRequest`) created alongside ports or in appropriate domain sub-packages
- **Source**: t4-architect-ports.md
- **REQ**: §4

---

## T004: Migrate agent domain models and pure logic [P]

- **Role**: backend
- **Module**: agent
- **Depends on**: T003
- **Parallel**: yes (with T005)
- **Files** (12): `AgentConfigValidator`, `AgentDefinitionPolicy`, `AgentPromptBuilder`, `AgentSectionParser`, `AgentFrontmatterMapper`, `ReviewSystemPromptFormatter`, `ReviewTargetInstructionResolver`, `RubberDuckPromptBuilder`, `ReviewResultFactory` → all to `domain.agent` (or `domain.report` for ReviewResultFactory)
- **Purification**: Remove SLF4J (use `java.util.logging` or return results). Replace `FrontmatterParser` calls with port parameter (parsed data passed in). Remove `@Nullable` → `Optional`.
- **Acceptance criteria**: All 12 files import only `java.*`/`shared.*`/`domain.*`. Tests updated, pass.
- **Source**: t4-architect-classmap.md §2
- **REQ**: AGT-01–13, §3

---

## T005: Create review orchestration use-cases [P]

- **Role**: backend
- **Module**: review-orchestration
- **Depends on**: T003
- **Parallel**: yes (with T004)
- **Files** (11): `ReviewOrchestrator` (implements `RunReviewPort`), `ReviewPassRunner`, `ReviewRetryExecutor`, `RubberDuckDialogueRunner`, `ReviewExecutionModeRunner`, `OrchestratorMetrics`, `ReviewResultPipeline`, `LocalSourcePrecomputer`, `AgentReviewExecutor`, `ExecutorResources`, `OrchestratorConfig` → all to `application.review`
- **Key change**: All SDK/infrastructure references replaced with outbound port calls. `OrchestratorConfig` → remove `@Nullable`, remove `TemplateService` reference.
- **Acceptance criteria**: `application.review` imports only `application.port.*`, `domain.*`, `shared.*`, `java.*`. No infrastructure imports. Tests updated, pass.
- **Source**: t4-architect-classmap.md §6
- **REQ**: ORC-01–10, RTY-01–04

---

## T006: Migrate report domain + application [P]

- **Role**: backend
- **Module**: report
- **Depends on**: T003
- **Parallel**: yes (with T007, T008)
- **Files** (18):
  - `domain.report`: `FindingsExtractor`, `FindingsParser`, `ReviewFindingParser`, `ReviewFindingSimilarity`, `FindingsSummaryFormatter`, `ReportContentFormatter`, `ReviewMergedContentFormatter`, `SummaryFinalReportFormatter`, `ReviewOverallSummaryAppender`, `ReviewResultMerger`, `ContentSanitizationPipeline`, `ContentSanitizationRule`, `ContentSanitizer`, `FallbackSummaryBuilder`, `SummaryPromptBuilder`
  - `application.report`: `GenerateReportUseCase` (implements `GenerateReportPort`), `SummaryGenerator`
- **Key change**: Break cycle 9 — `FindingsExtractor` must NOT import `FindingsSummaryFormatter`. Data flow: Extractor produces `List<AggregatedFinding>`, Formatter consumes it. Remove SLF4J from `FallbackSummaryBuilder`.
- **Acceptance criteria**: Zero intra-domain cycles. Domain report files import only `java.*`/`shared.*`/`domain.*`. Application report uses only ports. Tests pass.
- **Source**: t4-architect-classmap.md §7, t2-architect-cycles.md cycle 9
- **REQ**: OUT-01–09, §3

---

## T007: Migrate skill and agent application [P]

- **Role**: backend
- **Module**: skill
- **Depends on**: T003
- **Parallel**: yes (with T006, T008)
- **Files** (3):
  - `application.skill/ExecuteSkillUseCase` ← `service/SkillService` (implements `ExecuteSkillPort`)
  - `application.agent/LoadAgentUseCase` ← `service/AgentService` (implements `LoadAgentPort`)
  - Eliminate `TemplateService` — replaced by `LoadTemplatePort` usage
- **Acceptance criteria**: Application classes use ports only. Tests pass.
- **Source**: t4-architect-classmap.md §8
- **REQ**: SKL-01–08, AGT-01–13

---

## T008: Create diagnostics use-case [P]

- **Role**: backend
- **Module**: diagnostics
- **Depends on**: T003
- **Parallel**: yes (with T006, T007)
- **Files** (1): `application.review/RunDiagnosticsUseCase` (implements `RunDiagnosticsPort`). Delegates to `ManageCopilotClientPort`.
- **Acceptance criteria**: No SDK imports. Tests pass.
- **Source**: t4-architect-ports.md §2.5
- **REQ**: AUTH-09

---

## T009: Migrate Copilot SDK infrastructure [P]

- **Role**: backend
- **Module**: infra-copilot
- **Depends on**: T005, T006, T007, T008
- **Parallel**: yes (with T010)
- **Files** (16): `CopilotService`, `CopilotClientStarter`, `CopilotHealthProbe`, `ReviewSessionConfigFactory`, `ReviewSessionExecutor`, `ReviewSessionMessageSender`, `ReviewMessageFlow`, `RubberDuckDialogueExecutor`, `SdkRubberDuckSessionFactory`, `ReviewContextFactory`, `ReviewOrchestratorFactory`, `ReportGeneratorFactory`, `CopilotPermissionHandlers`, `SkillExecutor`, `AiSummaryClient`, `CircuitBreakerFactory` → all to `infrastructure.copilot`
- **Port implementations**: `RunCopilotSessionPort`, `RunRubberDuckSessionPort`, `ManageCopilotClientPort`, `GenerateAiSummaryPort`
- **Acceptance criteria**: All SDK imports (`com.github.copilot.*`) confined to this package. Port implementations annotated with `@Singleton`. Tests pass.
- **Source**: t4-architect-classmap.md §2,§6,§7,§8,§9; t2-architect-leakage.md (SDK 20 files)
- **REQ**: AUTH-01–08, AUTH-10–11, §7.6

---

## T010: Migrate support infrastructure [P]

- **Role**: backend
- **Module**: infra-support
- **Depends on**: T005, T006, T007, T008
- **Parallel**: yes (with T009)
- **Files** (32):
  - `infrastructure.config/` (11 records): all `@ConfigurationProperties` classes. `GithubMcpConfig` — replace SDK `McpServerConfig` with domain DTO.
  - `infrastructure.template/TemplateRepository` (implements `LoadTemplatePort`)
  - `infrastructure.file/` (7): `LocalFileCandidateCollector`, `LocalFileCandidateProcessor`, `LocalFileContentFormatter`, `LocalFileProvider` (implements `CollectLocalSourcePort`), `ReportFileWriter` (implements `WriteReportPort`), `SummaryReportWriter`, `ReportFileUtils`
  - `infrastructure.auth/` (7): `GhAuthTokenProvider`, `GhCliLocator`, `GitHubTokenResolver`, `CliPathResolver`, `CopilotCliPathResolver`, `TokenInputReader`, `TokenReadUtils`
  - `infrastructure.logging/` (2): `LogbackLevelSwitcher`, `SecurityAuditLogger`
  - `infrastructure.parsing/` (5): `FrontmatterParser`, `AgentMarkdownParser`, `SkillMarkdownParser`, `AgentConfigLoader`, `SkillRegistry`
- **Acceptance criteria**: Micronaut/Jakarta annotations allowed here. SLF4J allowed here. All port implementations properly annotated. Tests pass.
- **Source**: t4-architect-classmap.md §4,§7,§8,§9,§10,§11
- **REQ**: TGT-01–09, OUT-02–03–06, §7.7

---

## T011: Migrate presentation layer

- **Role**: backend
- **Module**: presentation
- **Depends on**: T009, T010
- **Parallel**: no
- **Files** (29): All 28 `cli` files + `ReviewApp` → `presentation` with sub-packages `command`, `formatter`, `parser`. Replace direct service calls with inbound port injection.
- **DI wiring**: `@Inject` port implementations in command classes and coordinators.
- **Acceptance criteria**: Presentation imports only `application.port.inbound.*`, `domain.*`, `shared.*`. No infrastructure imports. Tests pass.
- **Source**: t4-architect-classmap.md §3
- **REQ**: §1, §8, all CLI behaviors

---

## T012: Create ArchUnit boundary tests

- **Role**: backend
- **Module**: archunit
- **Depends on**: T011
- **Parallel**: no
- **Files** (1 new): `src/test/java/dev/logicojp/reviewer/architecture/LayerDependencyRulesTest.java`
- **Rules to enforce**:
  1. `domain..` does not import `io.micronaut..`, `jakarta..`, `com.github.copilot..`, `org.slf4j..`, `org.yaml..`
  2. `shared..` imports only `java..`
  3. No package imports `presentation..` (except presentation itself)
  4. `infrastructure..` imports only `application.port..`, `domain..`, `shared..`, `java..`
  5. `application..` (excluding port) does not import `infrastructure..` or `presentation..`
  6. No package-level cycles (using `slices().matching("dev.logicojp.reviewer.(*)..").should().beFreeOfCycles()`)
- **Build file change**: Add `com.tngtech.archunit:archunit-junit5` dependency to `pom.xml` (test scope)
- **Acceptance criteria**: All 6 rules pass on current codebase. `mvn test -pl . -Dtest=LayerDependencyRulesTest` returns 0.
- **Source**: t1-teamlead.md §6
- **REQ**: §2, §3, §6

---

## T013: Migrate test files to new package structure

- **Role**: backend
- **Module**: test-migration
- **Depends on**: T012
- **Parallel**: no
- **Files** (148 test files): Mirror production package moves. Update all imports. Preserve test logic exactly.
- **Acceptance criteria**: Every test file mirrors its production class's new package. All 148 tests compile. No test logic changes — import-only changes.
- **Source**: project-profile.yaml (148 test files / 17017 LOC)
- **REQ**: §7.3

---

## T014: Full build verification

- **Role**: backend
- **Module**: build-verify
- **Depends on**: T013
- **Parallel**: no
- **Command**: `mvn -B clean verify`
- **Acceptance criteria**: Exit code 0. Shade JAR produced. If GraalVM available: `mvn -B clean verify -Pnative` also exits 0.
- **REQ**: §7.2

---

## T015: Full regression test run

- **Role**: tester
- **Module**: regression
- **Depends on**: T014
- **Parallel**: no
- **Command**: `mvn -B test`
- **Acceptance criteria**: All tests pass (148 original + 1 ArchUnit). Zero regressions. Report pass/fail/skip counts.
- **REQ**: §7.3, §7.8

---

## T016: Smoke test

- **Role**: architect (per charter: smoke test is architect's responsibility)
- **Module**: smoke-test
- **Depends on**: T015
- **Parallel**: no
- **Steps**: Build fat JAR → run `java -jar target/*.jar doctor` → verify exit 0. Run `java -jar target/*.jar list` → verify agent listing. Run `java -jar target/*.jar --help` → verify help output.
- **Acceptance criteria**: All 3 commands produce expected output. No crashes.
- **REQ**: AUTH-09, AGT-01, §7.8

---

## Dependency Graph (DAG)

```
T001 → T002 → T003 → T004 [P]
                    → T005 [P]
                    → T006 [P]
                    → T007 [P]
                    → T008 [P]

T005,T006,T007,T008 → T009 [P]
                     → T010 [P]

T009,T010 → T011 → T012 → T013 → T014 → T015 → T016
```

## Task Summary

| Task | Phase | Role | Parallel | Files | Depends On |
|------|-------|------|----------|-------|------------|
| T001 | 1 | backend | — | 10 | — |
| T002 | 1 | backend | — | 22+ | T001 |
| T003 | 1 | backend | — | 12 | T002 |
| T004 | 2 | backend | [P] | 12 | T003 |
| T005 | 2 | backend | [P] | 11 | T003 |
| T006 | 3 | backend | [P] | 18 | T003 |
| T007 | 3 | backend | [P] | 3 | T003 |
| T008 | 3 | backend | [P] | 1 | T003 |
| T009 | 4 | backend | [P] | 16 | T005–T008 |
| T010 | 4 | backend | [P] | 32 | T005–T008 |
| T011 | 5 | backend | — | 29 | T009,T010 |
| T012 | 5 | backend | — | 1 | T011 |
| T013 | 6 | backend | — | 148 | T012 |
| T014 | 6 | backend | — | 0 | T013 |
| T015 | 6 | tester | — | 0 | T014 |
| T016 | 6 | architect | — | 0 | T015 |
