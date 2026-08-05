# t11 — Phase 4: Infrastructure Adapters — Copilot SDK + Support

## Summary

Phase 4 complete. Implemented all `infrastructure.copilot` (16 files) and supporting infrastructure
packages (`infrastructure.file`, `infrastructure.parsing`, `infrastructure.template`). Also applied
required domain/application fixes. Build: **907 tests, 0 failures, 0 errors**.

## Files Implemented

### infrastructure.copilot (16 files)
| File | Role |
|------|------|
| `CopilotService` | `ManageCopilotClientPort` impl — `volatile CopilotClient`, `@PostConstruct` eager init |
| `ReviewOrchestratorFactory` | `@Singleton` DI wiring point — wires all 5 port implementations into `RunReviewPort` |
| `ReviewSessionExecutor` | SDK session lifecycle — `run(SessionRequest)` → message loop |
| `ReviewSessionMessageSender` | SDK message sending with timeout, content extraction |
| `ReviewSessionConfigFactory` | Converts `SessionRequest` domain DTO → SDK `SessionConfig` |
| `SdkRubberDuckSessionFactory` | Package-private; builds SDK sessions for rubber duck dialogue |
| `RubberDuckDialogueExecutor` | `RunRubberDuckSessionPort` impl — dual-session counter-prompt pattern |
| `AiSummaryClient` | `GenerateAiSummaryPort` impl — single-session summary generation |
| `SkillExecutor` | `ExecuteSkillPort` impl — delegates to `RunCopilotSessionPort` + `SkillRegistry` |
| `CopilotClientStarter` | Startup with retry + backoff (max 3 attempts) |
| `CopilotHealthProbe` | `@Singleton`; `isClientHealthy()`, `getConnectionState()`, auth status |
| `CopilotPermissionHandlers` | Turn + request permission denial event handlers |
| `CircuitBreakerFactory` | Builds `SharedCircuitBreaker` from `CopilotConfig` resilience settings |
| `ReviewMessageFlow` | Message routing: initial prompt vs continuation patterns |
| `ReviewContextFactory` | `OrchestratorConfig` builder from `ExecutionConfig` + `ModelConfig` + `RubberDuckConfig` |
| `CopilotStartupErrorFormatter` | Error message factory for CLI not found / not initialized / timeout |

### infrastructure.file (7 files)
| File | Role |
|------|------|
| `LocalFileProvider` | `ProvideLocalFilesPort` impl — walks repository tree |
| `LocalFileCandidateCollector` | File walking logic with depth/extension/size limits |
| `LocalFileCandidateProcessor` | Reads file bytes, validates content, converts to `FileCandidate` |
| `LocalFileContentFormatter` | Formats `FileCandidate` → review-context markdown |
| `ReportFileWriter` | `WriteReportPort` impl — writes per-agent report files |
| `SummaryReportWriter` | Writes executive summary; delegates to `SummaryFinalReportFormatter` |
| `ReportFileUtils` | Static utilities: `ensureOutputDirectory`, `writeSecureString` |

### infrastructure.parsing (5 files)
| File | Role |
|------|------|
| `AgentMarkdownParser` | Parses `AgentConfig` from markdown front-matter + body |
| `AgentConfigLoader` | `LoadAgentDefinitionsPort` impl — validates, applies skills, deduplicates |
| `FrontmatterParser` | Low-level YAML front-matter extraction from markdown |
| `SkillMarkdownParser` | Parses `SkillDefinition` from markdown front-matter |
| `SkillRegistry` | In-memory skill registry; `get(id)` → `Optional<SkillDefinition>`, `getAll()` |

### infrastructure.template (1 file)
| File | Role |
|------|------|
| `TemplateRepository` | `LoadTemplatePort` impl — classpath + filesystem template resolution |

## Domain/Application Fixes Applied
| File | Fix |
|------|-----|
| `domain.agent.AgentConfig` | Added `validateRequired()` — throws `IllegalStateException` if name/model blank |
| `infrastructure.auth.CopilotCliPathResolver` | Made `CLI_PATH_ENV` `public static final` |
| `application.skill.ExecuteSkillUseCase` | Removed TODO stub; full port-based implementation via `RunCopilotSessionPort` |
| `infrastructure.file.SummaryReportWriter` | `write()` accepts `findingsSummary` as 5th param (matching `SummaryFinalReportFormatter.format()`) |
| `infrastructure.copilot.ReviewSessionConfigFactory` | `Map<String,McpServerConfig>` (generics invariance fix) |
| `infrastructure.copilot.SdkRubberDuckSessionFactory` | Same generics invariance fix |
| `infrastructure.copilot.CopilotService` | `resolveCliPath()` (not `resolve()`), `setCliPath()` (not `setCopilotClientPath()`) |
| `infrastructure.copilot.CopilotStartupErrorFormatter` | Added `buildClientNotInitializedMessage()` |

## Test Results
- Command: `JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open ./mvnw -B clean verify`
- Passed: 907
- Failed: 0
- Errors: 0
- Skipped: 0

## Architecture Compliance
- All new files at `dev.logicojp.reviewer.infrastructure.*` — brownfield files untouched
- No `presentation` imports from `infrastructure`
- No DI annotations in `domain` or `application`
- Ports resolved via `application.port.outbound.*` interfaces only

## Upstream Artifacts Consumed
- `t4-architect-packages.md` — package layering rules and allowed imports
- `t4-architect-ports.md` — outbound port contracts (T009/T010 tasks)
- `t4-architect-classmap.md` — class-to-package mapping for all infrastructure adapters
- `t5-teamlead-tasks.md` — T009/T010 task specs, acceptance criteria
- `t8-backend.md`, `t9-backend.md`, `t10-backend.md` — prior phase outputs for domain/application types

## Evidence Mapping
- `t4-architect-ports.md#ManageCopilotClientPort` → `CopilotService.java` implements `ManageCopilotClientPort`
- `t4-architect-ports.md#RunRubberDuckSessionPort` → `RubberDuckDialogueExecutor.java`
- `t4-architect-ports.md#GenerateAiSummaryPort` → `AiSummaryClient.java`
- `t4-architect-ports.md#ExecuteSkillPort` → `SkillExecutor.java`
- `t4-architect-ports.md#ProvideLocalFilesPort` → `LocalFileProvider.java`
- `t4-architect-ports.md#WriteReportPort` → `ReportFileWriter.java`
- `t4-architect-ports.md#LoadAgentDefinitionsPort` → `AgentConfigLoader.java`
- `t4-architect-ports.md#LoadTemplatePort` → `TemplateRepository.java`
- `t5-teamlead-tasks.md#T009` → `infrastructure.copilot.*` (16 files)
- `t5-teamlead-tasks.md#T010` → `infrastructure.file.*` + `infrastructure.parsing.*` + `infrastructure.template.*`
