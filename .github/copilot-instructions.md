# Copilot Instructions

## Build & Test

```bash
# Compile
mvn compile

# Build (fat JAR)
mvn clean package

# Build native image (GraalVM 26 required)
mvn clean package -Pnative

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ModelConfigTest

# Run a single test method
mvn test -Dtest=ModelConfigTest#testDefaultValues

# Skip tests during build
mvn clean package -DskipTests

# Run
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar run --repo owner/name
```

Requires **GraalVM 26 (Java 26)** — managed via `.sdkmanrc` with SDKMAN.

## Architecture

This is a CLI application that orchestrates multiple AI agents to review code in parallel using the GitHub Copilot SDK for Java.

**Flow**: `ReviewApp` (entry point) → CLI parsing → `ReviewCommand` / `ListAgentsCommand` / `SkillCommand` → `ReviewOrchestrator` (parallel agent dispatch via `StructuredTaskScope` + `Semaphore`) → `ReviewAgent` (per-agent LLM calls via `CopilotService`) → `ReportService` → `ReportGenerator` / `SummaryGenerator` (output).

**Key layers**:
- **CLI** (`cli/`): Custom argument parser — no framework (no Picocli). `CliParsing` uses records for option state, commands are routed manually in `ReviewApp.run()`.
- **Agent** (`agent/`): Loads `.agent.md` files (YAML frontmatter + Markdown sections) into `AgentConfig`. `ReviewAgent` handles per-agent execution with retry, jittered backoff, and circuit breaker support.
- **Orchestrator** (`orchestrator/`): Runs agents in parallel using `StructuredTaskScope` with semaphore-based concurrency limiting. Virtual threads via `Thread.ofVirtual().factory()`. Intermediate results are checkpointed to disk for crash recovery.
- **Service** (`service/`): `CopilotService` wraps the Copilot SDK client lifecycle (path resolution, health check, auth, retry with backoff). `TemplateService` loads `{{placeholder}}` templates. `AgentService` and `SkillService` manage agent/skill lifecycle.
- **Report** (`report/`): `ReportGenerator` (individual reports), `SummaryGenerator` (executive summary with AI + fallback), `FindingsExtractor`, `ContentSanitizer` (LLM preamble/CoT removal), `ReportService` (orchestrates generation), `ReviewResult` (result model). File writes use atomic temp-file-then-rename.
- **Skill** (`skill/`): Individual executable tasks defined in `.github/skills/<name>/SKILL.md` files. `SkillExecutor` runs skills via `StructuredTaskScope` with retry and circuit breaker.
- **Config** (`config/`): Micronaut `@ConfigurationProperties` records binding to `application.yml` under `reviewer.*`. Key records: `ModelConfig`, `ExecutionConfig`, `ReviewerConfig`, `GithubMcpConfig`, `TemplateConfig`.
- **Target** (`target/`): `ReviewTarget` (sealed interface: `GitHubTarget` / `LocalTarget`), `LocalFileProvider` (local source collection with size/extension/sensitive-file filtering).
- **Instruction** (`instruction/`): `CustomInstructionLoader` auto-detects project instructions (`.github/copilot-instructions.md`, scoped `.instructions.md`, `.prompt.md`). `CustomInstructionSafetyValidator` checks for prompt injection patterns.
- **Util** (`util/`): `ApiCircuitBreaker` (shared circuit breaker for Copilot API), `StructuredConcurrencyUtils`, `SecurityAuditLogger` (structured audit logging via MDC), `TokenHashUtils`, `GitHubTokenResolver`, `FrontmatterParser`, `CliPathResolver`.

**External runtime dependency**: Requires GitHub Copilot CLI (`github-copilot` or `copilot`) to be installed and authenticated. `CopilotService` validates CLI health on startup with exponential backoff retries.

## Conventions

### DI & Framework
- **Micronaut** for DI — use `@Singleton`, `@Inject` (constructor injection only), `@ConfigurationProperties`.
- No Spring dependencies. No Picocli. CLI parsing is hand-rolled in `cli/`.
- Entry point: `ApplicationContext.run()` in main, then `context.getBean()`.

### Data Modeling
- **Prefer Java records** for immutable data types (`ModelConfig`, `ExecutionConfig`, `CustomInstruction`, `SkillDefinition`, `ReviewResult`, etc.).
- **Sealed interfaces** with record implementations for type-safe sum types (e.g., `ReviewTarget`).
- Use **compact constructors** in records for validation and default values.
- Use `List.copyOf()` / `Map.copyOf()` for defensive copies in constructors.
- Use the **builder pattern** (static inner `Builder` class) for records with many optional fields (see `ModelConfig.builder()`).
- `with*()` copy methods on records for creating modified copies.

