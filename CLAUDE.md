# Multi-Agent Code Reviewer - Development Guide

## Project Overview

CLI tool that executes parallel code reviews using multiple AI agents (GitHub Copilot SDK).
Reviews GitHub repositories or local directories, generating Markdown reports with findings.

## Tech Stack

- **Java 26** with `--enable-preview` (structured concurrency, record patterns, sealed interfaces)
- **Micronaut 4.10.x** — compile-time DI, no reflection at runtime
- **Maven** build with `micronaut-maven-plugin`
- **GraalVM Native Image** support (profile: `native`)
- **Copilot SDK 1.0.9** for AI agent interactions
- **JUnit 5 + AssertJ + Micronaut Test** for testing
- **Logback** for logging (runtime level control via `--verbose` flag)

## Build & Test Commands

```bash
# Compile
mvn compile

# Run tests
mvn test

# Package (fat JAR)
mvn package

# Native image
mvn -Pnative package

# Run
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar run --repo owner/name
```

## Architecture

```
ReviewApp (CLI entry point)
  -> ReviewCommand / SkillCommand / ListAgentsCommand
    -> CopilotService (Copilot SDK client)
    -> ReviewOrchestrator (parallel agent execution with Semaphore + virtual threads)
      -> ReviewAgent (per-agent execution with timeout/retry)
    -> ReportService -> ReportGenerator / SummaryGenerator / FindingsExtractor
```

### Key Packages

| Package | Responsibility |
|---------|---------------|
| `cli` | CLI argument parsing, usage help, exit codes |
| `agent` | Agent configuration, execution, validation |
| `config` | Application config (`ModelConfig`, `ExecutionConfig`, `GithubMcpConfig`) |
| `orchestrator` | Parallel execution with concurrency control |
| `service` | Business logic (Copilot, Report, Template services) |
| `report` | Report generation, findings extraction, content sanitization |
| `target` | Review target abstraction (GitHub / local directory) |
| `instruction` | Custom instruction loading from target directories |
| `skill` | Agent skill definitions, registry, markdown parsing |
| `util` | Feature flags, executor utils, structured concurrency helpers |

## Coding Conventions

### Micronaut DI

- Use `@Singleton` scope (not `@Prototype`) unless there's a specific reason
- **Constructor injection only** — annotate with `@Inject`. No field injection.
- Use `@ConfigurationProperties("reviewer.*")` for externalized configuration
- Entry point: `ApplicationContext.run()` in main, then `context.getBean()`

```java
@Singleton
public class MyService {
    private final DependencyA depA;
    private final DependencyB depB;

    @Inject
    public MyService(DependencyA depA, DependencyB depB) {
        this.depA = depA;
        this.depB = depB;
    }
}
```

### Data Modeling

- **Records** for all immutable data carriers (configs, results, DTOs)
- **Sealed interfaces** with record implementations for type-safe sum types
- Use **compact constructors** for validation and default values in records
- Use `List.copyOf()` in record constructors to enforce immutability
- **Builder pattern** (nested static class) for records with many fields
- `with*()` copy methods on records for creating modified copies

```java
public sealed interface ReviewTarget permits ReviewTarget.LocalTarget, ReviewTarget.GitHubTarget {
    record LocalTarget(Path directory) implements ReviewTarget {}
    record GitHubTarget(String repository) implements ReviewTarget {}
}
```

### Pattern Matching

- Use exhaustive `switch` expressions with record patterns (Java 21+)
- Prefer pattern matching over `instanceof` chains
- Use unnamed patterns (`_`) for unused bindings

```java
return switch (target) {
    case ReviewTarget.LocalTarget(Path directory) -> handleLocal(directory);
    case ReviewTarget.GitHubTarget(String repository) -> handleGitHub(repository);
};
```

### Concurrency

- **Virtual threads** via `Thread.ofVirtual().factory()` or `Executors.newVirtualThreadPerTaskExecutor()`
- **Structured concurrency** (`StructuredTaskScope`) behind feature flags
- **Semaphore** for concurrency limiting
- `CompletableFuture` with `.orTimeout()` for deadline enforcement
- Feature flags: `REVIEWER_STRUCTURED_CONCURRENCY`, `REVIEWER_STRUCTURED_CONCURRENCY_SKILLS`

### Error Handling

- Custom exceptions extending `RuntimeException` for domain errors
- Validate at system boundaries (CLI input, external API responses)
- Use `IllegalArgumentException` for invalid configuration
- Use `IllegalStateException` for programming errors / invariant violations
- No checked exceptions in business logic

### Logging

- SLF4J API (`LoggerFactory.getLogger(ClassName.class)`)
- Runtime level control via Logback API (for `--verbose` CLI flag)
- Use `logger.debug()` for troubleshooting, `logger.info()` for operations
- Do not log sensitive values (tokens, keys)

### Naming

- Classes: `PascalCase` (e.g., `ReviewOrchestrator`, `AgentConfig`)
- Methods: `camelCase` with descriptive verbs (e.g., `executeReview`, `buildInstruction`)
- Constants: `UPPER_SNAKE_CASE`
- Packages: singular nouns (e.g., `agent`, `config`, `report`)
- Test classes: `{ClassName}Test`
- Javadoc: `///` (Java 23+ markdown doc-comments) for public API

### Code Style

- Prefer `var` for local variables when the type is obvious from context
- Use text blocks (`"""`) for multi-line strings
- `List.of()`, `Map.of()`, `Set.of()` for immutable collections
- `Optional` for return values, never for parameters or fields
- `StringBuilder` for string concatenation in loops or large strings
- No wildcard imports — always use explicit imports

## Testing Conventions

- **JUnit 5** with `@Nested` classes for test organization
- **`@DisplayName`** in Japanese describing the test scenario
- **AssertJ** fluent assertions — never use JUnit assertions directly
- Arrange-Act-Assert pattern
- Test class-level `@DisplayName` matches the class under test name
- Constants for reusable test data at the top of the test class
- Package-private test classes (no `public` modifier)

```java
@DisplayName("AgentConfig")
class AgentConfigTest {

    @Nested
    @DisplayName("コンストラクタ - デフォルト値")
    class DefaultValues {

        @Test
        @DisplayName("nameがnullの場合は空文字列に設定される")
        void nullNameDefaultsToEmptyString() {
            AgentConfig config = new AgentConfig(null, ...);
            assertThat(config.name()).isEmpty();
        }
    }
}
```

## GraalVM Native Image

- Reflection config: `src/main/resources/META-INF/native-image/reflect-config.json`
- Resource config: `src/main/resources/META-INF/native-image/resource-config.json`
- Reachability metadata: `src/main/resources/META-INF/native-image/reachability-metadata.json`
- When adding new classes that need reflection (e.g., for Jackson/Logback), update `reflect-config.json`
- When adding new resource files, update `resource-config.json`
- Build flags: `--no-fallback -O1 --enable-preview --enable-url-protocols=https`

## Agent Configuration

Agent definitions live in `agents/*.agent.md` using YAML frontmatter + Markdown body.
Parsed by `AgentMarkdownParser`. Template placeholders: `${repository}`, `${displayName}`, `${name}`, `${focusAreas}`.

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