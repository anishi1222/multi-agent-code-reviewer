
## [t8] Phase 1 foundation — shared layer, domain core types, 12 port interfaces

- **Brownfield coexistence**: Key gotcha: compiler sees both old and new packages simultaneously. New files coexist without old files being touched. Only naming conflicts would break compilation — none occurred because package names differ (e.g. `util.PlaceholderUtils` vs `shared.PlaceholderUtils`).
- **CircuitBreaker interface**: Placing the `CircuitBreaker` interface in `shared` (not `domain.resilience`) breaks the circular dependency between `shared.RetryExecutor` and `domain.resilience.SharedCircuitBreaker`. Both layers depend on `shared.*`, not each other.
- **MDC stripping pattern**: `ExecutionCorrelation` in shared strips ALL MDC methods (they use SLF4J MDC). The MDC code stays in the old `util.ExecutionCorrelation` and will migrate to `infrastructure.logging` in T010. Future tasks using the domain `ExecutionCorrelation` only get UUID generation + `CheckedSupplier`.
- **SnakeYAML isolation**: `InstructionFrontmatter` in domain uses a simple `---` delimiter regex + line parser. This is sufficient for the scalar key: value frontmatter fields the domain cares about. YAML-complex types (lists, nested maps) stay in infrastructure's `FrontmatterParser`.
- **`@Nullable` removal**: Just omit the annotation entirely — Java already supports null without annotation. Don't replace with `Optional` for record fields (breaks compact constructor compactness).
- **Hardcoded constants**: When a domain class needs config constants previously sourced from infrastructure config classes (e.g. `CircuitBreakerConfig.DEFAULT_FAILURE_THRESHOLD`), simply inline them. The constant belongs to the domain entity that uses it, not to an external config class.
- **Port DTOs placement**: Inbound port DTOs live in `application.port.inbound` (alongside their ports). Outbound port DTOs live in `application.port.outbound`. This avoids a separate `dto` package and makes co-location obvious.
- **`McpServerSpec`**: New domain DTO needed for `SessionRequest` / `RubberDuckRequest` because the existing `McpServerConfig` is an SDK type (`com.github.copilot.rpc.McpServerConfig`). The infrastructure adapter maps domain → SDK at the boundary.
- **Test pattern for safety validator**: The `containsSuspiciousPattern` patterns are specific regex anchors — test strings must match the actual regex, not just "sound like injection". Used the actual pattern texts when writing tests.
- **Learnings consumed**: none (first backend task in this project)

## [t9] Phase 2 — agent domain models (T004) + review orchestration use-cases (T005)

- **T004 key discovery**: `AgentFrontmatterMapper` had to be redesigned — the original accepted `FrontmatterParser.Parsed` which is an infrastructure type. New signature: `(Map<String,String>, String, String)`.
- **T005 key discovery**: `ReviewTargetInstructionResolver.ResolvedInstruction` dropped `Map<String,McpServerConfig>` — MCP server handling is purely infrastructure. The domain class resolves instruction text only.
- **`StructuredConcurrencyUtils`** is in `shared.*` (not old `util.*`), so `ReviewExecutionModeRunner` can use it without import violations.
- **`RunReviewPort.execute()` returns single `ReviewResult`** — consolidates all agent results by joining content with `---` dividers; `success = anyAnySuccess`.
- **Executor lifecycle**: `ReviewOrchestrator.execute()` creates `ExecutorResources` per-invocation and shuts down in `finally` block — avoids needing `AutoCloseable`.
- **Dead code in `RubberDuckDialogueRunner`**: `buildSynthesisContent()` and `loadSynthesisTemplate()` were leftover from earlier draft; removed before build.
- **`ReviewRetryExecutor` per-pass**: a new `ReviewRetryExecutor` instance is created per review pass (with name `agentName#passNumber`) — this is correct since each pass is independent.
- Learnings consumed: [backend/domain-purification-patterns.md]

## [t10] Phase 3 — report, skill, and diagnostics application layers (T006–T008)
- Domain.report files (17) + RunDiagnosticsUseCase were already committed by the t9 session; t10 committed the remaining 4 application files: SummaryGenerator, GenerateReportUseCase, LoadAgentUseCase, ExecuteSkillUseCase.
- **Visibility gotcha**: domain classes created as package-private (`final class` without `public`) prevent cross-package use from application layer. Always declare domain collaborators `public` even if conceptually internal.
- **Cycle-9 fix pattern**: mutual dependency broken by introducing top-level `ReviewFinding` record; extractors return it, formatters accept it; no shared import chain. Pre-compute in application layer: `extractAll()` → `formatSummary()` → pass string to `format()`.
- **TemplateService substitution**: application layer loads raw templates via `LoadTemplatePort.loadRaw(key)` and passes `String` params to domain constructors. Domain classes stay I/O-free. Pattern is reusable for any domain object needing externally-managed content.
- **AgentLoader functional interface**: use a `@FunctionalInterface` in application layer to decouple from brownfield infrastructure. Infrastructure injects lambda. Pattern eliminates direct brownfield imports at application boundary.
- `ExecuteSkillUseCase` is a stub (T010 pending). Design: single class, return `SkillResult.failure()`, no imports from infrastructure needed.
- Build 907/907 passing after visibility fixes. No new test failures introduced.
- Learnings consumed: [backend/domain-purification-patterns]

## [t9.1] Remediate RunReviewPort — restore OUT-02/OUT-03 per-agent file reachability
- **Root cause**: `aggregateResults()` in `ReviewOrchestrator` joined all per-agent `ReviewResult`
  objects into a single blob before returning from `RunReviewPort.execute()`. The port's
  `ReviewResult` (singular) return type enforced the collapse.
