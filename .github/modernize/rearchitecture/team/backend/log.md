
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

## [t15] CVE scan + remediation — 1 finding: a "security override" pinned to a still-vulnerable version
- **The finding.** Both POMs carried a deliberate override commented "from vulnerable 3.1.3 to 3.1.4" for `tools.jackson.core:jackson-databind`. The advisory range for CVE-2026-59889 is `[3.0.0, 3.1.5)` — 3.1.4 is *inside* it. The fix was real but landed one patch short. Bumped to 3.1.5 in both manifests.
- **Wrong assumption I nearly made:** that a clean scan of the resolved dependency tree means clean. It doesn't here. `tools.jackson.core:*` never resolves (`micronaut.runtime=none`), so the vulnerable coordinate is BOM-managed but invisible to tree-based scanning. Both scanners said clean — correctly and uselessly. The finding only appeared after scanning the **BOM-managed set + the targets named in override comments**.
- **Wrong assumption I actually caught:** that bumping `jackson.version` moves everything Jackson-3. It moves **7 of 64** coordinates; the other 57 are pinned directly by the Micronaut BOM. I verified `jackson-databind` was among the 7 that moved rather than assuming — if it hadn't been, the "fix" would have been cosmetic and I'd have reported success on nothing. Always diff the effective POM before/after and confirm the *specific vulnerable artifact* moved.
- **Non-vacuity controls are cheap and worth it.** Before trusting any "0 findings", I fed the scanner `logback-core:1.5.12` (→6) and `jackson-databind:2.13.0` (→9). A scanner that scanned nothing and a scanner that found nothing look identical otherwise. This is the same failure class as the t12 vacuous-ArchUnit incident.
- **`pom-native.xml` is broken at HEAD** — `clean compile` fails with a Micronaut annotation-processor error (`ElementBeanDefinitionBuilderFactory`). I proved it pre-existing by building `git show HEAD:pom-native.xml` unmodified → identical failure. Do this before accepting blame for a red build on a file you just touched; it took one command and changed the whole conclusion.
- **`micronaut.version=5.1.2` is dead config** in both POMs — the parent pins it (5.0.4/5.0.2, resolving to 5.0.5). Don't trust `<properties>` as a statement of what's actually in use; read the effective POM.
- **Tooling notes:** `appmod-cve-assessment` isn't registered in this runtime; `appmod-validate-cves-for-java` is the equivalent. `osv-scanner` binary isn't installed, but the OSV.dev REST API (`/v1/querybatch`) needs no auth and takes coordinates directly — reusable scanner left at `/tmp/osv_scan.py`.
- **Deliberately did NOT use a bytecode-level scanner** — `decisions.md` names t15 explicitly in the ASM/Java-27 silent-degradation constraint.
- Learnings consumed: [devops/dual-jdk-build-activation, devops/logback-version-bom-override, backend/deleting-legacy-tree-verifies-rewrite]

## [t13.1] Closed the arch-enforcement gap (Rule 5b) and restored correlation logging as a port
- **The gap that mattered most was invisible.** G2 wasn't "MDC broke" — it was "MDC was deleted and 877 tests still passed." Any capability with no test asserting it *exists* can be removed by a refactor without a single red light. The fix isn't just the port; it's `PropagateCorrelationPortWiringTest`, which resolves the bean from a real `ApplicationContext`. Unit tests only prove the executors use whatever port they're handed.
- **Wrong assumption corrected — the two `RetryPolicyUtils` copies were not duplicates.** I nearly deleted the infrastructure one as redundant. Diffing them first showed disjoint transient-marker lists, an `InterruptedException → false` guard present in only one, and a null-root-cause NPE in the other. "Consolidate duplicates" tasks must start with a semantic diff, not a file-count check. Merged the union + both guards; documented as a deliberate behaviour widening for the two former `shared` consumers.
- **`shared/ConfigDefaults` had zero importers** before this task — it was dead code shadowed by the infrastructure copy. Worth checking which of a duplicate pair is actually live before picking the survivor.
- **Rule numbering is load-bearing.** Named the new rule **5b**, not 7, so the dependency-direction rules stay contiguous (3, 4, 5, 5b) and Rule 6a/6b keep the numbers that `learnings/backend/sibling-package-cycle-granularity.md` references by name.
- **Negative controls are cheap and non-optional for arch rules.** A rule with `Set.of()` exemptions that has never been observed failing is indistinguishable from a rule with a broken predicate. Injecting `static final Class<?> ARCH_PROBE = MdcCorrelationAdapter.class;` into `presentation.CliOutput` proved Rule 5b names the offender; restore from a `/tmp` backup + `git diff` to confirm a clean revert.
- **Dead end:** first guess at the rubber-duck test lambda returned `List.of("round-1")` — `RunRubberDuckSessionPort` returns `List<DialogueRound>`, not `List<String>`. Reading the port interface would have been faster than inferring the shape from a neighbouring test helper.
- **Thread-identity assertions matter.** Each propagation test asserts the child thread name differs from the caller's; without it the test would pass vacuously if the work were ever inlined onto the calling thread.
- **Stale Javadoc is actively harmful.** Three classes carried comments stating MDC was *intentionally* removed. Left alone, the next agent would have read the regression as a decision and preserved it.
- Learnings consumed: [backend/archunit-java27-bytecode-ceiling, backend/self-cleaning-architecture-exclusions, backend/sibling-package-cycle-granularity, backend/micronaut-factory-port-binding, devops/dual-jdk-build-activation, teamlead/domain-purity-rules, teamlead/layer-naming-conventions]

