# t4 — Full Class → Target Package Mapping

120 production Java files. Each row: current location, target package, migration notes.

Legend: **P**=presentation, **A**=application, **AP**=application.port, **D**=domain, **I**=infrastructure, **S**=shared

---

## 1. Root (`dev.logicojp.reviewer`) → 2 files

| Current File | Layer | Target Package | Migration Notes |
|---|---|---|---|
| ReviewApp | P | `presentation` | Micronaut bootstrap; stays as entry point |
| LogbackLevelSwitcher | I | `infrastructure.logging` | Framework-specific |

## 2. `agent` → 30 files (decompose across 4 layers)

| Current File | Layer | Target Package | Migration Notes |
|---|---|---|---|
| AgentConfig | D | `domain.agent` | Remove `@Nullable` → `Optional`; cycle root — move first |
| AgentConfigLoader | I | `infrastructure.parsing` | File I/O; reads `.agent.md` from disk |
| AgentConfigValidator | D | `domain.agent` | Pure validation |
| AgentDefinitionPolicy | D | `domain.agent` | Pure policy |
| AgentFrontmatterMapper | D | `domain.agent` | Pure mapping; FrontmatterParser access via port |
| AgentMarkdownParser | I | `infrastructure.parsing` | File I/O |
| AgentPromptBuilder | D | `domain.agent` | Pure prompt construction |
| AgentSectionParser | D | `domain.agent` | Pure text parsing |
| CircuitBreakerFactory | I | `infrastructure.copilot` | DI-wired `@Singleton` factory |
| DialogueRound | D | `domain.agent` | Value object |
| ParsedAgentMetadata | D | `domain.agent` | Value object |
| ReviewAgent | D | `domain.agent` | Domain model |
| ReviewContext | D | `domain.review` | Extract SDK types to port params; remove `@Nullable` |
| ReviewMessageFlow | I | `infrastructure.copilot` | Network I/O via SDK |
| ReviewPassRunner | A | `application.review` | Use-case: single pass orchestration |
| ReviewResultFactory | D | `domain.report` | Pure construction |
| ReviewRetryExecutor | A | `application.review` | Application retry coordination |
| ReviewSessionConfigFactory | I | `infrastructure.copilot` | SDK session configuration |
| ReviewSessionExecutor | I | `infrastructure.copilot` | SDK session execution; implements `RunCopilotSessionPort` |
| ReviewSessionMessageSender | I | `infrastructure.copilot` | SDK message sending |
| ReviewSystemPromptFormatter | D | `domain.agent` | Pure formatting |
| ReviewTargetInstructionResolver | D | `domain.agent` | Remove `@Nullable` |
| RubberDuckDialogueExecutor | I | `infrastructure.copilot` | SDK I/O; implements `RunRubberDuckSessionPort` |
| RubberDuckDialogueRunner | A | `application.review` | Use-case orchestration |
| RubberDuckPromptBuilder | D | `domain.agent` | Pure prompt construction |
| RubberDuckSession | AP | `application.port.outbound` | Port interface (merge into `RunRubberDuckSessionPort`) |
| RubberDuckSessionFactory | AP | `application.port.outbound` | Port factory (merge into port or keep if DI needs it) |
| SdkRubberDuckSessionFactory | I | `infrastructure.copilot` | SDK adapter |
| SharedCircuitBreaker | D | `domain.resilience` | Pure state logic; cycle root — move first |
| SynthesisStrategy | D | `domain.resilience` | Strategy interface |

## 3. `cli` → 28 files → all `presentation`

