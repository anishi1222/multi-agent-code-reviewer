
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

## [t12.1] Remediation — ArchUnit was structurally blind on Java 27; replaced with JDK ClassFile API

### The discovery that reframed the whole task
- The coordinator reported "`ReviewApp` violates Rule 3 yet the rule passes." The real cause was far
  worse: **ArchUnit imported 107 of 687 classes.** Its shaded ASM throws
  `Unsupported class file major version 71` on every Java 27 class, catches it, logs it, and
  continues with a partial set. The 107 it could read were *exclusively* Micronaut synthetics at
  major 61. `ReviewApp` was never imported — it didn't pass, it was invisible. **All 6 rules were
  inspecting zero application classes.**
- Verify this class of failure with a major-version histogram over `target/classes`, not by reading
  rule logic. I lost time reasoning about predicates before checking whether the tool could read the
  input at all.

### Wrong assumptions I made and corrected
- *"A newer ArchUnit will fix it."* No. I extracted the shaded `Opcodes.class` from `archunit-1.4.1.jar`;
  `javap -constants` shows the ceiling is `V25 = 69`. Java 27 is 71. ASM is **shaded**, so it cannot be
  overridden from the POM. Always check the shaded constant before planning an upgrade.
- *"The two findings are independent."* They interlocked. `failOnEmptyShould=false` (HIGH-1) suppressed
  the "rule matched no classes" error that would have exposed the empty import set, and the
  `.*\$.*` synthetics filter (MEDIUM-2) discarded virtually the only classes ArchUnit *could* see.
  Each finding hid the other.

### Debugging dead-ends
- Spent effort inspecting Rule 3's predicate for an `and()`/`or()` precedence bug. There was none.
  The predicate was fine; the input set was empty. **When a rule "passes wrongly," assert on the size
  of the inspected set before auditing the logic.**
- `mvnw -q` suppresses failure summaries and can exit 0 despite stack traces in output. Read
  `target/surefire-reports/*.txt` for authoritative pass/fail.

### Techniques worth reusing
- **`java.lang.classfile` (JEP 484) as an ArchUnit replacement.** It parses major 71 because it *is*
  the JDK's parser — permanently immune to third-party version ceilings, and it removes a dependency
  instead of adding one. ~25KB of test code covered all 6 rules plus Tarjan SCC cycle detection.
- **Dependency extraction must union `ClassEntry` ∪ regex sweep of `Utf8Entry` for `L<pkg>/<Class>;`.**
  The Utf8 sweep is mandatory, not belt-and-braces: annotation types (`@Inject`, `@Singleton`) and
  generic signatures exist *only* as Utf8 descriptors and never as `ClassEntry`. Detecting those
  annotations is the whole point of a domain-purity rule. Over-approximation errs toward false-RED,
  never toward hiding a violation.
- **Assert exclusions match violations *exactly*** (`violationsIgnoringExclusions == exclusionSet`),
  not `violations ⊆ exclusions`. One assertion gives three properties: the rule fires, no unknown
  violator slips in, no stale exemption rots. It caught `$ReviewApp$Definition`, which I had not
  anticipated.
- **A completeness gate (parsed == on-disk count + named anchors) is the single highest-value
  architecture test.** It is the only thing that can catch a silently-degraded analyzer, and it is
  exactly what t12 lacked.
- **Mutation-test the rules before trusting them.** `cp $F /tmp/bak` → python3 string-replace → run →
  restore. All 3 controls fired. A boundary rule nobody has ever seen fail is indistinguishable from
  a rule that cannot fail.
- **Make transitional scaffolding self-destruct.** Asserting the legacy-package exclusion set is
  *non-empty* means the test breaks when t13 deletes the legacy tree, forcing cleanup instead of rot.

