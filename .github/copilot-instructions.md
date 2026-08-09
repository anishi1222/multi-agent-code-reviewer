# Copilot Instructions

## Build & Test

```bash
# Full JVM build, tests, and packaged-JAR CLI smoke (Java 28)
./mvnw -B clean verify
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar --version

# Native image (activate Oracle GraalVM 25.0.4 first)
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.4-graal"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B clean verify -Pnative -f pom-native.xml

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ModelConfigTest

# Run a single test method
./mvnw test -Dtest=ModelConfigTest#testDefaultValues

# Diagnostic-only package without tests (never use for release verification)
./mvnw clean package -DskipTests
```

The default build requires **Java 28 with preview features**; `.sdkmanrc` selects that
toolchain. The separate `pom-native.xml` path targets **Java 25** and requires explicit
GraalVM 25 activation. `docs/runbook.md` is the authoritative toolchain and release procedure.

## Architecture

This is a CLI application that orchestrates multiple AI agents to review code in parallel using the GitHub Copilot SDK for Java.

**Flow**: `ReviewApp` (composition root / entry point) → CLI parsing (`presentation/parser/`) → `ReviewCommand` / `ListAgentsCommand` / `SkillCommand` / `DoctorCommand` (`presentation/command/`) → inbound port → `ReviewOrchestrator` (parallel agent dispatch) → `ReviewAgent` (per-agent LLM calls via outbound ports) → `GenerateReportUseCase` / `SummaryGenerator` (output).

**Layers** (Ports & Adapters — see [ADR-0006](../docs/adr/0006-ports-and-adapters-layering.md)):
- **Composition root** (`dev.logicojp.reviewer`): `ReviewApp` plus the Micronaut `@Factory` classes. Wiring only; no other layer depends on it.
- **Presentation** (`presentation/`): Custom argument parser — no framework (no Picocli). Records for option state; commands routed manually. **Must not import `infrastructure`.**
- **Application** (`application/`): use-case coordination (`application/review/`, `application/report/`, `application/agent/`, `application/skill/`). Runs agents in parallel on virtual threads.
- **Ports** (`application/port/inbound/`, `application/port/outbound/`): boundary contracts. Direction is decided by *who implements it* — inbound = implemented by `application`, outbound = implemented by `infrastructure` (ADR-0006 D2).
- **Domain** (`domain/`): business rules and models — `AgentConfig`, `AgentDefinitionPolicy`, prompt builders. JDK + `shared` only; no Micronaut / Jakarta / SLF4J / Copilot SDK.
- **Infrastructure** (`infrastructure/`): adapters — `CopilotService` wraps the Copilot SDK client and authenticates via `gh auth`; `TemplateRepository` loads templates from `templates/`; `infrastructure/config/` holds Micronaut `@ConfigurationProperties` records binding `application.yml` under `reviewer.*`.
- **Shared** (`shared/`): cross-layer defaults and utilities only (ADR-0006 D6).

Agent definitions are loaded from `.agent.md` files in `agents/` or `.github/agents/`. Because those paths are **CWD-relative**, definitions supplied by the repository under review are **untrusted input** — see [ADR-0007](../docs/adr/0007-agent-definition-trust-model-and-secret-sink-boundary.md) for the trust model and the per-field schema contract.

Layer boundaries are enforced by `LayerDependencyRulesTest`, which uses the JDK's `java.lang.classfile` API (not ArchUnit — see that test's Javadoc for why).

**External runtime dependency**: Requires GitHub Copilot CLI (`gh copilot`) to be installed and authenticated. `CopilotService` validates CLI health on startup.

## Conventions

### DI & Framework
- **Micronaut** for DI — use `@Singleton`, `@Inject` (constructor injection only), `@ConfigurationProperties`.
- No Spring dependencies. No Picocli. CLI parsing is hand-rolled in `presentation/parser/`.

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
- Report/prompt templates live in `templates/` as Markdown files using `${key}` placeholder syntax (resolved by `shared/PlaceholderUtils`; there is no Mustache dependency).
- Template paths are configurable in `application.yml` under `reviewer.templates.*`.

### Agent Definitions
- `.agent.md` files use YAML frontmatter (`name`, `description`, `model`) and Markdown sections (`## Role`, `## Instruction`, `## Focus Areas`, `## Output Format`, `## Skills`).
- Instruction placeholders: `${repository}`, `${displayName}`, `${focusAreas}`.
- Agent content is written in Japanese.

### Logging
- SLF4J with Logback. `--verbose` flag enables debug-level output.