### Pattern Matching
- Use exhaustive `switch` expressions with record patterns (Java 21+).
- Prefer pattern matching over `instanceof` chains.
- Use unnamed patterns (`_`) for unused bindings.

### Concurrency
- **Virtual threads** via `Thread.ofVirtual().factory()` or `Executors.newVirtualThreadPerTaskExecutor()`.
- **Structured concurrency** (`StructuredTaskScope`) for orchestrator and skill execution.
- **Semaphore** for concurrency limiting.
- Feature flags: `REVIEWER_STRUCTURED_CONCURRENCY`, `REVIEWER_STRUCTURED_CONCURRENCY_SKILLS`.

### Resilience
- **Exponential backoff with jitter** on all retry paths (`ReviewAgent`, `SummaryGenerator`, `SkillExecutor`, `CopilotService`).
- **Shared circuit breaker** (`ApiCircuitBreaker.copilotApi()`) gates API calls across all components.
- **Atomic file writes** (temp file + `ATOMIC_MOVE`) in `ReportGenerator.writeSecureString`.
- **Intermediate checkpoints** persisted per-agent in `ReviewOrchestrator` for crash recovery.
- Configurable retry counts, backoff base/max, and checkpoint directory via `application.yml`.

### Error Handling
- Use Java 22+ unnamed variables in catch blocks: `catch (InterruptedException _)`.
- Custom `CliValidationException` for CLI input errors (carries a `showUsage` flag).
- Failed agent executions return a `ReviewResult` with error info rather than throwing — the orchestrator never aborts due to a single agent failure.
- Use `IllegalArgumentException` for invalid configuration.
- Use `IllegalStateException` for programming errors / invariant violations.
- No checked exceptions in business logic.

### Testing
- **JUnit 5** + **AssertJ** (fluent assertions). Micronaut Test for integration tests.
- `@DisplayName` with Japanese descriptions, `@Nested` for grouping, `@TempDir` for filesystem tests.
- Arrange-Act-Assert pattern. Constants for reusable test data at the top of the test class.
- Package-private test classes (no `public` modifier).

### Templates
- Report/prompt templates live in `templates/` as Markdown files with `{{placeholder}}` syntax.
- Template paths are configurable in `application.yml` under `reviewer.templates.*`.

### Agent Definitions
- `.agent.md` files use YAML frontmatter (`name`, `description`, `model`) and Markdown sections (`## Role`, `## Instruction`, `## Focus Areas`, `## Output Format`).
- Instruction placeholders: `${repository}`, `${displayName}`, `${name}`, `${focusAreas}`.
- Agent content is written in Japanese.
- Agents are defined externally in `agents/` or `.github/agents/`.

### Skill Definitions
- Skills live in `.github/skills/<name>/SKILL.md` with YAML frontmatter (`name`, `description`, `metadata.agent`) and Markdown body as prompt template.
- Prompt placeholders: `${paramName}` replaced at execution time.

### Logging
- SLF4J with Logback. `--verbose` flag enables debug-level output.
- Runtime level control via Logback API (for `--verbose` CLI flag).
- MDC fields `event.category` / `event.action` used for structured security audit logging.
- Do not log sensitive values (tokens, keys).

### Naming
- Classes: `PascalCase` (e.g., `ReviewOrchestrator`, `AgentConfig`).
- Methods: `camelCase` with descriptive verbs (e.g., `executeReview`, `buildInstruction`).
- Constants: `UPPER_SNAKE_CASE`.
- Packages: singular nouns (e.g., `agent`, `config`, `report`).
- Test classes: `{ClassName}Test`.
- Javadoc: `///` (Java 23+ markdown doc-comments) for public API.

### Code Style
- Prefer `var` for local variables when the type is obvious from context.
- Use text blocks (`"""`) for multi-line strings.
- `List.of()`, `Map.of()`, `Set.of()` for immutable collections.
- `Optional` for return values, never for parameters or fields.
- `StringBuilder` for string concatenation in loops or large strings.
- No wildcard imports — always use explicit imports.

## Important Notes

- This is a CLI application, NOT a web server — no HTTP endpoints
- The project uses `--enable-preview` JVM flag everywhere (compile, test, run)
- Tokens/secrets come from environment variables or `gh auth token` — never hardcode
- Output reports go to `./reports/` directory by default
- Japanese is used for user-facing output, display names, and test descriptions
- English is used for code identifiers, comments, and documentation
- Follow the principle of least privilege for permissions (e.g., GitHub token scopes)
- Use feature flags for experimental features to allow easy rollback
- Always validate external inputs (CLI args, API responses) and fail fast with clear error messages
- No need for excessive advertising. Focus on clean, maintainable code and robust error handling.
- There is no need to use emojis and/or flashy expressions. Just write simply and honestly. Keep it simple and professional.