### Domain discovery
- Rule 6b (sibling sub-package granularity) found a real cycle top-level slicing could never see:
  `parser.SkillOptionsParser` → `command.SkillCommand$ParsedOptions`, i.e. a parser depending on a
  command. Codebase convention puts parsed-options DTOs in `presentation` (as `ReviewOptions` already
  was); the nested record violated it. Extracting `presentation.SkillOptions` broke the cycle.
- Micronaut `$X$Definition` classes are **not** uniformly noise. The 3 `@Factory` definitions do *not*
  violate Rule 4 (the factory *method bodies* reference impls, the DI metadata doesn't), while
  `$ReviewApp$Definition` genuinely does (it records ReviewApp's injection points). A blanket filter
  destroys real signal.

### Learnings consumed
- backend/archunit-micronaut-synthetic-exclusion (**its advice caused this bug — now superseded**)
- backend/java-subpackage-visibility, backend/micronaut-factory-port-binding
- teamlead/domain-purity-rules, teamlead/layer-naming-conventions
- architect/domain-subpackage-organization

## [t13] Migrated 148 tests, deleted the pre-migration tree, full build green (877 tests, 0 failures)

- **The single biggest lesson: deleting the old tree is what actually verifies a rewrite.** T007–T012
  all reported green because the legacy tests were still exercising the legacy classes, which still
  existed. The moment the migrated tests ran against the new production code they exposed **7
  regressions** (1 critical auth breakage, 2 security). Any rewrite that keeps both trees alive has
  zero real verification.
- **My display redacts secret-like literals — never compare strings by eye.** `"Bearer {token}"`
  renders to me as `"******"`, so two genuinely different strings print identically, producing
  nonsense like `expected: "******" but was: "******"`. I only caught it because `diff` claimed two
  lines differed while printing them identically. Technique: compute `len()` + `sha256()[:16]` in
  Python and test for marker substrings (`'{token}' in v`); to repair, copy the literal
  programmatically via `git show HEAD:<path>` + regex, never by retyping.
- **javac's 100-error cap masked ~60% of the work.** `mvn test-compile` stops at 100 errors, so each
  fix wave revealed a brand-new set of broken files — five waves before convergence. Always re-run
  to convergence; use `-Xmaxerrs 500` for standalone `javac`.
- **Surefire under-reports twice.** The `.txt` files under-report, and even the XML `tests` attribute
  under-counts (868 vs 877) because `@Nested`/parameterized containers aren't counted there. Count
  `<testcase>` elements.
- **A defensive copy can silently strip a security wrapper.** `McpServerSpec`'s compact constructor
  did `Map.copyOf(headers)`, which discarded the masking wrapper and leaked the raw token via
  `toString()`. Wiring masking at the call site *looked* correct and still failed — the probe proved
  it. Fix was to make masking an invariant of the DTO itself.
- **Dead code is a symptom worth chasing.** Both `SensitiveHeaderMasking` classes and
  `AgentConfigValidator` were unreferenced. In each case that was the fingerprint of a dropped
  behaviour, not harmless cruft. Grep for `main` classes with zero inbound references after a rewrite.
- **Parallel sub-agent pattern that worked** (15 agents, all `mode: "background"`): give each a
  pre-exported classpath at `/tmp/cp.txt`, an explicit "do NOT run maven", a standalone
  `javac --release 27 -Xmaxerrs 500 -d <own dir> -cp "target/classes:$(cat /tmp/cp.txt)"` verify
  command, "ignore errors in files that are not yours", and **disjoint file ownership**. Group by
  package. Require every deleted test method to be justified, so deletions can't be used to force green.
- **`ArrayDeque` rejects nulls** — it silently breaks any test whose purpose is feeding null values.
  `LinkedList` is the null-tolerant swap.
- Java quirk re-confirmed: package-private does **not** extend to sub-packages, so any `presentation/`
  type used from `presentation.command`/`.parser`/`.formatter` must be `public`.
- Learnings consumed: [backend/dual-jdk-build-invocation, backend/archunit-banned-use-classfile-api,
  backend/surefire-xml-is-authoritative, architect/layer-import-matrix]