- **Fix pattern**: Port → `List<ReviewResult>`; remove aggregation; add `passNumber` field to
  domain record; tag in `ReviewPassRunner`; fix filenames in `GenerateReportUseCase`.
- **Record + Builder**: Adding a new field to a Java `record` that uses a custom `Builder` requires
  updating the `build()` method positional call. Existing call sites via Builder were unaffected
  (default `passNumber=0`). The `withPassNumber()` wither pattern (canonical constructor call)
  is cleaner than a full builder chain for pass-tagging in a loop.
- **Null safety on agentConfig**: `writePerAgentReports()` was calling `result.agentConfig().name()`
  without null check — error-path results can have `null` agentConfig. Fixed with `!= null ?` guard
  defaulting to `"unknown"`.
- **Test isolation held**: 907 brownfield tests all pass; new application-layer classes have no new
  test coverage yet (out of scope for this remediation).
- Learnings consumed: [backend/domain-purification-patterns, backend/orchestrator-per-invocation-resources]

## [t11] Phase 4 infrastructure adapters — copilot SDK + support (32 files, 907 tests pass)

### Codebase/domain discoveries
- `CopilotClientOptions` SDK 1.0.6: methods are `setCliPath()`, `setAutoRestart()`, `setUseLoggedInUser()`, `setLogLevel()` — NOT `setCopilotClientPath()`, `setSdkLogLevel()`, or any `setCopilotClientPath` variant
- `McpHttpServerConfig extends McpServerConfig` but Java generics invariance means `Map<String,McpHttpServerConfig>` is NOT assignable to `Map<String,McpServerConfig>`. Fix: cast `(McpServerConfig) new McpHttpServerConfig()`
- `CopilotCliPathResolver.resolveCliPath()` NOT `resolve()`. The package-private `CLI_PATH_ENV` field must be `public` for cross-package use
- `SummaryFinalReportFormatter.format()` takes 5 args including `findingsSummary` — not 4. Always read actual method signature before calling
- New domain `AgentConfig` record had no `validateRequired()` — added it explicitly (throws `IllegalStateException` on blank name/model). Brownfield had `AgentConfigValidator.validateRequired()` delegation
- `SkillDefinition.buildPrompt(Map, int)` — NOT `renderPrompt()`. Second arg is `maxParameterValueLength` (use 4096)
- `SkillRegistry` methods: `get(String)` → `Optional<SkillDefinition>` and `getAll()` → `List<SkillDefinition>` — not `findById()`/`listAll()`
- The new domain `ReviewOrchestrator` builds its own `ReviewContext` internally from `OrchestratorConfig` — so `ReviewContextFactory` in infra is only an `OrchestratorConfig` builder

### Wrong assumptions and corrections
- Assumed `CLI_PATH_ENV` was public because brownfield used it across packages — it was package-private in the new impl, required explicit `public`
- Assumed `buildClientNotInitializedMessage()` existed on formatter — had to add it
- Assumed 4 params for `format()` call in `SummaryReportWriter` — was 5, always verify signatures

### Techniques/patterns worth reusing
- When SDK generics invariance blocks assignment, use explicit cast to supertype: `(McpServerConfig) new McpHttpServerConfig()`
- For domain records needing validation: add `validateRequired()` method that throws `IllegalStateException` — clean, no external validator needed
- `volatile CopilotClient` in `CopilotService` with `@PostConstruct` eager init and `@PreDestroy` shutdown covers thread-safe lifecycle

### Learnings consumed
- backend/sdk-api-verification (read SDK javap output before coding to correct method names)

## [t12] Phase 5 — presentation layer + ArchUnit boundary tests

- **Java sub-package visibility**: ALL classes in `presentation/` root used by `presentation/command/` or `presentation/parser/` must be `public`. Java's package access does NOT extend to sub-packages. Sealed interfaces, records, exceptions, utility classes — everything needs `public`.
- **Micronaut `@Factory` pattern**: To bind a port interface to an implementation, create a `@Factory` class with `@Singleton` methods returning the port interface type. The factory itself needs no special name — Micronaut discovers it via the annotation.
- **`@Singleton` missing on infra classes**: `CopilotClientStarter`, `LocalFileProvider`, `ReviewSessionConfigFactory` all lacked `@Singleton`. The old CLI layer never triggered their DI wiring. The new presentation layer's constructor-injection chain exposed the missing annotations immediately at context startup.
- **`SummaryGenerator` template constants were wrong**: Used `"summary/user-prompt"` (slash, no `.md`) but actual files are `"summary-prompt.md"` (dash, `.md`). `TemplateRepository.loadRaw()` validates `[A-Za-z0-9._/-]+\.md` — fails on names without `.md`. Fix: align constants with actual file names.
- **ArchUnit `failOnEmptyShould=true`** (default): If `that()` predicate matches 0 classes, rule fails. For brownfield phases with partially populated packages, use `archunit.properties` with `archRule.failOnEmptyShould=false`.
- **ArchUnit and Micronaut-generated synthetics**: `$ReviewApp$Definition` (generated by Micronaut annotation processor) lives in the root package and references `presentation.*`. Exclude via `haveNameNotMatching(".*\\$.*")` regex on the full class name.
- **`haveSimpleNameNotContaining("$")` doesn't exclude `$ReviewApp$Definition`**: ArchUnit's `getSimpleName()` for top-level synthetic classes behaves unexpectedly. Use full-name regex `haveNameNotMatching(".*\\$.*")` instead.
- Learnings consumed: backend/archunit-micronaut-synthetic-exclusion, backend/micronaut-factory-port-binding, backend/java-subpackage-visibility