## [t16.1] Narrowed Rule 4 to application.port.outbound, then fixed the two inversions it exposed
- **Narrowing a rule is itself a negative control.** Pointing Rule 4 at `application.port.outbound`
  made 11 violators appear where the ADR predicted 2. The extra 9 were not noise — they were the
  generated `$…$Definition` mirrors plus the already-exempt composition-root classes. Running the
  narrowing *before* the fixes is what made the defects fail mechanically instead of by review.
- **Wrong assumption I corrected mid-task:** I expected to hard-code the generated-mirror FQNs as
  exemptions. They are named by *method declaration index* (`$…$ExecuteSkillPort5$Definition`), so
  any inserted factory method renames them and the exemption list rots silently. Switched to
  deriving them (declaring class must already be exempt AND deps ⊆ source deps).
- **Dead end:** the obvious fix for `ResolveTokenPort` — move it to `application.port.outbound` —
  looks right until you check the *callers*. They are `presentation` classes, which may not reach
  outbound. Worse, no rule enforces presentation's allowlist, so it would have been an invisible
  violation. Always check both halves of a port-direction rule: implementer AND caller.
- **ADR premises are not evidence.** Deviation #4 named three "Micronaut factory classes";
  `grep -rln "@Factory"` returned exactly one. Verifying the premise found a *fifth* inversion
  (`ReviewOrchestratorFactory` implements inbound `RunReviewPort`) that nobody had recorded.
- **Static rules can't catch mis-selection.** `ExecuteSkillUseCase` was correct and simply not the
  bean being handed out. No reference was wrong, so no bytecode rule could see it. Needed a
  `@MicronautTest` asserting `isInstanceOf` on the resolved bean.
- **Surefire counts two different numbers.** Console = actual `<testcase>` elements; XML root
  `tests` attribute = declared. Three parameterized classes here differ by 9 total, which is the
  whole "928 vs 937" mystery from earlier tasks. Use the console figure.
- Learnings consumed: [architect/port-direction-by-implementer, backend/architecture-rule-negative-control, backend/self-cleaning-architecture-exclusions, architect/matrix-row-requires-enforcement-rule]

## [t23] Merged origin/main (36 commits) into the layered tree — 82 conflicts → 0, 939 tests green

- **The shape of the problem**: ours deleted the flat tree and rewrote it as layers; main kept the flat tree and
  added features to it. Nearly every conflict was therefore a *category error* rather than a line disagreement.
  The rule that made it tractable: **structure from ours, behaviour from main**. Without a stated rule up front
  I would have made inconsistent per-file calls.
- **`DU` is the dangerous category, not `UU`.** "Deleted by us, modified by them" resolves *cleanly* by keeping the
  deletion — and silently discards whatever main changed. There is no conflict marker to catch it. I diffstat-audited
  all 45 and found **3 features I had not catalogued from commit messages** (`--no-rubber-duck` resolver support,
  dynamic code fence, `ReviewFinding.summary`/`location`). Trusting the commit log would have lost all three.
- **Auto-merged files need auditing too — this surprised me most.** Git auto-merged `ReviewResultPipelineTest`
  (no conflict, nothing flagged) and dropped a test covering a capability we deliberately retained. Found *only* by
  diffing per-file test-annotation counts vs HEAD. **An auto-merge is not evidence of a safe merge.**
- **Auto-merge also broke layering twice** by inserting `infrastructure` imports into `domain`. Both compiled fine
  and would only have failed the architecture gate. Generalisable: when main adds a framework-bound config type that
  domain consumes, git will happily wire it across a layer boundary. Grep domain after every auto-merge.
- **Wrong assumption, corrected**: I initially treated "it compiles + arch test passes" as sufficient. It is not —
  templates load by *path* at runtime, so a dropped template edit throws no compile error and no test failure.
  `git diff MERGE_HEAD -- templates/ agents/ src/main/resources/` being empty is the only real proof.
- **Dead end: line-based conflict resolvers.** My all-ours script (drop everything between `=======` and `>>>>>>>`)
  produced syntactically broken Java in 3 files where main had hoisted a `@Test` out of a `@Nested` class — braces
  didn't balance, surfacing as a cascade of `';' expected`. **`git show :2:<path> > <path>` is the correct primitive**
  for whole-file "take ours". Line-based only works when both sides share block structure.
- **Dead end: BSD `sed`.** `\b` is unsupported and *silently* no-ops, so a rename looks successful and breaks later.
  A greedy capture also matched across a chained `.getFirst()` → `...(List.of(merged)).getFirst(, 1);`.
  Always `grep`-audit after a `sed` rename.
- **Worktree gotcha**: `cat .git/MERGE_HEAD` returns nothing in a worktree because `.git` is a *file*. I briefly
  thought the merge state was lost. Use `git rev-parse -q --verify MERGE_HEAD`.