| Current File | Layer | Target Sub-package | Migration Notes |
|---|---|---|---|
| CliCommand | P | `presentation` | Base command interface/class |
| CliOutput | P | `presentation.formatter` | Stdout/stderr formatting |
| CliParsing | P | `presentation.parser` | Low-level arg parsing |
| CliUsage | P | `presentation.parser` | Help text |
| CliValidationException | P | `presentation.parser` | Validation error |
| CommandExecutor | P | `presentation.command` | Command dispatch |
| DoctorCommand | P | `presentation.command` | Doctor diagnostics; SDK types → use `RunDiagnosticsPort` |
| ExitCodes | P | `presentation.parser` | Exit code constants |
| LifecycleRunner | P | `presentation.command` | App lifecycle |
| ListAgentsCommand | P | `presentation.command` | List agents |
| ReviewAgentConfigResolver | P | `presentation` | CLI-specific agent config resolution |
| ReviewAgentSelection | P | `presentation` | CLI-specific agent selection |
| ReviewCommand | P | `presentation.command` | Main review command |
| ReviewExecutionCoordinator | P | `presentation` | CLI → use-case bridge |
| ReviewModelConfigResolver | P | `presentation` | CLI model config |
| ReviewOptions | P | `presentation` | CLI options DTO |
| ReviewOptionsParser | P | `presentation.parser` | Options parsing |
| ReviewOutputFormatter | P | `presentation.formatter` | Review output |
| ReviewPreparationService | P | `presentation` | Pre-review preparation |
| ReviewRunExecutor | P | `presentation` | Run execution bridge |
| ReviewRunRequestFactory | P | `presentation` | Request factory |
| ReviewTargetResolver | P | `presentation` | Target resolution |
| ReviewTargetSelection | P | `presentation` | Target selection |
| SkillCommand | P | `presentation.command` | Skill command |
| SkillExecutionCoordinator | P | `presentation` | Skill coordination |
| SkillExecutionPreparation | P | `presentation` | Skill preparation |
| SkillOptionsParser | P | `presentation.parser` | Skill parsing |
| SkillOutputFormatter | P | `presentation.formatter` | Skill output |

## 4. `config` → 13 files

| Current File | Layer | Target Package | Migration Notes |
|---|---|---|---|
| AgentPathConfig | I | `infrastructure.config` | @ConfigurationProperties |
| CircuitBreakerConfig | I | `infrastructure.config` | @ConfigurationProperties |
| ConfigDefaults | S | `shared` | Pure constants |
| CopilotConfig | I | `infrastructure.config` | @ConfigurationProperties |
| ExecutionConfig | I | `infrastructure.config` | @ConfigurationProperties |
| GithubMcpConfig | I | `infrastructure.config` | @ConfigurationProperties; has SDK type — replace with domain DTO |
| LocalFileConfig | I | `infrastructure.config` | @ConfigurationProperties |
| ModelConfig | I | `infrastructure.config` | @ConfigurationProperties |
| RubberDuckConfig | I | `infrastructure.config` | @ConfigurationProperties |
| SensitiveHeaderMasking | S | `shared` | Pure utility |
| SkillConfig | I | `infrastructure.config` | @ConfigurationProperties |
| SummaryConfig | I | `infrastructure.config` | @ConfigurationProperties |
| TemplateConfig | I | `infrastructure.config` | @ConfigurationProperties |

## 5. `instruction` → 2 files → `domain.instruction`

| Current File | Layer | Target Package | Migration Notes |
|---|---|---|---|
| CustomInstructionSafetyValidator | D | `domain.instruction` | Pure validation |
| InstructionFrontmatter | D | `domain.instruction` | Pure data |

## 6. `orchestrator` → 15 files (decompose)

