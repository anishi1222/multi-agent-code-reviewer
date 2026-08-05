# t4 — Target Package Tree

## §1 Package Structure

Root: `dev.logicojp.reviewer`

```
dev.logicojp.reviewer/
├── presentation/                    # CLI entry, argument parsing, output
│   ├── ReviewApp.java               # Micronaut bootstrap (entry point)
│   ├── command/                     # CLI command implementations
│   ├── formatter/                   # Output formatting for stdout/stderr
│   └── parser/                      # CLI argument parsing & validation
│
├── application/                     # Use-case orchestration (no business rules)
│   ├── port/                        # Port interfaces (inbound + outbound)
│   │   ├── inbound/                 # Entry points called by presentation
│   │   └── outbound/                # Contracts implemented by infrastructure
│   ├── review/                      # Review orchestration use-cases
│   ├── report/                      # Report generation use-cases
│   ├── skill/                       # Skill execution use-cases
│   └── agent/                       # Agent loading use-cases
│
├── domain/                          # Pure business logic — framework-free
│   ├── agent/                       # Agent models, validation, policy, prompt building
│   ├── report/                      # Report models, finding extraction, formatting, sanitization, merging
│   ├── skill/                       # Skill models, parameters, results
│   ├── instruction/                 # Custom instruction models & safety validation
│   ├── review/                      # ReviewResult, ReviewTarget, ReviewContext, value objects
│   └── resilience/                  # SharedCircuitBreaker, SynthesisStrategy
│
├── infrastructure/                  # Adapter implementations
│   ├── copilot/                     # Copilot SDK client lifecycle, sessions, message sending
│   ├── config/                      # @ConfigurationProperties (Micronaut binding)
│   ├── template/                    # Template file loading (Mustache/Markdown)
│   ├── file/                        # Local file I/O (target collection, report writing)
│   ├── auth/                        # GitHub auth, token resolution, gh CLI integration
│   ├── logging/                     # LogbackLevelSwitcher, SecurityAuditLogger
│   └── parsing/                     # FrontmatterParser (SnakeYAML), AgentMarkdownParser, SkillMarkdownParser
│
└── shared/                          # Cross-cutting pure utilities (java.* only)
    ├── PlaceholderUtils.java
    ├── ExecutorUtils.java
    ├── StructuredConcurrencyUtils.java
    ├── RetryExecutor.java
    ├── RetryPolicyUtils.java
    ├── TokenHashUtils.java
    ├── ExecutionCorrelation.java
    └── ReportFilenameUtils.java
```

## §2 Allowed Imports per Package

| Layer | May import | MUST NOT import |
|---|---|---|
| `presentation` | `application`, `application.port.inbound`, `domain`, `shared` | `infrastructure` |
| `application` | `application.port`, `domain`, `shared` | `presentation`, `infrastructure` |
| `application.port` | `domain`, `shared` | `presentation`, `application` (impl classes), `infrastructure` |
| `domain` | `shared`, `java.*` | Everything else — no Micronaut, Jakarta, SLF4J, Copilot SDK, SnakeYAML |
| `infrastructure` | `application.port.outbound`, `domain`, `shared` | `presentation`, `application` (impl classes) |
| `shared` | `java.*` only | Everything else |

