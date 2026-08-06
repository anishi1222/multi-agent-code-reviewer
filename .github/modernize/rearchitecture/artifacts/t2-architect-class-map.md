# t2 — Class Inventory with Layer Target Mapping

120 Java files mapped to their target layer per constitution §1, §8.

Legend: **P**=presentation, **A**=application, **AP**=application.port, **D**=domain, **I**=infrastructure, **S**=shared

## `dev.logicojp.reviewer` (root) → 2 files

| File | Type | Target | Rationale |
|---|---|---|---|
| ReviewApp | class | **P** | Entry point; Micronaut bootstrap (§8) |
| LogbackLevelSwitcher | class | **I** | Framework-specific log level manipulation |

## `agent` → 30 files (MUST decompose)

| File | Type | Target | Rationale |
|---|---|---|---|
| AgentConfig | record | **D** | Domain model — agent configuration data (remove @Nullable → Optional) |
| AgentConfigLoader | class | **I** | File I/O: loads agent .md from disk |
| AgentConfigValidator | class | **D** | Pure validation logic |
| AgentDefinitionPolicy | class | **D** | Pure policy/rule enforcement |
| AgentFrontmatterMapper | class | **D** | Pure mapping logic (move FrontmatterParser dep to port) |
| AgentMarkdownParser | class | **I** | File I/O: reads Markdown files |
| AgentPromptBuilder | class | **D** | Pure prompt construction |
| AgentSectionParser | class | **D** | Pure text parsing |
| CircuitBreakerFactory | class | **I** | DI-wired factory (@Singleton) |
| DialogueRound | record | **D** | Domain value object |
| ParsedAgentMetadata | record | **D** | Domain value object |
| ReviewAgent | class | **D** | Domain model |
| ReviewContext | record | **D** | Domain model (extract SDK types to port) |
| ReviewMessageFlow | class | **I** | Network I/O: LLM communication |
| ReviewPassRunner | class | **A** | Use-case orchestration of single review pass |
| ReviewResultFactory | class | **D** | Pure construction logic |
| ReviewRetryExecutor | class | **A** | Application-level retry coordination |
| ReviewSessionConfigFactory | class | **I** | SDK session config (uses SDK types directly) |
| ReviewSessionExecutor | class | **I** | SDK session execution |
| ReviewSessionMessageSender | class | **I** | SDK message sending |
| ReviewSystemPromptFormatter | class | **D** | Pure formatting |
| ReviewTargetInstructionResolver | class | **D** | Pure resolution logic (extract @Nullable) |
| RubberDuckDialogueExecutor | class | **I** | SDK/network I/O |
| RubberDuckDialogueRunner | class | **A** | Use-case orchestration |
| RubberDuckPromptBuilder | class | **D** | Pure prompt construction |
| RubberDuckSession | interface | **AP** | Port: session abstraction |
| RubberDuckSessionFactory | interface | **AP** | Port: factory abstraction |
| SdkRubberDuckSessionFactory | class | **I** | SDK adapter implementation |
| SharedCircuitBreaker | class | **D** | Pure state logic (no I/O, no DI) |
| SynthesisStrategy | interface | **D** | Domain strategy pattern |

## `cli` → 28 files → **P** (presentation)

All 28 files map to `presentation` (CLI commands, argument parsing, output formatting). This is the natural home per §8.

Key sub-packages: `presentation.command` (DoctorCommand, ListAgentsCommand, ReviewCommand, SkillCommand), `presentation.formatter` (CliOutput, ReviewOutputFormatter, SkillOutputFormatter), `presentation.parser` (CliParsing, ReviewOptionsParser, SkillOptionsParser).

## `config` → 13 files → **I** (infrastructure.config)

All @ConfigurationProperties records move to `infrastructure.config` per §8. `ConfigDefaults` and `SensitiveHeaderMasking` move to `shared` (no framework deps).

## `instruction` → 2 files → **D** (domain)

| File | Target | Rationale |
|---|---|---|
| CustomInstructionSafetyValidator | **D** | Pure validation (file I/O aspect extracted to port) |
| InstructionFrontmatter | **D** | Pure data parsing |

## `orchestrator` → 15 files (MUST decompose)

| File | Target | Rationale |
|---|---|---|
| AgentReviewExecutor | **A** | Use-case: executes single agent review |
| AgentReviewer | **AP** | Port interface |
| AgentReviewerFactory | **AP** | Port factory interface |
| ExecutorResources | **A** | Application DTO |
| LocalSourceCollector | **AP** | Port interface |
| LocalSourceCollectorFactory | **AP** | Port factory interface |
| LocalSourcePrecomputer | **A** | Application service |
| OrchestratorCollaborators | **A** | Application DTO (extract SDK type to port) |
| OrchestratorConfig | **A** | Application DTO |
| OrchestratorMetrics | **A** | Application metric tracking |
| PromptTexts | **D** | Domain value object |
| ReviewContextFactory | **I** | Uses CopilotClient directly |
| ReviewExecutionModeRunner | **A** | Use-case runner |
| ReviewOrchestrator | **A** | Top-level use-case orchestrator |
| ReviewOrchestratorFactory | **I** | DI-wired factory with SDK deps |
| ReviewResultPipeline | **A** | Application pipeline |