| Current File | Layer | Target Package | Migration Notes |
|---|---|---|---|
| AgentReviewExecutor | A | `application.review` | Use-case: single agent review |
| AgentReviewer | AP | `application.port.outbound` | Port interface — merge into `RunCopilotSessionPort` or keep distinct |
| AgentReviewerFactory | AP | `application.port.outbound` | Factory port |
| ExecutorResources | A | `application.review` | Application DTO |
| LocalSourceCollector | AP | `application.port.outbound` | Port interface — merge into `CollectLocalSourcePort` |
| LocalSourceCollectorFactory | AP | `application.port.outbound` | Factory port |
| LocalSourcePrecomputer | A | `application.review` | Application service |
| OrchestratorCollaborators | A | `application.review` | Application DTO; extract SDK type to port |
| OrchestratorConfig | A | `application.review` | Application DTO; remove `@Nullable` |
| OrchestratorMetrics | A | `application.review` | Metrics |
| PromptTexts | D | `domain.review` | Value object |
| ReviewContextFactory | I | `infrastructure.copilot` | Uses CopilotClient directly |
| ReviewExecutionModeRunner | A | `application.review` | Use-case runner |
| ReviewOrchestrator | A | `application.review` | Top-level orchestrator; implements `RunReviewPort` |
| ReviewOrchestratorFactory | I | `infrastructure.copilot` | DI + SDK wiring |
| ReviewResultPipeline | A | `application.review` | Pipeline |

## 7. `report.*` → 22 files (decompose)

| Current File | Layer | Target Package | Migration Notes |
|---|---|---|---|
| ReportGenerator (core) | I | `infrastructure.file` | Rename → `ReportFileWriter`; implements `WriteReportPort` |
| ReviewResult (core) | D | `domain.report` | Cycle root — move first; remove `@Nullable` |
| ReportGeneratorFactory (factory) | I | `infrastructure.copilot` | DI + SDK |
| AggregatedFinding (finding) | D | `domain.report` | Value object |
| FindingsExtractor (finding) | D | `domain.report` | Pure extraction; remove direct Formatter import |
| FindingsParser (finding) | D | `domain.report` | Pure parsing |
| ReviewFindingParser (finding) | D | `domain.report` | Pure parsing |
| ReviewFindingSimilarity (finding) | D | `domain.report` | Pure computation |
| FindingsSummaryFormatter (formatter) | D | `domain.report` | Pure formatting |
| ReportContentFormatter (formatter) | D | `domain.report` | Pure formatting |
| ReviewMergedContentFormatter (formatter) | D | `domain.report` | Pure formatting |
| SummaryFinalReportFormatter (formatter) | D | `domain.report` | Pure formatting |
| ReviewOverallSummaryAppender (merger) | D | `domain.report` | Pure logic |
| ReviewResultMerger (merger) | D | `domain.report` | Pure logic |
| ContentSanitizationPipeline (sanitize) | D | `domain.report` | Pure logic |
| ContentSanitizationRule (sanitize) | D | `domain.report` | Value object |
| ContentSanitizer (sanitize) | D | `domain.report` | Pure logic |
| AiSummaryClient (summary) | I | `infrastructure.copilot` | SDK I/O; implements `GenerateAiSummaryPort` |
| FallbackSummaryBuilder (summary) | D | `domain.report` | Pure logic; remove SLF4J |
| SummaryGenerator (summary) | A | `application.report` | Use-case orchestrator |
| SummaryPromptBuilder (summary) | D | `domain.report` | Pure prompt construction |
| SummaryReportWriter (summary) | I | `infrastructure.file` | File I/O |
| ReportFileUtils (util) | I | `infrastructure.file` | File I/O |
| ReportFilenameUtils (util) | S | `shared` | Pure utility |

## 8. `service` → 13 files (decompose)

