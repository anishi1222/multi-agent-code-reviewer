# t8 — Phase 1: Shared Layer, Domain Core Types, 12 Port Interfaces

## Summary

Phase 1 foundation complete. **52 new files** created (0 old files modified) implementing:
- **T001**: 11 shared utility files + `CircuitBreaker` interface
- **T002**: 17 domain core type files (records/sealed interfaces, no framework deps)
- **T003**: 12 port interfaces + 7 domain DTOs

All 907 tests pass. `mvn clean verify` exits 0.

---

## T001 — Shared Layer (11 source + 1 interface = 12 files)

| File | Change |
|------|--------|
| `shared/CircuitBreaker.java` | NEW interface: `allowRequest()`, `onSuccess()`, `onFailure()`, `noOp()` |
| `shared/RetryExecutor.java` | `SharedCircuitBreaker` dep → `CircuitBreaker` interface; null-safe constructor |
| `shared/ExecutionCorrelation.java` | MDC methods stripped; kept: `generateExecutionId()`, `EXECUTION_ID_MDC_KEY`, `CheckedSupplier` |
| `shared/ConfigDefaults.java` | SLF4J removed; made `public`; `loadListFromResource` silent fallback |
| `shared/SensitiveHeaderMasking.java` | Made `public final class` (was package-private) |
| `shared/PlaceholderUtils.java` | Package change: `util` → `shared` |
| `shared/ExecutorUtils.java` | Package change: `util` → `shared` |
| `shared/StructuredConcurrencyUtils.java` | Package change: `util` → `shared` |
| `shared/RetryPolicyUtils.java` | Package change: `util` → `shared` |
| `shared/TokenHashUtils.java` | Package change: `util` → `shared` |
| `shared/ReportFilenameUtils.java` | Package change: `report.util` → `shared` |

**Verification**: `test-compile` exit 0 ✅

---

## T002 — Domain Core Types (17 files)

### domain.agent (5 files)
| File | Purification |
|------|-------------|
| `AgentConfig` | Removed `@Nullable`; hardcoded `DEFAULT_MODEL="claude-sonnet-4.5"`; `effectiveDialogueRounds(int)` |
| `DialogueRound` | Public record |
| `ParsedAgentMetadata` | Public record |
| `SynthesisStrategy` | Sealed interface: `LastResponder`, `DedicatedSession`; pure java.* |
| `ReviewAgent` | Domain identity record (name + agentId + config); NO execution logic |

### domain.report (1 file)
| File | Purification |
|------|-------------|
| `ReviewResult` | Removed `@Nullable`; Builder; `domain.agent.AgentConfig` reference |

### domain.skill (3 files)
| File | Purification |
|------|-------------|
| `SkillDefinition` | Imports: `domain.instruction.CustomInstructionSafetyValidator`, `shared.PlaceholderUtils` |
| `SkillParameter` | Package change only |
| `SkillResult` | Package change; `Clock`-injectable factory methods |

### domain.instruction (2 files)
| File | Purification |
|------|-------------|
| `CustomInstructionSafetyValidator` | SLF4J logger removed; silent fallback in `loadPatternTextsFromResource` |
| `InstructionFrontmatter` | SnakeYAML removed; simple `---` regex + line parser; `java.*` only |

### domain.review (4 files)
| File | Purification |
|------|-------------|
| `LocalFileCandidate` | Made `public record` (was package-private) |
| `LocalFileSelectionConfig` | Made `public record`; removed `from(LocalFileConfig)` factory |
| `ReviewTarget` | Sealed interface: `LocalTarget`, `GitHubTarget`; pure java.* |
| `PromptTexts` | Moved from `orchestrator`; field `localReviewResultRequest` |

### domain.resilience (2 files)
| File | Purification |
|------|-------------|
| `SharedCircuitBreaker` | `implements shared.CircuitBreaker`; hardcoded `DEFAULT_FAILURE_THRESHOLD=8`, `DEFAULT_RESET_TIMEOUT_MS=30_000L` |
| `CopilotCliException` | Simple package move; pure Java RuntimeException |