## `report.*` → 22 files (MUST decompose)

| Sub-package | File | Target | Rationale |
|---|---|---|---|
| core | ReportGenerator | **I** | File I/O: writes reports to disk |
| core | ReviewResult | **D** | Domain model (cycle hub — must move) |
| factory | ReportGeneratorFactory | **I** | DI + SDK wiring |
| finding | AggregatedFinding | **D** | Domain value object |
| finding | FindingsExtractor | **D** | Pure extraction logic |
| finding | FindingsParser | **D** | Pure parsing |
| finding | ReviewFindingParser | **D** | Pure parsing |
| finding | ReviewFindingSimilarity | **D** | Pure computation |
| formatter | FindingsSummaryFormatter | **D** | Pure formatting |
| formatter | ReportContentFormatter | **D** | Pure formatting |
| formatter | ReviewMergedContentFormatter | **D** | Pure formatting |
| formatter | SummaryFinalReportFormatter | **D** | Pure formatting |
| merger | ReviewOverallSummaryAppender | **D** | Pure logic |
| merger | ReviewResultMerger | **D** | Pure logic |
| sanitize | ContentSanitizationPipeline | **D** | Pure logic |
| sanitize | ContentSanitizationRule | **D** | Domain value object |
| sanitize | ContentSanitizer | **D** | Pure logic |
| summary | AiSummaryClient | **I** | SDK I/O |
| summary | FallbackSummaryBuilder | **D** | Pure logic |
| summary | SummaryGenerator | **A** | Use-case: orchestrates summary generation |
| summary | SummaryPromptBuilder | **D** | Pure prompt construction |
| summary | SummaryReportWriter | **I** | File I/O |
| util | ReportFileUtils | **I** | File I/O |
| util | ReportFilenameUtils | **S** | Pure utility |

## `service` → 13 files (MUST decompose)

| File | Target | Rationale |
|---|---|---|
| AgentService | **A** | Use-case: loads and provides agents |
| CopilotCliException | **D** | Domain exception |
| CopilotCliPathResolver | **I** | File/process I/O |
| CopilotClientStarter | **I** | SDK lifecycle |
| CopilotHealthProbe | **I** | SDK health check |
| CopilotService | **I** | SDK process lifecycle |
| CopilotStartupErrorFormatter | **D** | Pure formatting |
| CopilotTimeoutResolver | **D** or **A** | Pure logic |
| ReportService | **A** | Use-case: report orchestration |
| ReviewService | **A** | Use-case: review orchestration |
| SkillService | **A** | Use-case: skill management |
| TemplateRepository | **I** | File I/O: loads templates |
| TemplateService | **A** → **AP** | Must become port interface + infra adapter |

## `skill` → 6 files

| File | Target | Rationale |
|---|---|---|
| SkillDefinition | **D** | Domain model |
| SkillExecutor | **I** | SDK I/O |
| SkillMarkdownParser | **I** | File I/O |
| SkillParameter | **D** | Domain value object |
| SkillRegistry | **I** | DI-wired registry |
| SkillResult | **D** | Domain value object |

## `target` → 7 files

| File | Target | Rationale |
|---|---|---|
| LocalFileCandidate | **D** | Domain value object |
| LocalFileCandidateCollector | **I** | File I/O |
| LocalFileCandidateProcessor | **I** | File I/O |
| LocalFileContentFormatter | **I** | File I/O |
| LocalFileProvider | **I** | File I/O |
| LocalFileSelectionConfig | **D** | Domain value object |
| ReviewTarget | **D** | Domain model |

## `util` → 14+ files

| File | Target | Rationale |
|---|---|---|
| CliPathResolver | **I** | Process I/O |
| CopilotPermissionHandlers | **I** | SDK adapter |
| ExecutionCorrelation | **S** | Pure utility |
| ExecutorUtils | **S** | Pure utility |
| FrontmatterParser | **I** | SnakeYAML dependency |
| GhAuthTokenProvider | **I** | Process I/O |
| GhCliLocator | **I** | Process I/O |
| GitHubTokenResolver | **I** | Process/env I/O + DI |
| PlaceholderUtils | **S** | Pure utility |
| RetryExecutor | **S** | Pure utility (break cycle: remove SharedCircuitBreaker dep) |
| RetryPolicyUtils | **S** | Pure utility |
| SecurityAuditLogger | **I** | Logging infrastructure |
| StructuredConcurrencyUtils | **S** | Pure utility |
| TokenHashUtils | **S** | Pure utility |
| TokenInputReader | **I** | stdin/file I/O |
| TokenReadUtils | **I** | file/env I/O |

---

## Layer Distribution Summary

| Layer | File Count | Key Concerns |
|---|---|---|
| **presentation** | ~30 | CLI commands, arg parsing, output formatting |
| **application** | ~18 | Use-case orchestration, pipelines |
| **application.port** | ~8 | Port interfaces (review, template, source collection, session) |
| **domain** | ~40 | Models, VOs, validators, pure formatters, pure builders |
| **infrastructure** | ~30 | SDK adapters, file I/O, config binding, template loading |
| **shared** | ~10 | Pure utilities (text, concurrency, retry, hashing) |