**DI annotations** (`@Inject`, `@Singleton`, `@Named`, `@ConfigurationProperties`):
- Allowed in: `presentation`, `infrastructure`
- Forbidden in: `domain`, `shared`, `application` (use constructor injection without annotations; let infrastructure's DI wiring provide instances)

**`@Nullable` replacement**: All current `io.micronaut.core.annotation.@Nullable` usages in domain-bound classes → replace with `java.util.Optional` wrapping or `@javax.annotation.Nullable` (JSR-305, which is `java.*`-adjacent and acceptable in domain), or simply document nullability via Javadoc.

## §3 Sub-Package Rationale

### presentation (3 sub-packages)

| Sub-package | Purpose | Files (approx) |
|---|---|---|
| `command` | One class per CLI command: ReviewCommand, ListAgentsCommand, SkillCommand, DoctorCommand. Plus LifecycleRunner, CommandExecutor | ~8 |
| `formatter` | CliOutput, ReviewOutputFormatter, SkillOutputFormatter | ~3 |
| `parser` | CliParsing, ReviewOptionsParser, SkillOptionsParser, CliValidationException, ExitCodes, CliUsage | ~6 |

Top-level in `presentation`: ReviewApp (entry point), ReviewCommand-adjacent coordinators (ReviewExecutionCoordinator, SkillExecutionCoordinator, ReviewPreparationService, ReviewRunExecutor, ReviewRunRequestFactory, ReviewAgentConfigResolver, ReviewModelConfigResolver, ReviewAgentSelection, ReviewTargetSelection, ReviewTargetResolver, ReviewOptions, CliCommand)

**Design note**: The `ReviewExecutionCoordinator`, `SkillExecutionCoordinator`, and `ReviewPreparationService` currently in `cli` are coordination facades that sit between CLI parsing and use-cases. They stay in `presentation` because they translate CLI-specific concerns (options, exit codes, stdout formatting) into use-case calls. They do NOT contain business rules.

### application (4 sub-packages + port)

| Sub-package | Purpose | Key classes |
|---|---|---|
| `review` | Review orchestration | ReviewOrchestrator, ReviewPassRunner, ReviewRetryExecutor, RubberDuckDialogueRunner, ReviewExecutionModeRunner, OrchestratorMetrics, ReviewResultPipeline, LocalSourcePrecomputer |
| `report` | Report/summary orchestration | ReportService (→ renamed GenerateReportUseCase), SummaryGenerator |
| `skill` | Skill management | SkillService (→ renamed ExecuteSkillUseCase) |
| `agent` | Agent loading | AgentService (→ renamed LoadAgentUseCase) |

### domain (6 sub-packages)

| Sub-package | Purpose | Key classes |
|---|---|---|
| `agent` | Agent domain models & pure logic | AgentConfig, ReviewAgent, ParsedAgentMetadata, DialogueRound, AgentConfigValidator, AgentDefinitionPolicy, AgentPromptBuilder, AgentSectionParser, AgentFrontmatterMapper, ReviewSystemPromptFormatter, ReviewTargetInstructionResolver, RubberDuckPromptBuilder |
| `report` | Report domain models & pure logic | ReviewResult, AggregatedFinding, FindingsExtractor, FindingsParser, ReviewFindingParser, ReviewFindingSimilarity, FindingsSummaryFormatter, ReportContentFormatter, ReviewMergedContentFormatter, SummaryFinalReportFormatter, ReviewOverallSummaryAppender, ReviewResultMerger, ContentSanitizationPipeline, ContentSanitizationRule, ContentSanitizer, FallbackSummaryBuilder, SummaryPromptBuilder, ReviewResultFactory |
| `skill` | Skill domain models | SkillDefinition, SkillParameter, SkillResult |
| `instruction` | Instruction models & safety | CustomInstructionSafetyValidator, InstructionFrontmatter |
| `review` | Cross-cutting review models | ReviewContext, ReviewTarget, LocalFileCandidate, LocalFileSelectionConfig, PromptTexts, ReviewOptions (domain DTO copy if needed) |
| `resilience` | Resilience patterns | SharedCircuitBreaker, SynthesisStrategy, CopilotCliException, CopilotStartupErrorFormatter, CopilotTimeoutResolver |

### infrastructure (7 sub-packages)

| Sub-package | Purpose | Key classes |
|---|---|---|
| `copilot` | Copilot SDK lifecycle & sessions | CopilotService, CopilotClientStarter, CopilotHealthProbe, ReviewSessionConfigFactory, ReviewSessionExecutor, ReviewSessionMessageSender, ReviewMessageFlow, RubberDuckDialogueExecutor, SdkRubberDuckSessionFactory, ReviewContextFactory, ReviewOrchestratorFactory, ReportGeneratorFactory, CopilotPermissionHandlers, SkillExecutor, AiSummaryClient |
| `config` | Micronaut @ConfigurationProperties | All 11 config records: AgentPathConfig, CircuitBreakerConfig, CopilotConfig, ExecutionConfig, GithubMcpConfig, LocalFileConfig, ModelConfig, RubberDuckConfig, SkillConfig, SummaryConfig, TemplateConfig |
| `template` | Template file loading | TemplateRepository (implements LoadTemplatePort) |
| `file` | File I/O adapters | LocalFileCandidateCollector, LocalFileCandidateProcessor, LocalFileContentFormatter, LocalFileProvider, ReportGenerator (→ ReportFileWriter), SummaryReportWriter, ReportFileUtils |
| `auth` | Authentication | GhAuthTokenProvider, GhCliLocator, GitHubTokenResolver, CliPathResolver, CopilotCliPathResolver, TokenInputReader, TokenReadUtils |
| `logging` | Logging infrastructure | LogbackLevelSwitcher, SecurityAuditLogger |
| `parsing` | External-lib parsers | FrontmatterParser (SnakeYAML), AgentMarkdownParser, SkillMarkdownParser, AgentConfigLoader, SkillRegistry |

### shared (flat)

8 files — pure utilities with `java.*` only imports. No sub-packages needed.

| File | Purpose |
|---|---|
| PlaceholderUtils | Template placeholder substitution |
| ExecutorUtils | ExecutorService helpers |
| StructuredConcurrencyUtils | Virtual thread structured concurrency |
| RetryExecutor | Generic retry logic (circuit breaker ref removed — uses port) |
| RetryPolicyUtils | Retry policy computation |
| TokenHashUtils | Token hashing |
| ExecutionCorrelation | Correlation ID generation |
| ReportFilenameUtils | Report filename construction |
| ConfigDefaults | Default config value constants |
| SensitiveHeaderMasking | Header masking utility |
