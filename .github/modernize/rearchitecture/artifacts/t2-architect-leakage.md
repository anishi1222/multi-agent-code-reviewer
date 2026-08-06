# t2 — Framework/SDK Leakage Analysis

Per-file inventory of external framework imports that must be confined per constitution §3, §7.6, §7.7.

## Copilot SDK (`com.github.copilot.*`) — 20 files

Must reside ONLY in `infrastructure` layer (§7.6).

| Current Package | File | SDK Types Used |
|---|---|---|
| agent | ReviewContext | CopilotClient, McpServerConfig |
| agent | ReviewPassRunner | McpServerConfig |
| agent | ReviewSessionConfigFactory | SystemMessageMode, McpServerConfig, SessionConfig, SystemMessageConfig |
| agent | ReviewSessionExecutor | CopilotSession, McpServerConfig, SessionConfig |
| agent | ReviewSessionMessageSender | CopilotSession, AssistantMessageEvent, MessageOptions |
| agent | ReviewTargetInstructionResolver | McpServerConfig |
| agent | RubberDuckDialogueExecutor | McpServerConfig |
| agent | RubberDuckSessionFactory | McpServerConfig |
| agent | SdkRubberDuckSessionFactory | CopilotSession, SystemMessageMode, McpServerConfig, SessionConfig, SystemMessageConfig |
| cli | DoctorCommand | ConnectionState, CopilotClient, GetAuthStatusResponse, GetStatusResponse |
| config | GithubMcpConfig | McpHttpServerConfig, McpServerConfig |
| orchestrator | OrchestratorCollaborators | McpServerConfig |
| orchestrator | ReviewContextFactory | CopilotClient, McpServerConfig |
| orchestrator | ReviewOrchestrator | CopilotClient, McpServerConfig |
| orchestrator | ReviewOrchestratorFactory | CopilotClient |
| report.factory | ReportGeneratorFactory | CopilotClient |
| report.summary | AiSummaryClient | CopilotClient, CopilotSession, SystemMessageMode, MessageOptions, SessionConfig, SystemMessageConfig |
| report.summary | SummaryGenerator | CopilotClient |
| skill | SkillExecutor | CopilotClient, SystemMessageMode, McpServerConfig, MessageOptions, SessionConfig, SystemMessageConfig |
| util | CopilotPermissionHandlers | PermissionHandler, PermissionRequestResult, PermissionRequestResultKind |

**Migration impact**: 20 files across 8 packages → all must move to or be wrapped by `infrastructure` adapters.

## Micronaut (`io.micronaut.*`) — 24 files

Allowed in `infrastructure` and `presentation` only (§7.7).

| Current Package | File | Micronaut Types |
|---|---|---|
| (root) | ReviewApp | ApplicationContext, Environment |
| agent | AgentConfig | @Nullable |
| agent | ReviewContext | @Nullable |
| agent | ReviewTargetInstructionResolver | @Nullable |
| cli | ReviewTargetResolver | @Nullable |
| config | AgentPathConfig | @ConfigurationProperties |
| config | CircuitBreakerConfig | @ConfigurationProperties |
| config | CopilotConfig | @ConfigurationProperties, @Bindable, @Nullable |
| config | ExecutionConfig | @ConfigurationProperties, @Bindable, @Nullable |
| config | GithubMcpConfig | @ConfigurationProperties, @Nullable |
| config | LocalFileConfig | @ConfigurationProperties, @Nullable |
| config | ModelConfig | @ConfigurationProperties |
| config | RubberDuckConfig | @ConfigurationProperties, @Nullable, @Bindable |
| config | SkillConfig | @ConfigurationProperties, @Nullable |
| config | SummaryConfig | @ConfigurationProperties |
| config | TemplateConfig | @ConfigurationProperties, @Nullable |
| orchestrator | OrchestratorConfig | @Nullable |
| orchestrator | ReviewOrchestratorFactory | @Nullable |
| report.core | ReviewResult | @Nullable |
| service | ReviewService | @Nullable |
| skill | SkillExecutor | @Nullable |
| util | GhCliLocator | @Nullable |
| util | TokenInputReader | @Nullable |

**Note**: Many `@Nullable` usages can be replaced with `java.util.Optional` or `@javax.annotation.Nullable`. `@ConfigurationProperties` classes move to `infrastructure.config`.

## Jakarta (`jakarta.*`) — 32 files

Allowed in `infrastructure` and `presentation` only (§7.7).

Concentrated in `cli` (16 files) and `service` (10 files) for `@Inject`/`@Singleton` DI wiring. Also in `agent` (1), `orchestrator` (1), `report.factory` (1), `skill` (1), `util` (1).

## SLF4J (`org.slf4j.*`) — 50 files

Domain layer (§3) MUST NOT use SLF4J. 50 files across nearly every package. Domain-destined classes (pure logic validators, parsers, formatters) must either drop logging or use `java.util.logging`.

## SnakeYAML — 1 file

`util/FrontmatterParser.java` — moves to `infrastructure` (parser adapter).

---

## Per-Package Migration Risk

| Package | Files | SDK | Micronaut | Jakarta | Risk | Notes |
|---|---|---|---|---|---|---|
| agent | 30 | 8 | 3 | 1 | **CRITICAL** | Must split across domain/infra/application; largest decomposition |
| cli | 28 | 1 | 1 | 16 | **HIGH** | Maps to presentation; DI wiring is natural there |
| service | 13 | 2 | 1 | 10 | **HIGH** | Must decompose into application use-cases + infrastructure adapters |
| config | 13 | 1 | 11 | 0 | **MEDIUM** | Moves wholesale to infrastructure.config |
| orchestrator | 15 | 3 | 2 | 1 | **HIGH** | Maps to application; SDK refs must be pushed to infra |
| report.* | 22 | 3 | 1 | 1 | **HIGH** | 4 internal cycles to break; domain logic + infra I/O split |
| skill | 6 | 1 | 1 | 1 | **MEDIUM** | Domain types + infra executor split |
| util | 14+ | 1 | 3 | 1 | **MEDIUM** | Pure utils → shared; I/O utils → infrastructure |
| target | 7 | 0 | 0 | 0 | **LOW** | File I/O → infrastructure; DTOs → domain |
| instruction | 2 | 0 | 0 | 0 | **LOW** | Pure logic → domain |
