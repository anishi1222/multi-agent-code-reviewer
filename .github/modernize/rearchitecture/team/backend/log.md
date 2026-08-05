
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