- **Technique worth reusing**: reconcile test counts by diffing per-file `@Test` annotation counts between HEAD and
  the working tree. The annotation delta (−7) matched the executed delta exactly, which both validated the arithmetic
  and pinpointed the one file that had silently lost a test.
- **Judgement call**: declined to escalate for a task split. A merge index is shared mutable state; partitioning it
  across agents would destroy rename detection and produce inconsistent per-file calls.
- Learnings consumed: [backend/duplicate-utility-consolidation-semantic-drift, backend/layer-dependency-test-is-an-allowlist,
  backend/maven-test-count-reconciliation, backend/jdk-pinning-for-preview-features]

## [t26] F1 negative control for the cumulative assigned-skill budget + ruling on one-knob-many-budgets

- **Verify the brief, don't inherit it.** The brief said one knob governed *three* budgets; enumerating
  the comparison sites myself found **five**. The two extra sites were where the real defect lived
  (`AgentPromptBuilder:145` reads the hardcoded constant, not the configured value → raising the knob
  to silence a warning turns a graceful skip into an `IllegalStateException`). Proposed as F4.
- **Prove branch reachability algebraically before writing the test.** The cumulative budget at L207 is
  *unreachable with a single skill*: `isSafeSkill` already enforces `skillLength + 2 <= budget`, so
  `skillLength > budget` cannot hold on the first iteration. That's why the pre-existing
  `rejectsOversizedSkillFile` never touched it. Needed ≥2 assigned skills.
- **Order sensitivity is the airtight isolation trick.** Per-file gates are pure functions of
  `(file, budget)`, so a byte-identical file at an identical budget cannot behave differently. Showing
  the same file survives alone but is dropped when preceded by others attributes the drop to the
  cumulative branch with no hand-waving.
- **Passing tests ≠ D7 satisfied.** Ran two mutations and built a kill matrix. M1 (cumulative branch off)
  killed tests 1+2; M2 (`metadata.agent` guard off) killed test 3. **Disjoint** kills prove no test is
  vacuous — test 3 passes under M1 by design, which would have looked like a weak test without the matrix.
- **Measure before calling a bug active.** I nearly wrote "the byte gate is ~3× stricter for Japanese".
  Measuring the real corpus: 27 skills have `bytes != chars` (up to 2×), but **zero** currently fall in
  the mis-gated window. It's *latent*, not active. Also nearly mis-stated the prompt overhead as 11
  chars/skill; computing it from the literals gave **10** (`"\n### "` is 5 chars, not 6). Compute, don't count by eye.
- **Split the deliverable at the charter boundary.** Ruling was "no, one knob may not govern these" —
  but the fix divides cleanly: renaming the alias into three unit-bearing fields is behaviour-identical
  and in-charter; new YAML keys are a user-facing contract change needing an ADR, so escalated. Avoided
  touching the `SkillConfig` record (7 call sites + new config surface needing its own D7 controls).
- **Don't test a defect into permanence.** Deliberately did *not* write a test asserting the F4
  `IllegalStateException` — that would ratify the bug as expected behaviour.
- A real shipped skill (`java-add-graalvm-native-image-support`, 12 908 bytes) is already being dropped
  at the default budget on every test run — so the F4 remediation path is one users will actually walk.
- Build: 942 tests (939 baseline, +3 = exactly the tests added), 0 failures, exit 0, 15/15 arch rules.
- Learnings consumed: [tester/negative-control-inside-the-test, tester/test-conventions,
  tester/never-pipe-a-verification-build, backend/surefire-declared-vs-actual-test-counts]

### [t26 addendum] clarification.md added to dependencies post-delivery

- **Reconciled all 7 applicable constraints; no deliverable needed revision.** The record
  *corroborated* ruling B rather than changing it: its "`application.yml` keys may break only
  when justified ... with ADR and migration notes" clause independently mandates the exact
  ADR escalation I'd chosen on charter grounds. Worth mining a late dependency for support of
  decisions already made, not just for contradictions.
