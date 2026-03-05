# Copilot Instructions

## Build & Test

```bash
# Build (fat JAR)
mvn clean package

# Build native image (GraalVM 26 EA required)
mvn clean package -Pnative

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ModelConfigTest

# Run a single test method
mvn test -Dtest=ModelConfigTest#testDefaultValues

# Skip tests during build
mvn clean package -DskipTests
```

Requires **GraalVM 26 EA (Java 26)** — managed via `.sdkmanrc` with SDKMAN.

## Architecture

This is a CLI application that orchestrates multiple AI agents to review code in parallel using the GitHub Copilot SDK for Java.

**Flow**: `ReviewApp` (entry point) → CLI parsing → `ReviewCommand` / `ListAgentsCommand` / `SkillCommand` → `ReviewOrchestrator` (parallel agent dispatch) → `ReviewAgent` (per-agent LLM calls via `CopilotService`) → `ReportGenerator` / `SummaryGenerator` (output).

**Key layers**:
- **CLI** (`cli/`): Custom argument parser — no framework (no Picocli). `CliParsing` uses records for option state, commands are routed manually in `ReviewApp.run()`.
- **Agent** (`agent/`): Loads `.agent.md` files (YAML frontmatter + Markdown sections) into `AgentConfig`. Agents are defined externally in `agents/` or `.github/agents/`.
- **Orchestrator** (`orchestrator/`): Runs agents in parallel using Java virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`). Each agent gets a `CompletableFuture` with configurable timeout.
- **Service** (`service/`): `CopilotService` wraps the Copilot SDK client, resolving the CLI binary path and authenticating via `gh auth`. `TemplateService` loads Mustache-style `{{placeholder}}` templates from `templates/`.
- **Skill** (`skill/`): Individual executable tasks defined within agent `.agent.md` files under `## Skills` sections.
- **Config** (`config/`): Micronaut `@ConfigurationProperties` records binding to `application.yml` under `reviewer.*`.

**External runtime dependency**: Requires GitHub Copilot CLI (`gh copilot`) to be installed and authenticated. `CopilotService` validates CLI health on startup.

## Conventions

### DI & Framework
- **Micronaut** for DI — use `@Singleton`, `@Inject` (constructor injection only), `@ConfigurationProperties`.
- No Spring dependencies. No Picocli. CLI parsing is hand-rolled in `cli/`.

### Data Modeling
- **Prefer Java records** for immutable data types (`ModelConfig`, `ExecutionConfig`, `CustomInstruction`, `SkillDefinition`, `ReviewResult`, etc.).
- Use **compact constructors** in records for validation and default values.
- Use `List.copyOf()` / `Map.copyOf()` for defensive copies in constructors.
- Use the **builder pattern** (static inner `Builder` class) for records with many optional fields (see `ModelConfig.builder()`).

### Error Handling
- Use Java 22+ unnamed variables in catch blocks: `catch (InterruptedException _)`.
- Custom `CliValidationException` for CLI input errors (carries a `showUsage` flag).
- Failed agent executions return a `ReviewResult` with error info rather than throwing — the orchestrator never aborts due to a single agent failure.

### Testing
- **JUnit 5** + **AssertJ** (fluent assertions). Micronaut Test for integration tests.
- `@DisplayName` with Japanese descriptions, `@Nested` for grouping, `@TempDir` for filesystem tests.

### Templates
- Report/prompt templates live in `templates/` as Markdown files with `{{placeholder}}` syntax.
- Template paths are configurable in `application.yml` under `reviewer.templates.*`.

### Agent Definitions
- `.agent.md` files use YAML frontmatter (`name`, `description`, `model`) and Markdown sections (`## Role`, `## Instruction`, `## Focus Areas`, `## Output Format`, `## Skills`).
- Instruction placeholders: `${repository}`, `${displayName}`, `${focusAreas}`.
- Agent content is written in Japanese.

### Logging
- SLF4J with Logback. `--verbose` flag enables debug-level output.