---

## T003 — 12 Port Interfaces + 7 Domain DTOs

### Inbound ports (`application.port.inbound`)

| Interface | Signatures |
|-----------|-----------|
| `RunReviewPort` | `ReviewResult execute(ReviewRequest)` |
| `LoadAgentPort` | `List<AgentConfig> loadAll(List<Path>)`, `Optional<AgentConfig> loadByName(String, List<Path>)` |
| `ExecuteSkillPort` | `SkillResult execute(String, Map<String,String>)`, `List<SkillDefinition> listSkills()` |
| `GenerateReportPort` | `ReportOutput generate(List<ReviewResult>, ReportOptions)`, `Optional<String> generateSummary(...)` |
| `RunDiagnosticsPort` | `List<DiagnosticResult> runAll()` |

**Inbound DTOs**: `ReviewRequest`, `ReportOptions`, `ReportOutput`, `DiagnosticResult`

### Outbound ports (`application.port.outbound`)

| Interface | Signatures |
|-----------|-----------|
| `LoadTemplatePort` | `String render(String, Map<String,String>)`, `String loadRaw(String)` |
| `RunCopilotSessionPort` | `String runSession(SessionRequest)` |
| `RunRubberDuckSessionPort` | `List<DialogueRound> executeDialogue(RubberDuckRequest)` |
| `ManageCopilotClientPort` | `void start(String)`, `void stop()`, `boolean isHealthy()` |
| `CollectLocalSourcePort` | `List<LocalFileCandidate> collect(Path, LocalFileSelectionConfig)`, `String formatContent(List<LocalFileCandidate>)` |
| `WriteReportPort` | `Path write(String, String, Path)`, `Path createOutputDirectory(Path)` |
| `GenerateAiSummaryPort` | `Optional<String> generate(String)` |

**Outbound DTOs**: `SessionRequest`, `RubberDuckRequest`, `McpServerSpec`

---

## Test Results

- Command: `JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open ./mvnw -B clean verify`
- Passed: 907
- Failed: 0
- Skipped: 0
- New test classes (5): `CircuitBreakerTest`, `ExecutionCorrelationTest`, `SharedCircuitBreakerTest`, `ReviewTargetTest`, `CustomInstructionSafetyValidatorTest`

---

## Upstream Artifacts Consumed

- `t4-architect-ports.md` — exact signatures for all 12 port interfaces
- `t4-architect-packages.md` — import rules per layer (java.* only in shared/domain)
- `t4-architect-classmap.md` — file-to-layer mapping; purification requirements
- `t5-teamlead-tasks.md` — T001/T002/T003 acceptance criteria and file lists
- `t1-teamlead.md` — constitution §3 (no DI in domain), §5 (brownfield coexistence)
- `t7-devops.md` — Java 27 JAVA_HOME path

## Evidence Mapping

- `t4-architect-ports.md §2–§3` → all 12 port interfaces match signatures exactly
- `t4-architect-classmap.md §5` → `shared.*` files purified: no SLF4J, no framework annotations
- `t4-architect-classmap.md §2` → `domain.agent.*` purified: no Micronaut @Nullable
- `t4-architect-ports.md §4 (cycle table)` → cycles 3,4 resolved: `SharedCircuitBreaker` implements `CircuitBreaker`; `RetryExecutor` uses interface
- `t5-teamlead-tasks.md T001 acceptance` → 11 shared files + CircuitBreaker interface ✅
- `t5-teamlead-tasks.md T002 acceptance` → 17 domain files; all import only `java.*`/`shared.*`/`domain.*` ✅
- `t5-teamlead-tasks.md T003 acceptance` → 12 port interfaces + 7 DTOs ✅
- `t1-teamlead.md §5 (coexistence)` → 0 old files modified; 52 new files only ✅
