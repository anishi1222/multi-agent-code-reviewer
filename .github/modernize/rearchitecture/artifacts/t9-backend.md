# t9 — Phase 2: Agent Domain Models and Review Orchestration Use-Cases

## Summary

Tasks T004 (migrate agent domain models to pure domain layer) and T005 (create review
orchestration use-cases in the application layer) are complete.

All 21 new files compile cleanly. The full build passes **907 tests, 0 failures**.
No old files were deleted (brownfield coexistence rule observed).

---

## Deliverables

### T004 — Domain Layer Files (10/10)

| File | Notes |
|------|-------|
| `domain/agent/AgentConfigValidator.java` | SLF4J → `java.util.logging` |
| `domain/agent/AgentDefinitionPolicy.java` | SLF4J → `java.util.logging`; Map-based audit |
| `domain/agent/AgentPromptBuilder.java` | Import fixed to `shared.PlaceholderUtils` |
| `domain/agent/AgentSectionParser.java` | SLF4J → `java.util.logging` |
| `domain/agent/AgentFrontmatterMapper.java` | **Redesigned** — accepts `(Map<String,String>, String, String)` instead of `FrontmatterParser.Parsed` |
| `domain/agent/ReviewSystemPromptFormatter.java` | Pure package move |
| `domain/agent/ReviewTargetInstructionResolver.java` | SDK types removed; source content pre-computed by app layer |
| `domain/agent/RubberDuckPromptBuilder.java` | Template strings as parameters (app layer loads via `LoadTemplatePort`) |
| `domain/report/ReviewResultFactory.java` | Imports fixed to `domain.agent.AgentConfig`, `domain.report.ReviewResult` |
| `domain/review/ReviewContext.java` | **NEW** pure domain record replacing SDK-tainted `agent.ReviewContext` |

### T005 — Application Layer Files (11/11)

| File | Notes |
|------|-------|
| `application/review/OrchestratorConfig.java` | Plain values, no SDK types, no DI |
| `application/review/OrchestratorMetrics.java` | `java.util.logging`; `domain.report.ReviewResult` |
| `application/review/ExecutorResources.java` | `shared.ExecutorUtils` |
| `application/review/ReviewResultPipeline.java` | `java.util.logging`; `domain.report.ReviewResult` |
| `application/review/LocalSourcePrecomputer.java` | `CollectLocalSourcePort` replaces `LocalSourceCollectorFactory` |
| `application/review/ReviewRetryExecutor.java` | `domain.resilience.SharedCircuitBreaker`, `shared.RetryExecutor` |
| `application/review/ReviewPassRunner.java` | **NEW** — replaces `AgentReviewer`/`ReviewAgent.review()` pattern |
| `application/review/RubberDuckDialogueRunner.java` | **NEW** — uses `RunRubberDuckSessionPort` + `LoadTemplatePort` |
| `application/review/ReviewExecutionModeRunner.java` | `StructuredConcurrencyUtils` from `shared.*`; `OrchestratorConfig` replaces `ExecutionConfig` |
| `application/review/AgentReviewExecutor.java` | `ReviewPassRunner` + `RubberDuckDialogueRunner` replace `AgentReviewerFactory` |
| `application/review/ReviewOrchestrator.java` | **Implements `RunReviewPort`**; constructor takes all ports + config; no DI annotations |

---

## Key Architecture Decisions

1. **`ReviewTargetInstructionResolver` stays in `domain.agent`** — source content is pre-computed by
   the application layer (via `LocalSourcePrecomputer`) and passed as a plain `String`. The domain
   class does not call `CollectLocalSourcePort`.

2. **`RubberDuckPromptBuilder` stays in `domain.agent`** — template strings are method parameters;
   the application layer loads them via `LoadTemplatePort`.

3. **`domain.review.ReviewContext` is new** — no SDK types; contains only pure domain fields
   (`invocationTimestamp`, `reasoningEffort`, `outputConstraints`, `cachedSourceContent`,
   `sharedSessionEnabled`, `maxRetries`, `reviewCircuitBreaker`).

4. **`AgentFrontmatterMapper` redesigned** — accepts `(Map<String,String> metadata, String body,
   String filename)` instead of `FrontmatterParser.Parsed`; the infrastructure layer parses YAML
   and passes the raw map.

5. **`ReviewOrchestrator` builds collaborators per invocation** — each `execute()` call creates its
   own `ExecutorResources`, `AgentReviewExecutor`, etc., scoped to that invocation. No `AutoCloseable`
   needed; `ExecutorResources.shutdownGracefully()` is called in a `finally` block.

6. **MCP servers default to `List.of()`** — the infrastructure port implementation is responsible
   for appending MCP server specs based on agent config flags.

7. **`RunReviewPort.execute()` returns a single aggregated `ReviewResult`** — content from all
   agents is joined with `---` dividers; `success = anySuccess` across agents.

---

## Test Results

- Command: `JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open ./mvnw -B clean verify`
- Passed: **907**
- Failed: **0**
- Skipped: 0
- Duration: ~36s

---

## Upstream Artifacts Consumed

- `t4-architect-packages.md` — target package structure and import rules
- `t4-architect-ports.md` — port interface contracts (constructor params for `ReviewOrchestrator`)
- `t4-architect-classmap.md` — authoritative file → package mapping
- `t5-teamlead-tasks.md` — T004 + T005 acceptance criteria
- `t8-backend.md` — Phase 1 port interfaces and domain types already created

## Evidence Mapping

- `t4-architect-ports.md#RunReviewPort` → `ReviewOrchestrator implements RunReviewPort`
- `t4-architect-ports.md#ManageCopilotClientPort` → `ReviewOrchestrator.execute()` calls `start()`/`stop()`
- `t4-architect-ports.md#CollectLocalSourcePort` → `LocalSourcePrecomputer` constructor
- `t4-architect-classmap.md#ReviewContext→domain.review` → `domain/review/ReviewContext.java`
- `t5-teamlead-tasks.md#T004-acceptance` → all 10 domain files use only `java.*/shared.*/domain.*`
- `t5-teamlead-tasks.md#T005-acceptance` → all 11 application files use only `application.port.*/domain.*/shared.*/java.*`
- `t8-backend.md#Phase1-ports` → all 12 Phase 1 ports consumed in T005 classes