| Current File | Layer | Target Package | Migration Notes |
|---|---|---|---|
| AgentService | A | `application.agent` | Rename → `LoadAgentUseCase`; implements `LoadAgentPort` |
| CopilotCliException | D | `domain.resilience` | Domain exception |
| CopilotCliPathResolver | I | `infrastructure.auth` | Process I/O |
| CopilotClientStarter | I | `infrastructure.copilot` | SDK lifecycle |
| CopilotHealthProbe | I | `infrastructure.copilot` | SDK health |
| CopilotService | I | `infrastructure.copilot` | SDK process lifecycle; implements `ManageCopilotClientPort` |
| CopilotStartupErrorFormatter | D | `domain.resilience` | Pure formatting |
| CopilotTimeoutResolver | D | `domain.resilience` | Pure logic |
| ReportService | A | `application.report` | Rename → `GenerateReportUseCase`; implements `GenerateReportPort` |
| ReviewService | A | `application.review` | High-level review coordination (may merge into ReviewOrchestrator) |
| SkillService | A | `application.skill` | Rename → `ExecuteSkillUseCase`; implements `ExecuteSkillPort` |
| TemplateRepository | I | `infrastructure.template` | File I/O; implements `LoadTemplatePort` |
| TemplateService | A | `application.review` | Becomes thin coordinator or eliminated (replaced by `LoadTemplatePort`) |

## 9. `skill` → 6 files

| Current File | Layer | Target Package | Migration Notes |
|---|---|---|---|
| SkillDefinition | D | `domain.skill` | Domain model |
| SkillExecutor | I | `infrastructure.copilot` | SDK I/O |
| SkillMarkdownParser | I | `infrastructure.parsing` | File I/O |
| SkillParameter | D | `domain.skill` | Value object |
| SkillRegistry | I | `infrastructure.parsing` | DI-wired registry |
| SkillResult | D | `domain.skill` | Value object |

## 10. `target` → 7 files

| Current File | Layer | Target Package | Migration Notes |
|---|---|---|---|
| LocalFileCandidate | D | `domain.review` | Value object |
| LocalFileCandidateCollector | I | `infrastructure.file` | File I/O; part of `CollectLocalSourcePort` impl |
| LocalFileCandidateProcessor | I | `infrastructure.file` | File I/O |
| LocalFileContentFormatter | I | `infrastructure.file` | File I/O formatting |
| LocalFileProvider | I | `infrastructure.file` | File I/O; implements `CollectLocalSourcePort` |
| LocalFileSelectionConfig | D | `domain.review` | Value object |
| ReviewTarget | D | `domain.review` | Domain model |

## 11. `util` → 16 files

| Current File | Layer | Target Package | Migration Notes |
|---|---|---|---|
| CliPathResolver | I | `infrastructure.auth` | Process I/O |
| CopilotPermissionHandlers | I | `infrastructure.copilot` | SDK adapter |
| ExecutionCorrelation | S | `shared` | Pure utility |
| ExecutorUtils | S | `shared` | Pure utility |
| FrontmatterParser | I | `infrastructure.parsing` | SnakeYAML dependency |
| GhAuthTokenProvider | I | `infrastructure.auth` | Process I/O |
| GhCliLocator | I | `infrastructure.auth` | Process I/O; remove `@Nullable` |
| GitHubTokenResolver | I | `infrastructure.auth` | Process/env I/O + DI |
| PlaceholderUtils | S | `shared` | Pure utility |
| RetryExecutor | S | `shared` | Pure utility; **remove `SharedCircuitBreaker` import** — accept circuit breaker via parameter |
| RetryPolicyUtils | S | `shared` | Pure utility |
| SecurityAuditLogger | I | `infrastructure.logging` | SLF4J logging |
| StructuredConcurrencyUtils | S | `shared` | Pure utility |
| TokenHashUtils | S | `shared` | Pure utility |
| TokenInputReader | I | `infrastructure.auth` | stdin/file I/O |
| TokenReadUtils | I | `infrastructure.auth` | file/env I/O |

---

## Layer Distribution Summary

| Layer | File Count | % |
|---|---|---|
| presentation | 30 | 25% |
| application | 18 | 15% |
| application.port | 8 | 7% |
| domain | 40 | 33% |
| infrastructure | 32 | 27% |
| shared | 10 | 8% |
| **Total** | **138** | — |

Note: 138 > 120 because some port interfaces are new files (12 port interfaces created), and some existing interfaces are merged into ports. Net new files: ~12 ports + ~2 domain DTOs. Some existing files are eliminated (TemplateService absorbed).