- **Found one real discrepancy: record says Java 26, pom says 28.** Resisted both reflexes —
  "canonical record wins, downgrade the pom" (would be a genuine regression *and* violate the
  record's own no-version-upgrades rule) and "record's wrong, ignore it" (hides a process defect).
- **Provenance check resolved it in 3 commands.** merge-base=27, origin/main=28, and the 27→28
  commit `98b095c` is authored by the repo owner and is an ancestor of origin/main. So the drift
  came through Upstream Merge legitimately — **no worker violated scope** — and the record, generated
  2026-08-05, simply predates the merge. Reported as F5 (MEDIUM, process); did **not** hand-edit
  the file since it declares itself regenerate-only.
- **This retroactively validated the t26 build.** I'd built under JDK 28 matching `<release>28</release>`;
  had I trusted the "Java 26" line I'd have failed the build. Verify claims against build files.
- No source changed in this addendum (docs/learnings only), so the 942/0 build result stands unchanged.
- Learnings consumed: [same set as t26, plus reconciliation of clarification.md]

## [t29] F4 closed — skill budget injected into AgentPromptBuilder as a pure value, drop-and-warn replaces throw

- **Seam picked by measurement.** Before choosing where to thread the budget I scanned
  `new AgentConfig(` arities: 70/71 call sites use the 8-arg convenience ctor, the only wide call is
  inside `Builder.build()`. So a 13th record component + null-normalisation broke **zero** call
  sites. The "obvious" fix (add a parameter to `AgentPromptBuilder`'s static methods) would have
  broken ~18 call sites and forced `ReviewTargetInstructionResolver` and `ReviewPassRunner` to carry
  a budget neither uses. **Count call sites before arguing about design.**
- **Gotcha: `SkillConfig.defaults().directory()` points at the *real* project skills dir.** A loader
  test that doesn't override it silently loads real global skills from disk, so
  `assertThat(agent.skills()).isEmpty()` fails for reasons unrelated to the test. Cost one red run.
  Always point loader fixtures at an isolated temp dir.
- **Gotcha: `applySkills` early-returns** (`if (agentSkills.isEmpty()) return config;`). Attaching
  the budget inside it would silently skip skill-less agents. Attached in `parseAgent` instead.
  Related subtlety: `enforceAssignedSkillBudget` *passes through* skills whose `metadata.agent`
  doesn't match, so global skills count toward non-emptiness — the early return fires only when the
  skills dir yields literally zero skills.
- **A mutant that kills too many tests is a defect signal, not a win.** M9 (move the attach point
  past the early return) killed all three loader tests. That looked strong; it actually meant all
  three fixtures took the *same* path and nothing covered the with-skills path. After
  parameterising skill-presence, M9 kills exactly one test and its inverse M10 kills the other two —
  complementary sets, which is the result that actually proves both paths are guarded.
- **Predicting kill sets beforehand paid off.** I predicted algebraically that M2 (re-introduce F4:
  ignore the injected budget, hardcode 10_000) would *survive* `dropsOversizedExpandedSkillInsteadOfThrowing`,
  because 11,100 > 10,000 under both fixed and mutated code. Confirmed. That proves the intuitive
  "drops instead of throws" test verifies only half the remedy and says nothing about configurability.
- **Byte-identity pinned with a literal golden string**, written out in full rather than rebuilt from
  production constants — so header drift fails the test instead of silently tracking it.
  `ASSIGNED_SKILLS_HEADER` measures 71 chars (computed, not assumed).
- **Concurrent worktree co-tenancy.** `RubberDuckPromptBuilderTest` and
  `ReviewOverallSummaryAppenderTest` were modified inside my task window by another agent working in
  the same worktree. I left them alone and raised it to the coordinator. Practical consequence: the
  962-test total is not solely attributable to t29 (+15 is mine). If you see unexpected `git status`
  entries, check mtimes against your own start time before assuming your tooling did it.
- Build: `clean verify` → 962 tests, 0 failures/errors/skipped, `LayerDependencyRulesTest` 10/10, 0 cycles.
- Learnings consumed: [backend/one-knob-many-budgets-erases-provenance, backend/domain-purification-patterns,
  backend/architecture-rule-negative-control, backend/verify-clarification-against-the-repo,
  backend/merging-upstream-into-restructured-tree, architect/pre-check-predicting-downstream-is-duplicated-invariant,
  architect/purity-displaced-capabilities-become-ports]

## [t27] F2 — defaults single-sourced; the prescribed remedy was unsafe and the finding undercounted the sources
- The prescribed fix (delete `@Bindable(defaultValue=…)`, let unbound `int`s arrive as `0`)
  does not work on Micronaut 5.1.2. An absent key for a primitive record component throws
  `DependencyInjectionException` during parameter resolution — the compact constructor never
  runs, so it cannot rescue the value. Probed it before touching production code, which is the
  only reason this didn't ship as a startup crash.
- Working shape: box the components, mark them `@Nullable`, normalise `null` in the compact
  constructor. Absent → `null` → default. Already the in-tree idiom (`LocalFileConfig`,
  `ExecutionConfig.sharedSessionEnabled`, t29's `SkillBudget`).
- **The finding named two sources; there were three.** `application.yml` restated all eight
  values. Mutating one `@Bindable` literal to `424242` and watching the bean still bind `12000`
  proved the yaml won and the annotations were dead code. Deleting only the annotations would
  have been a fix that fixed nothing. Grep the key prefix; don't trust the finding's file list.
- Behaviour-neutral duplication is invisible to behavioural tests. Re-adding the yaml key with
  its *original* value (M4) — literally the state that shipped — changes nothing observable, so
  only a structural guard (scan the yaml, reflect over the annotation) rejects it. Two of the
  six mutants were of this kind.
- First draft of the reflection guard checked only `RecordComponent.getAnnotation`, and the
  mutant survived: `@Bindable` is retained on the **canonical constructor parameter**. A guard
  that never fails is indistinguishable from a broken one — the mutant run is what caught it.
- Self-inflicted: used `git checkout <file>` to revert mutants and wiped my own uncommitted
  implementation mid-run. The next mutant then "failed" in an unrelated test, which looked like
  a real kill. Restore from a `/tmp` copy, and always run a baseline through the harness first.
- Learnings consumed: [backend/one-knob-many-budgets-erases-provenance,
  backend/duplicate-utility-consolidation-semantic-drift,
  backend/injecting-config-into-pure-domain-as-values,
  backend/architecture-rule-negative-control, backend/merging-upstream-into-restructured-tree,
  architect/inherited-defect-is-not-a-merge-finding]

## [t28] F3 — banner and executor read different config keys; fixed via a new inbound port
- **The finding was mis-shaped as a typo.** t24 described F3 as "reads the wrong key". Correcting
  the string would have passed every existing test and left the actual defect — a `presentation`
  class naming an `infrastructure` config key by string — fully intact. The architect was right to
  insist on the port. Worth remembering: when a finding says "wrong key/name/path", ask *what made
  the wrongness invisible* before fixing the spelling.
- **`@Value` in `presentation` is an ADR-0006 D1 blind spot.** Rule 5b (`presentation ⊥
  infrastructure`) inspects **imports**. A `@Value("${some.infra.key}")` is the same coupling with
  none of the compile-time safety, and no static rule sees it. `presentation/ReviewModelConfigResolver`
  still does this. Flagged to architect; deliberately did not add a rule myself.
- **The obvious test would have been worthless.** "set property to 3, assert banner prints 3" passes
  identically against the broken code — the broken code also printed what *its* key said. The only
  test with any power sets the two keys to **contradictory** values and compares the port's answer
  against `ReviewOrchestratorFactory#buildConfig(...)`, i.e. the executor's own derivation. Compare
  two independently-derived values, never a value against a literal.
- **Mutation-verify any test written as a regression control.** I reintroduced the defect in the
  isolated copy and confirmed red (`expected: 3 but was: 7`), then restored and confirmed green.
  Cheap, and it is the only actual evidence that a "regression test" regresses.
- **Bind a method reference, not a snapshotted value.** `new DescribeReviewPlanUseCase(executionConfig::reviewPasses)`
  makes the wiring itself read as "the same accessor the executor uses". Passing an `int` would be
  runtime-identical but reintroduces a *second independent read* — the exact shape of the bug.
- **Normalisation belongs to one owner (ADR-0006 D6).** `ReviewPlan` **throws** on `< 1` rather than
  clamping. `ExecutionConfig` already normalises; a second clamp would silently mask that owner
  breaking. Parameterized cases `-4`/`0` prove the port inherits normalisation instead of copying it.
- **Banner prints before `ReviewRequest` exists** (`ReviewCommand` L124 vs L128) — that is why the
  value cannot ride on `ReviewRequest` without reordering the command. Checked this before designing.
- **Grep for call sites *before* changing a public signature.** I changed `ReviewOutputFormatter`'s
  ctor, then discovered 3 more test files mid-build. `grep -rn "new Type(\|methodName("` up front
  would have cost 10 seconds and saved a failed build cycle.
- **Shared-worktree builds:** `rsync --exclude target/ --exclude .git/ --exclude logs/` to `/tmp`,
  and afterwards `diff -r src /tmp/<iso>/src` to prove the verified tree is the worktree. The stale
  first sync cost me a wasted 5-error build.
- Result: 990 tests, 0 failures (baseline 980, +10). `LayerDependencyRulesTest` needed no edits.
- Learnings consumed: [backend/derived-exemptions-for-generated-beans, backend/micronaut-factory-port-binding]

## [t18.2] Closed SEC-H1/SEC-H2 at the root — ADR-0007 D1–D4

SEC-H1 and SEC-H2 turned out to be one defect: `ApplicationPortFactory` concatenated
operator-supplied and repository-supplied directories into one `List<Path>`, and provenance died
there. Downstream there was only "a path", so only one limit could apply (SEC-H2) and the strict
constants had nothing to attach to (SEC-H1). Neither is fixable without D1 first — the
coordinator's D1→D2→D3 ordering is a correctness constraint, not a preference.

### Gotchas that cost real time

- **`surefire:test` does not recompile tests.** `./mvnw -B -o surefire:test` silently runs stale
  `.class` files. I burned a long stretch chasing a phantom "fixture bug" (`Missing required agent
  fields: instruction` at 8193 chars) that did not exist — the source edit simply had not been
  compiled. Always `test-compile surefire:test` when iterating. A standalone probe I wrote to
  investigate then failed with `NoClassDefFoundError: org/slf4j/LoggerFactory`, a classpath error
  in the probe, which I briefly misread as a second symptom. There is no size limit in the parser.
- **The inbox test baseline (981) was stale.** Other tasks landed 9 tests in this shared worktree
  since it was measured. I re-measured HEAD with `git archive HEAD | tar -x -C /tmp/...` — read-only,
  never touches the shared worktree — and got 990. Delta is then +51, reconciling exactly against
  the new files. Reporting "+60" against the stale figure would have implied tests I never wrote.

### Things the new tests caught that I would otherwise have shipped

- **I recreated SEC-H1 while fixing it.** After moving the constants to `AgentDefinitionPolicy`, I
  wrote the profiles with literals (`16 * 1024`). Numerically right; the constants still had zero
  references. `AgentPolicyConstantsAreLiveTest` failed 6/8 and named each one. Note this test *must*
  scan source text — these are JLS §4.12.4 constant variables, inlined by `javac`, so a bytecode
  scan sees no `Fieldref` and reports a live constant as dead-free. Bytecode analysis cannot detect
  this class of defect at all.
- **The charset whitelist admitted the characters it existed to exclude.** `ALLOWED_CHAR_RANGE`
  whitelisted `\u2000-\u206F` wholesale — which contains the bidi overrides (`U+202A`–`U+202E`) and
  zero-width/invisible characters. A routine boundary test failed on first run. Activating dead code
  forces you to ask what it actually does; worth checking other block-range whitelists.
- **A record field name broke the architecture test.** `Rule 1` reported a dependency on `ines` — not
  a class. `javac` emits the record's component names as a `;`-joined string constant, and the
  descriptor regex's optional package separator matched `Lines;` inside `maxInstructionLines;`. The
  trigger is positional (a sibling record has the same hazard in its *last* component and escapes),
  so a field reorder could break the build spontaneously. Fixed at the detector, verified detection
  power via the exact-equality exemption lists, added a default-package guard. Renaming my field
  would also have gone green while leaving the trap armed.

### Technique worth reusing

Mutation verification per ADR-0007 D7 is cheap and repeatedly informative: back up to `/tmp` with
md5s (**never** `git checkout` in a shared worktree), reintroduce the exact defect, confirm the
named test fails, restore, re-verify the checksum. Four mutations, four confirmed reds. Mutation C
(making both trust profiles identical) reproduces SEC-H2 verbatim and fails 3/3 differential tests —
that is the strongest single piece of evidence in the artifact.

Also: writing the D1 differential test first and *watching it go red* was what proved the limits are
genuinely provenance-aware. Without the captured red, a passing test after the fact is compatible
with a validator that ignores provenance entirely.

- Learnings consumed: [backend/mutation-verify-regression-tests, backend/never-git-checkout-to-revert-a-mutant, backend/one-knob-many-budgets-erases-provenance, backend/surefire-declared-vs-actual-test-counts, backend/architecture-rule-negative-control, backend/archunit-java27-bytecode-ceiling, backend/self-cleaning-architecture-exclusions, backend/derived-exemptions-for-generated-beans, backend/java-subpackage-visibility, backend/domain-purification-patterns, backend/verify-clarification-against-the-repo]

## [t18.3] SEC-H3 closed by subtracting Unicode categories from the charset allowlist, proven by exhaustive sweep

- **The block-range trap generalises.** t18.2/F1 hand-narrowed `\u2000-\u206F` and got it
  exactly right; the identical defect sat untouched in three other ranges of the same constant.
  Correct hand-curation still does not generalise. Subtracting a *property* fixed all 15 ranges
  at once.
- **Derivation beat recall, measurably.** Security recommended excluding 5 blank-rendering
  fillers. Sweeping the BMP against `Character.getName()` found **6** — `U+A8F9 DEVANAGARI GAP
  FILLER` was on nobody's list. Never hand-write a set you can derive from JDK data.
- **The coordinator's `Mn` count was low.** Reported 4 (U+302A–302D); actual is **6** — U+3099
  and U+309A (combining dakuten/handakuten) come in via `\u3040-\u309F`, a different range than
  the one audited. Re-derive counts rather than inheriting them.
- **Order the exemption before the mask.** `\t \n \r` are category `Cc` and are in the allowlist
  deliberately. `PERMITTED_CONTROL_CHARACTERS` has to be checked *before* `BLOCKED_CATEGORIES`
  or every multi-line definition breaks. Cost me nothing here only because a test covered it.
- **The over-block mutant is the one that taught me something.** M1 (remove the control) killed
  the 4 rejection pins as expected. M4 (add `SPACE_SEPARATOR` to the blocked set) killed 15
  tests including `japaneseIsAllowed` — proving the *acceptance* side is pinned and the rule
  cannot drift too strict. A removal-only mutation matrix would have missed that entirely.
- **Turn a tradeoff debate into a number.** Blocking all `Mn` rejects NFD kana and Ainu セ+U+309A,
  which sounded expensive. Scanning 1,332 repo files for "admitted before, rejected after" gave
  **0**, which settled it. Wrote `/tmp/t18_3/RepoImpact2.java` calling the *shipped* class, and
  had to correct a first version that forgot to intersect with "currently admitted" — it flagged
  U+FE0F in 240 files, which was already rejected. Always diff against current behaviour, not
  against the new rule alone.
- **Staging the RED honestly.** Added the constants as inert data first and left the predicate
  unwired. That made the sweep and `subtractionIsNotANoOp` fail while the derivation test passed
  — a much more informative RED than a compile error, and it doubled as mutation M1.
- **Sweep cost is a non-issue.** 1.1M regex matches ≈ 80 ms; 1M `Character.getType` ≈ 5 ms.
  Exhaustive enumeration of a codepoint domain is cheap enough to run on every build.
- Learnings consumed: [security/charset-allowlist-block-ranges, backend/architecture-rule-negative-control,
  backend/mutation-verify-regression-tests, backend/never-git-checkout-to-revert-a-mutant,
  backend/self-cleaning-architecture-exclusions, security/dead-security-controls]

## [t16.3] Bound `RunReviewPort` to application and split review composition responsibilities

- RED-first controls exposed both aspects of ADR-0006 deviation #8: the Micronaut test resolved
  `RunReviewPort` to infrastructure (6 tests, 1 failure), and un-exempted Rule 4 named the
  infrastructure source plus generated bean definition (10 violators / 8 exemptions).
- The final binding lives in root-package `ReviewPortFactory`, a true layer-zero factory containing
  wiring only. `application.review.ReviewOrchestrator` is the resolved implementation and keeps no
  invocation state; it resolves settings and creates SDK-backed session ports per call.
- Split configuration mapping into outbound `ResolveReviewSettingsPort` implemented by
  `ReviewContextFactory`, and SDK construction into `CreateReviewSessionPortsPort` implemented by
  the historically named `ReviewOrchestratorFactory`.
- Corrected an over-broad first design before verification: a configuration DTO had echoed the
  GitHub token, timestamp, and output constraints through infrastructure. The final settings DTO
  excludes all three; the application combines external settings with request-owned values.
- Rule 4 now has 7/7 exact violators/exemptions, all from the still-open `ApplicationPortFactory`
  deviation. `ReviewContextFactory` and `ReviewOrchestratorFactory` need no exemptions.
- Java 28 full build: 1058 tests, 0 failures/errors/skipped, exit 0; focused architecture/review
  suite: 38/38; Rule 0 parsed 345/345 classes.
- Learnings consumed: [backend/inbound-port-implemented-in-infrastructure,
  backend/micronaut-factory-port-binding, backend/architecture-rule-negative-control,
  backend/derived-exemptions-for-generated-beans, backend/orchestrator-per-invocation-resources,
  architect/port-direction-by-implementer, architect/composition-root-as-layer-zero,
  architect/relocation-must-not-conceal-inversion]

## [t17.1] Added RED-first layer-zero and application-port dependency rules

- Codebase/domain discoveries: Rule 3a analyzes 339 compiled named-layer classes backed by 185 source primary types; Rule 4a analyzes 30 compiled port classes backed by 26 source primary types. Test-tree controls can exercise the same bytecode analyzer without entering the production graph rooted at `target/classes`.
- Wrong assumptions and corrections: a non-empty compiled subject set alone is not a sufficient vacuity guard because generated classes may keep it non-empty; the final rules independently enumerate production sources and require source-to-bytecode correspondence.
- Debugging dead-ends and what actually worked: the required `context.md`, checklist, and topology/fanout artifacts are absent. The supplied t17 report, ADR-0006, implementation task T012, current source, and isolated `/tmp` mutants provided the authoritative contract and proof.
- Techniques/patterns worth reusing for future tasks: preserve published rule numbers with suffixes, use exact-shape test-only mutation fixtures, require zero exemptions, and run production mutants only in a disposable repository copy before a full restored `clean verify`.
- Learnings consumed: [backend/architecture-rule-negative-control, backend/java-classfile-parser-major-version, backend/layered-package-boundary-first, tester/java-architecture-test-fixtures]

## [t17.2] Thinned ReviewApp and split the remaining application-wide factory

- Codebase/domain discoveries: the old factory mixed five different seams (trust provenance,
  filesystem I/O, framework configuration, SDK construction, and inbound use-case binding);
  moving it wholesale would have hidden rather than resolved deviation #4.
- Wrong assumptions and corrections: Micronaut generated-definition names did not need preserving;
  they are implementation details tied to factory method indices. Preserving actual port bindings
  through a real container test is the durable contract.
- Debugging dead-ends and what actually worked: a first settings-adapter test assumed bean
  constructor/accessor order from memory and failed test compilation. Reading the concrete records
  and adapter signature fixed the test without changing production.
- Techniques/patterns worth reusing: keep the stable main FQN as a tiny process shell, route
  presentation-triggered technical changes through inbound→use-case→outbound ports, split adapter
  factories by external mechanism, and delete generated exemptions once the source exception is
  gone.
- Verification: Java 28 full clean verify passed 1073 Surefire + 4 Failsafe testcases; Rule 0 parsed
  364/364 and Rule 4 inspected 122 classes with 0 violators / 0 exemptions.
- Learnings consumed: [backend/review-composition-root-split,
  backend/derived-exemptions-for-generated-beans,
  backend/source-backed-architecture-rule-subjects,
  architect/layer-zero-needs-two-controls]

## [t32.2] Migrated presentation defaults through ReviewPlan and enforced ADR/Rule 5c traceability

- Codebase/domain discoveries: three presentation classes owned direct Micronaut bindings even
  though the canonical values were already normalized by `ExecutionConfig` and `ModelConfig`.
  Expanding the existing plan port let parallelism and stage-model defaults cross the boundary as
  one coherent application DTO while preserving explicit CLI precedence.
- Wrong assumptions and corrections: the outbound accessor is named `defaultParallelism`, not
  `maxParallelism`; the first focused compile RED exposed the mismatch and the implementation was
  aligned to the architect contract rather than changing the test.
- Debugging dead-ends and what actually worked: mutation proofs ran in three disposable copies
  under `/tmp`, avoiding checkout/restore against the shared dirty worktree. Rule 5c killed a real
  `ReviewModelConfigResolver -> @Value` mutant, and the ADR guard independently killed primary-rule
  and D-item renames while the Rule 4b control remained unchanged.
- Techniques/patterns worth reusing: source-enumerate every zero-violation structural subject,
  retain an exact test-tree detector fixture, inventory only primary `@Test` display names, and pin
  real ADR anchors so both sides of a bidirectional guard are non-vacuous.
- Verification: focused 62/62; Java 28 full clean verify 1082 Surefire + 4 Failsafe, 0 failures,
  errors, or skips; Rule 5c inspected 72 classes backed by 31 primary sources with 0 violations and
  0 exemptions.
- Learnings consumed: [backend/route-config-to-presentation-through-an-inbound-port,
  backend/source-backed-architecture-rule-subjects,
  backend/never-git-checkout-to-revert-a-mutant,
  backend/architecture-rule-negative-control]

## [t32.3] Killed the duplicate agent-load summary mutant with exact cardinality assertions

- Codebase/domain discoveries: production already had one post-scan `reportOutcome(...)` call; the
  defect was exclusively in the D4 test oracle, whose `findFirst()` proved existence but discarded
  duplicate events.
- Wrong assumptions and corrections: making the default-level visibility test another cardinality
  assertion caused the mutant to over-kill 3 tests. Keeping that test on observability and assigning
  exact size-one checks only to rejection and zero-rejection behavior produced the intended 2-test
  kill set.
- Debugging dead-ends and what actually worked: copied the complete dirty worktree to
  `/tmp/t32_3_mutant`, added a second production summary invocation only there, and copied in the
  final test source. Both cardinality branches failed with size 2 while the other 3 D4 tests passed.
- Techniques/patterns worth reusing: collect the complete filtered event set before asserting
  cardinality; keep existence, severity, continuation, and cardinality assertions independently
  attributable; mutation-test in an isolated copy.
- Verification: focused 8/8; duplicate mutant RED 2/5; Java 28 full `clean verify` 1082 Surefire +
  4 Failsafe, 0 failures/errors/skips.
- Learnings consumed: [architect/event-cardinality-needs-duplicate-mutant,
  backend/mutation-verify-regression-tests, backend/never-git-checkout-to-revert-a-mutant,
  backend/surefire-declared-vs-actual-test-counts, backend/architecture-rule-negative-control]

## [t14.2] Restored skill resilience and deprecated-token warning

- Codebase/domain discovery: the deleted legacy `SkillExecutor`, not the current outbound
  session adapter, had owned the complete skill retry/circuit/timeout policy. The shared
  retry mechanism and skill-domain breaker survived the rewrite but had no live caller.
- Wrong assumption corrected: a session port throwing a timeout does not preserve
  `SKL-07`; the application boundary must translate that technical cause into the
  skill-specific result contract and decide whether it is retryable.
- Debugging dead-end: `origin/main` no longer contained the flat `SkillService` path.
  Locating the last add/modify commit recovered the source policy constants and prevented
  inventing new retry behavior.
- Reusable technique: use the tester's original red run as the negative control, edit
  production only, and rerun the exact same classes before the full build. This produced
  2 pass / 5 fail before and 7 pass / 0 fail after, without touching test source.
- Verification: Java 28 full `clean verify` passed 1,106 Surefire and 4 Failsafe cases;
  Rule 0 parsed 365/365 and Rule 5 found zero application-to-adapter violations.
- Learnings consumed: [backend/architecture-rule-negative-control,
  backend/mutation-verify-regression-tests, backend/one-knob-many-budgets-erases-provenance,
  backend/duplicate-utility-consolidation-semantic-drift,
  backend/redacted-literals-compare-by-hash,
  backend/surefire-declared-vs-actual-test-counts, tester/test-conventions]

## [t22.1] Restored default agent discovery and one executable discovered-skill catalog

- Codebase/domain discoveries: configured agent directories were already correctly owned and
  merged by `AgentDefinitionLoaderAdapter`; only the application short circuit made that behavior
  unreachable. `AgentConfigLoader` also already performed the authoritative global-skill scan, so
  a second registry scan would have created another source of truth.
- Wrong assumptions and corrections: an empty list from presentation means "no additional
  directories", not "load nothing". The use case now always delegates, while infrastructure
  interprets configured defaults.
- Debugging dead-ends and what actually worked: exposing the exact valid-skill list on
  `AgentLoadReport` let the adapter publish the same discovery pass. A new outbound catalog port
  removed the concrete `SkillRegistry` method-reference wiring from the application factory.
- Techniques/patterns worth reusing: publish complete immutable snapshots rather than append-only
  discovery results; atomically swap them so stale entries disappear without an observable partial
  refresh; isolate packaged regressions with named fixtures and explicitly reject empty-inventory
  output.
- Verification: RED-first 16 run / 3 expected failures; focused post-fix 36/36; Java 28 full
  `clean verify` 1,107 Surefire + 5 Failsafe, 0 failures/errors/skips; 22/22 architecture checks.
- Learnings consumed: [teamlead/default-discovery-smoke-controls,
  devops/packaged-cli-smoke-at-verify, backend/find-which-duplicate-actually-wins,
  backend/mutation-verify-regression-tests, backend/micronaut-factory-port-binding,
  backend/skill-resilience-use-case-boundary, tester/test-conventions]
