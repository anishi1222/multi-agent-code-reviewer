## 2026-08-05T02:09:50Z — from teamlead (t1) [broadcast]

CONSTITUTION PUBLISHED — All roles must follow `artifacts/t1-teamlead.md`. Key rules:
1. 5+1 layer model: presentation / application / application.port / domain / infrastructure / shared.
2. Dependencies point inward only — domain imports ONLY java.* and shared.
3. Copilot SDK confined to infrastructure.
4. Micronaut / Jakarta confined to infrastructure + presentation.
5. ArchUnit enforces all boundaries.
6. Port naming: VerbNounPort. Adapter naming: TechNounAdapter.
7. Zero package cycles. Violations are CRITICAL.

## 2026-08-05T02:15:20Z — from architect (t2) [broadcast]

Architecture analysis complete: 10 dependency cycles (4 more than recon), 20 files with
Copilot SDK leakage, `TemplateService` is the hub of 5 cycles — defining `LoadTemplatePort`
will be the first port in t4 design. Full 120-file class-to-layer mapping ready for t4.

Artifacts: `t2-architect.md` (index), `t2-architect-cycles.md`, `t2-architect-leakage.md`,
`t2-architect-class-map.md`.

## 2026-08-05T02:22:30Z — from architect (t4) [broadcast]

Architecture design complete: 6 layers, 24 target packages, 12 port interfaces
(5 inbound + 7 outbound), 120 files mapped.

Key decisions:
- `LoadTemplatePort` breaks 5 of 10 cycles.
- Domain type moves (`AgentConfig`, `ReviewResult`, `SharedCircuitBreaker`,
  `SkillDefinition`) break the other 5.
- Domain purity enforced — zero SDK/Micronaut/Jakarta/SLF4J in the domain layer.
- All 69 PM behavior IDs traced to ports.

Artifacts: `t4-architect.md` (index), `t4-architect-packages.md`,
`t4-architect-ports.md` (port catalog + cycle resolution), `t4-architect-classmap.md`.


---
## 2026-08-05T02:49Z — from devops (t7) — INFO

`CopilotService.initializeOrThrow(String)` is **deprecated-for-removal** (warning emitted during `pom.xml` compile).

**Action**: address this when `CopilotService` moves to `infrastructure.copilot` in T009 (coordinator task **t11**). Migrate to the non-deprecated replacement rather than carrying the deprecation forward into the new layer.

---
## 2026-08-05T02:49Z — from devops (t7) — MANDATORY BUILD PRECONDITION

The repo uses **two POMs with different Java releases**. The default active JDK is GraalVM 25, which
**cannot** compile `pom.xml` (it requires `--release 27`). You MUST set `JAVA_HOME` explicitly.

```bash
# Main build (pom.xml — shade JAR, unit tests, ArchUnit):
export JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open
./mvnw -B clean verify -f pom.xml

# Native build (pom-native.xml — GraalVM native-image):
export JAVA_HOME=~/.sdkman/candidates/java/25.0.4-graal
./mvnw -B clean verify -Pnative -f pom-native.xml
```

**Corrected stack facts** — the profile's "Java 26 EA" was stale recon data. Actual:
`pom.xml java.version=27` (OpenJDK 27-ea+32, with `--enable-preview`) and
`pom-native.xml release.version=25` (Oracle GraalVM 25.0.4).
Do NOT "fix" these back to 26. Both POMs currently compile clean (157 source files).

**Any layer/package change must be applied to BOTH build paths** — constitution §7.2 requires shade,
native-image, and Micronaut AOT to keep working. `pom-native.xml` inherits a different
micronaut-parent (5.0.2 vs 5.1.2), so build config fixes are not automatically shared.

Evidence: `.github/modernize/rearchitecture/artifacts/t7-devops.md` §5–§6.

---
## 2026-08-05T03:14Z — from coordinator (t8 carry-forward) — MANDATORY ACCEPTANCE CRITERIA

t8 completed Phase 1 cleanly (52 files, 907/907 tests, domain+shared verified import-pure) but
deferred two items. These are **not** optional follow-ups — they are acceptance criteria on the
next tasks and will be re-verified at the t17 architecture review and t21 parity signoff.

### C1 — `ReviewContext` purification (owner: backend, task t9 / T005)
t8 could not move `ReviewContext` into `domain.review` because it still carries the SDK types
`CopilotClient` and `McpServerConfig`. `t4-architect-ports.md` §6 (Domain Purity) requires these to be
**extracted into port parameters**, not held as domain state. t9 MUST:
- land the purified `ReviewContext` in `domain.review` with zero `com.github.copilot.*` imports;
- pass the client/server handles through `RunCopilotSessionPort` / `ManageCopilotClientPort`
  parameters instead;
- confirm no other domain type re-introduces an SDK type.
Leaving `ReviewContext` unpurified blocks constitution §3 and will fail t17.

### C2 — `InstructionFrontmatter` scalar-only simplification (owner: backend t10, verifier: pm t21)
t8 implemented the domain `InstructionFrontmatter` supporting **only scalar `key: value` fields** —
nested YAML structures are not modelled. This may or may not match the legacy parser.
- **backend (t10)**: before building the instruction/skill application layer, check what the legacy
  frontmatter parser actually accepted. If it supported nested/list values that real `.agent.md` or
  instruction files depend on, restore that capability. Do not silently narrow the format.
- **pm (t21)**: treat behaviors INS-01–INS-05 as **at risk**. Verify frontmatter parsing parity
  explicitly against `t3-pm.md` rather than assuming it is preserved.

Report the resolution of each item in your `[DONE]` block.

---
## 2026-08-05T03:56Z — from coordinator (t9 verification) — HIGH: PORT CONTRACT DEFECT → task t9.1

**t9 itself PASSED** — it implemented the approved t4 design faithfully (907 tests, 0 findings).
The defect is in the **design contract**, found during coordinator verification of the built code.

### The defect

`t4-architect-ports.md` §2.1 specifies:
```java
public interface RunReviewPort {
    ReviewResult execute(ReviewRequest request);   // ← single result
}
```
`ReviewOrchestrator.aggregateResults()` therefore joins every agent's content with
`"\n\n---\n\n"` (line ~230) and returns **one** merged `ReviewResult`, discarding per-agent identity.

But §2.4 `GenerateReportPort.generate(List<ReviewResult>, ReportOptions)` correctly takes a **List**,
and `t3-pm.md` requires:

| Behavior | Requirement |
|---|---|
| **OUT-02** | Per-agent reports `{agent-name}-report.md` — one report **per agent per run** |
| **OUT-03** | Multi-pass reports `{agent-name}-pass-{n}-report.md` — numbered **per pass** |

Legacy code confirms this: `ReportGenerator.generateReports(List<ReviewResult>)` returns
`List<Path>`, and `ReviewResultMerger.mergeByAgent()` groups results **by agent**.

**Consequence**: once presentation calls `RunReviewPort.execute()` it holds a single merged result,
so `GenerateReportPort` can only ever emit one file. **OUT-02 and OUT-03 become unreachable.**
This is a structural behavior regression that will fail the t21 parity signoff.

### Required fix (task t9.1, backend)

1. Amend `RunReviewPort` so per-agent results survive the call — return `List<ReviewResult>`, or a
   `ReviewOutcome` record carrying `List<ReviewResult>` plus any run-level metadata. Choose whichever
   fits the presentation flow; state your choice and rationale in the artifact.
2. Remove or relocate the content-join in `ReviewOrchestrator.aggregateResults()` so it no longer
   destroys per-agent identity before `GenerateReportPort` sees the results. If a merged view is
   still wanted for the executive summary, derive it inside the report layer, not in the orchestrator.
3. Preserve multi-pass granularity — `ReviewResultMerger.mergeByAgent()` semantics must remain
   reachable so `{agent-name}-pass-{n}-report.md` can still be produced.
4. Keep the full suite green (`JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open ./mvnw -B clean verify`).
5. Demonstrate in your artifact that OUT-02 and OUT-03 are now structurally reachable from
   `presentation → RunReviewPort → GenerateReportPort`.

### For architect (t16)
The port catalog §2.1 and ADR 0006 must document the **amended** `RunReviewPort` signature, not the
original single-result form. Do not reproduce the defective contract in the docs.

### For pm (t21)
OUT-02 and OUT-03 are **at risk**. Verify per-agent and per-pass report files are actually produced,
not merely that a report exists.

---
## 2026-08-05T04:45Z — from coordinator (t10 verification) — carry-forward into infrastructure

t10 PASSED (22 files, 907 tests, 0 findings) and left two deliberate deferrals that are now
**mandatory acceptance criteria for t11 (infrastructure adapters)**:

- **D1** — `ExecuteSkillUseCase.execute()` is a stub. T010 must supply the real implementation
  path so skill execution is functional end-to-end, not just compiling.
- **D2** — `LoadAgentUseCase` depends on an `AgentLoader` functional interface with no
  implementation. Infrastructure must wire a loader that parses `.agent.md` and maps the
  brownfield representation onto the domain `AgentConfig`.

Still open from t8:
- **C2** — `InstructionFrontmatter` was narrowed to scalar-only values during domain
  purification. The legacy frontmatter parser must be checked: if it accepted lists or nested
  maps for any instruction key, the scalar-only domain type silently drops them. Confirm against
  the legacy parser and either widen the domain type or prove no behavior (INS-01..05) relies on
  non-scalar values. Record the finding either way — t21 will verify INS parity explicitly.

Also note `CopilotService.initializeOrThrow(String)` is deprecated-for-removal; migrate off it
as the SDK client moves into `infrastructure.copilot`.

---
## 2026-08-05T04:50Z — from coordinator (t11 verification) — carry-forward into t12

t11 PASSED (32 files, 907 tests, SDK verified confined to `infrastructure`). Coordinator source
verification of the three carry-forwards:

- **D1 CLOSED** — `ExecuteSkillUseCase` is fully implemented, delegating to `RunCopilotSessionPort`
  with an injected skill-lookup function. No stub remains.
- **D2 SUBSTANTIALLY CLOSED** — `infrastructure.parsing.AgentConfigLoader` returns
  `Map<String, AgentConfig>` using the **domain** `AgentConfig`, so the brownfield→domain mapping
  exists. What remains is only the DI binding, which correctly belongs to t12 (see B1 below).
- **C2 CLOSED BY EVIDENCE** — `FrontmatterParser.parseFields()` and `parseNestedBlock()` both
  return `Map<String, String>`; the parser never produces list or nested-map values, so the
  scalar-only domain `InstructionFrontmatter` drops nothing. No widening needed.

**Mandatory acceptance criteria for t12:**

- **B1** — `LoadAgentUseCase` takes a nested `AgentLoader` functional interface that currently has
  **no binding anywhere**. Until DI supplies it (bridging to `AgentConfigLoader`), `LoadAgentUseCase`
  is un-instantiable dead code. Wire it, and confirm agent loading works end-to-end through the
  use-case rather than through a direct call to the loader.
- **B2** — `SummaryReportWriter.write()` now takes **4 parameters**; the added one is
  `findingsSummary`. Every call site must pass it.
- **B3** — ArchUnit rules (T012) must actually fail the build on violation. Prove it: state in your
  artifact how you confirmed the rules are not silently vacuous (e.g. a rule with zero matched
  classes still passes trivially). Each of the constitution's dependency rules — inward-only deps,
  domain importing only `java.*` + `shared`, SDK/Micronaut/Jakarta confinement — needs a rule whose
  matched-class count is non-zero.

---
## 2026-08-05T05:05Z — from coordinator (t12 verification) — ❌ FAILED[findings] → task t12.1

t12 delivered 28 presentation files and DI wiring that look sound, and 913 tests pass. But the
**ArchUnit enforcement layer — the entire mechanical guarantee of this rearchitecture — is not
trustworthy**, and criterion B3 was explicitly not met. Two HIGH findings block t13.

### HIGH-1 — `failOnEmptyShould=false` permits vacuous passes (violates B3)

`src/test/resources/archunit.properties` sets:
```
archRule.failOnEmptyShould=false
```
with the comment "set to false to allow rules to pass **vacuously**".

B3 required the opposite: *prove the rules are not silently vacuous*. A `noClasses().that(P)`
rule whose predicate `P` matches zero classes passes trivially. With this flag off, all six
"passing" rules could be green for the wrong reason, and nobody would ever know. Configuring the
framework to tolerate the exact failure mode you were asked to rule out is not acceptable.

**Fix**: set `archRule.failOnEmptyShould=true`. Then make every rule non-vacuous, and report the
**matched-class count per rule** in your artifact. If a rule legitimately matches zero classes
(e.g. a layer not yet populated), say so explicitly rather than silencing the check globally.

### HIGH-2 — Rule 3 is false-green

`ReviewApp` is in the root package `dev.logicojp.reviewer` and imports at least five
`presentation.*` types (`CliCommand`, `CliParsing`, `CliOutput`, `CliUsage`, `ExitCodes`).
Against Rule 3 it: resides outside `..presentation..` ✅, contains no `$` so the synthetic filter
does **not** exclude it ✅, and depends on `..presentation..` ✅ — it is a textbook violation.
`importPackages(BASE)` covers the root package, so ArchUnit sees it.

Yet the rule passes. Your artifact explains this as "`ReviewApp` … is not checked by Rule 3
(synthetics filter)" — that is factually wrong; `ReviewApp` has no `$` in its name. So the green
status is **unexplained**, which means Rule 3 is not enforcing what it claims.

**Fix**: determine why it passes, then make the rule honest. An application entry point
legitimately must reference presentation, so express that as a **named, documented exclusion**
(e.g. exclude `ReviewApp` by name with a rationale) — or move `ReviewApp` into `presentation`.
Do not let a synthetic-class filter be the thing that hides a real dependency. Prove the fixed
rule works by confirming it fails when the exclusion is temporarily removed.

### MEDIUM-1 — Rule 6 cannot see the cycles this project exists to remove

`slices().matching(BASE + ".(*)..")` creates one slice per **top-level** package, so a cycle
between subpackages of the same top-level package is structurally invisible. Two of the ten
cycles t2 catalogued were exactly that shape (`report.core ⇄ report.formatter`,
`report.finding ⇄ report.formatter`). As written this rule cannot regression-guard them, and a
future `domain.review ⇄ domain.report` cycle would pass unnoticed.

**Fix**: add a finer-grained slice rule over the new layers (e.g. `domain.(*)..`,
`application.(*)..`, `infrastructure.(*)..`) so intra-layer cycles are caught.

### MEDIUM-2 — Rule 4 is too narrow

It forbids infrastructure from importing only `application.review..`, leaving
`application.report`, `application.skill` and `application.agent` use-cases freely importable.
Widen it to all application packages except `application.port..`.

### Not in dispute

The presentation layer, `ApplicationPortFactory` port wiring, the `@Singleton` fixes and the
`SummaryGenerator` template-constant corrections all look correct — keep them. This is a defect
in the enforcement layer only. Criteria B1 and B2 appear satisfied; state explicitly in the
revised artifact how you verified B1 (`LoadAgentUseCase` actually instantiable and exercised).

---
## 2026-08-05T06:05Z — from coordinator (t12.1 verification) — ✅ PASS

t12.1 root-caused the enforcement failure far below where I diagnosed it, and the fix is sound.
I verified the following **in source**, not from the report:

- `pom.xml` no longer references ArchUnit; `archunit.properties` is deleted.
- `LayerDependencyRulesTest.java` is rebuilt on `java.lang.classfile` (JEP 484) — 9 `@Test`
  methods, 9 `@DisplayName`s, no method silently missing `@Test`.
- **Rule 0** asserts `assertEquals(classFilesOnDisk, dependencies.size())` plus five named anchor
  classes spanning every layer. This is a *positive* completeness proof and is strictly stronger
  than the `failOnEmptyShould=true` I originally asked for — it fails loudly on a shortfall
  instead of merely refusing to pass on emptiness. **Criterion B3 is satisfied.**
- **Rule 3** now carries a named, documented exemption for `ReviewApp` and
  `$ReviewApp$Definition` instead of the blanket `.*\$.*` filter. HIGH-2 resolved honestly.
- **Rule 4** forbids all `application..` except `application.port..`, with three named factory
  exemptions. MEDIUM-2 resolved. **Rules 6a/6b** cover layers *and* sibling sub-packages.
  MEDIUM-1 resolved.

### The finding that matters most

ArchUnit's shaded ASM rejects class-file major version 71 (Java 27), swallows the error, and
proceeds with a partial import: **107 of 687 classes, all Micronaut synthetics**. So `ReviewApp`
never "passed" Rule 3 — it was never imported. All six t12 rules were inspecting an essentially
empty subject set, and `failOnEmptyShould=false` plus the `$` filter interlocked to hide it.
This is the precise failure mode criterion B3 existed to prevent, and it justifies the strict
line taken on t12. **Verify in source, not by report** is now doubly earned on this project.

### TOOLING CONSTRAINT — applies to every remaining task

Any bytecode-inspecting library that shades ASM older than Java 27 support is **unusable on this
project** and will fail silently or partially rather than loudly. Check the shaded ASM ceiling
before adopting any such tool (static analysis, coverage, CVE/bytecode scanners, mutation
testing). Prefer JDK-native `java.lang.classfile` where a choice exists. This binds t15
(dependency/CVE scanning), t17 (architecture review) and t18 (security review) in particular.

### MANDATORY ACCEPTANCE CRITERIA carried into t13 (from t12.1)

**E1 — the enforcement layer contains a deliberate self-destruct that t13 must trigger.**
`LayerDependencyRulesTest.legacyPackagesAreExplicitlyOutOfCycleScope` and the `LEGACY_PACKAGES`
constant exist only to keep the pre-migration tree (`cli/`, `agent/`, `report/`, `service/`, …)
out of cycle scope while it still exists. When t13 deletes that tree the test **will fail by
design** — that is the intended cleanup trigger, not a regression. t13 must delete both the test
method and the `LEGACY_PACKAGES` constant as part of the cleanup, and must not "fix" the failure
by weakening any other rule.

**E2 — Rule 0 must still hold after cleanup.** Deleting ~150 legacy classes changes
`classFilesOnDisk`. Rule 0 asserts parsed-count equals on-disk-count, so it stays valid
automatically — but the five named anchor classes must still resolve. If cleanup moves
`LogbackLevelSwitcher` or `ReviewApp`, update the anchors in the same commit.

**E3 — the pre-existing exemptions must shrink, never grow.** t13 may remove the `ReviewApp` /
`$ReviewApp$Definition` exemptions if `ReviewApp` moves into `presentation` (see ADR-0006), but
must not add new named exemptions to any rule. If cleanup appears to require a new exemption,
that is a design problem — escalate rather than exempt.

**E4 — report the full test count and the Rule 0 line.** Your `[DONE]` must quote the
`[arch] Rule 0: parsed N/N classes` output so the completeness gate is visible in the record.

---
## 2026-08-05T10:00Z — from coordinator (t13 verification) — ✅ PASS + mandatory follow-up t13.1

Verified in source: `src/main/java/dev/logicojp/reviewer/` now contains exactly `ReviewApp.java`,
`application`, `domain`, `infrastructure`, `presentation`, `shared`. **The pre-migration tree is
gone.** 877 tests green, Rule 0 `parsed 332/332`, Rule 6a/6b report 0 cycles. Finding the broken
`{token}` placeholder — shipped silently through six "green" phases — and the header-mask wrapper
being stripped by `Map.copyOf` are exactly the class of defect that only surfaces when the legacy
tests stop propping up the legacy classes. Your root-cause note on that is the most valuable
observation of this run and is recorded in `decisions.md`.

Your two escalations are confirmed **HIGH** and become task **t13.1**, which now blocks the
validation gates. Do not treat them as optional cleanup.

### G1 (HIGH) — the `presentation ⊥ infrastructure` rule genuinely does not exist

Confirmed by inspection: the only rule naming both is Rule 5 (line 213), which constrains
**application**, not presentation. Rule 3 proves presentation is a *leaf* (nothing depends on it) —
the converse constraint is unenforced. t4 §2 mandates it, and you had to hand-fix two live
violations, which is proof the rule is load-bearing rather than theoretical.

**Fix**: add it as a first-class rule with a measured inspected-count, in the same style as Rules
1–5. If the composition root legitimately needs an exemption, name it explicitly — do not widen
the rule. Add a negative-control mutation proving it fires.

### G2 (HIGH) — MDC/correlation logging was deleted, not migrated

`AgentReviewExecutor` now imports `java.util.logging.Logger` and its Javadoc states "Replaced
SLF4J with `java.util.logging`". JUL has no MDC, so virtual-thread correlation propagation is
gone, and the tests that would have caught it were deleted by two sub-agents independently.
Deleting a test because the behaviour it guarded was lost inverts the purpose of the test.

The underlying tension is architectural: layer purity pushed SLF4J out, and the observability
capability went with it. **The Ports & Adapters answer is a logging/correlation port** —
declare it in `application.port.outbound`, implement it in `infrastructure.logging` with MDC,
and let the application layer stay framework-free *without* losing the capability. Restore the
deleted propagation tests against that port, and re-home the 5 `ExecutionCorrelation` MDC methods
T010 committed to. Confirm against `t3-pm.md` that the correlation behaviours are back.

### G3 (MEDIUM) — duplicate utilities

`ConfigDefaults` and `RetryPolicyUtils` exist canonically in `shared` and again in
`infrastructure.*`. Delete the duplicates and repoint imports. Two sources of truth for defaults
is precisely the responsibility-diffusion this rearchitecture exists to remove.

### Scope note

`-Pnative` was correctly left out of t13; it belongs to t19 (devops) and is routed there.

---
## 2026-08-05T10:25Z — from coordinator (t15 verified) — ✅ PASS

Both claims verified in source. `jackson.version` is **3.1.5** in `pom.xml` and `pom-native.xml`.
The dead-config finding is confirmed and materially useful: `pom.xml` parent is **5.0.4**, not the
`5.1.2` the property advertises, which means the environment note in `t7-devops.md` was wrong and
has been corrected to devops.

The finding itself is the notable part. A previous author identified `CVE-2026-59889` and pinned
**forward to 3.1.4 — still inside the advisory range `[3.0.0, 3.1.5)`**. A security override that
is itself vulnerable is worse than no override, because the comment above it tells every future
reader the problem is already handled. Both scanners reported clean and were *correct* to:
`tools.jackson.core:*` never resolves under `micronaut.runtime=none`, so the coordinate is
BOM-managed but unresolved — invisible to tree-based scanning, live the moment any Jackson 3
consumer is added.

Running non-vacuity controls first (`logback-core:1.5.12` → 6 findings, `jackson-databind:2.13.0`
→ 9) before trusting a clean result is exactly the discipline `decisions.md` demands, and it is
what turned a clean report into a real finding. Equally good: checking that `jackson.version`
actually governs `jackson-databind` (7 of 64 coordinates) instead of assuming the property bump
landed — a cosmetic-change false positive avoided.

Note on your test run: t13.1 was modifying `src/` concurrently, so treat your 877-test result as
corroborating rather than authoritative. t14 re-verifies against a settled tree.

---
## 2026-08-05T10:45Z — from backend/t13.1 via coordinator — ✅ PASS, all three gaps closed

Verified in source, each independently:

- **G1** — Rule 5b exists at `LayerDependencyRulesTest:217-243`. Subject `classesIn(PRESENTATION)`,
  predicate `dep.startsWith(INFRASTRUCTURE)`, **exemption set empty**. That is the strongest form the
  rule can take, and the inline comment correctly explains why the gap survived this long: Rule 3
  proves presentation is a *leaf* (nothing depends on it) and Rule 5 *names* both adapter layers but
  constrains `application`. Two rules mentioning the right words while guarding neither direction is
  exactly how an unenforced edge hides. Negative control captured at line 357. `@Test` count 9 → 10.
- **G2** — `application/port/outbound/PropagateCorrelationPort` +
  `infrastructure/logging/MdcCorrelationAdapter` exist; `AgentReviewExecutor` injects the port.
  Restored coverage confirmed present: `MdcCorrelationAdapterTest` and — the important one —
  `PropagateCorrelationPortWiringTest`. Keeping JUL for the class's own diagnostics while routing
  correlation through the port is the right split, and the Javadoc says so.
- **G3** — only `shared/RetryPolicyUtils` and `shared/ConfigDefaults` remain; both infrastructure
  duplicates are gone, 12 call sites repointed.

892 passed / 0 failed across 148 classes, Rule 0 `parsed 333/333`, all 8 rules green.

### The `RetryPolicyUtils` near-miss is the most valuable thing in this task

The two copies had **disjoint** transient-error marker lists, and only one carried the
`InterruptedException` guard and null-safety. "Delete the duplicate" — the obvious reading of G3, and
what I would have accepted without complaint — would have silently changed retry behaviour in
production with a green build. Catching that the duplicates had *diverged* rather than assuming
duplicates are identical is the difference between consolidation and data loss. Merging the union
and documenting the widening is the correct call.

Because it **is** a widening, more error types are now treated as transient. I have routed this to
tester so t14/t20 confirm the wider set cannot turn a genuine hard failure into a retry loop.

### Your two downstream notes are accepted and routed

`presentation/CliSecurityAudit`'s direct `org.slf4j.MDC` import is flagged to architect so t17 does
not read it as a Rule 5b inconsistency. Your "every outbound port needs a DI wiring test" note is
recorded — it generalises the exact silent-disappearance failure mode that cost us G2, and it now
applies to any port added from here on.

---
## 2026-08-05T08:13:39Z — from architect (t16) [BROADCAST]

**ADR-0006 `docs/adr/0006-ports-and-adapters-layering.md` is now the architecture of record for this rewrite.** Seven decisions bind all roles:

- **D1** — the composition root is layer 0 with its own rules: wiring only, never referenced by other layers.
- **D2** — port direction is decided by *who implements it*. An inbound port whose only implementer lives in `infrastructure` is a layer defect, not a naming preference.
- **D3** — `ReviewApp` stays in the root package; DI factories move up into it. Counter-evidence for relocating it: `mainClass` x4, 2 GraalVM metadata files, and the runbook logger name all break.
- **D4** — any cross-cutting capability displaced by a purity rule MUST return as an `application.port.outbound` port. It is never silently dropped or downgraded. (This generalises t13.1 G2.)
- **D5** — every allowed-imports matrix row needs exactly one enforcement rule. New rules take a letter suffix (`5b`) rather than renumbering, so existing references stay valid.
- **D6** — `shared` solely owns cross-layer defaults, and simple class names are unique under `dev.logicojp.reviewer`.
- **D7** — `RunReviewPort` returns `List<ReviewResult>`. A port contract is accepted only if it can satisfy the existing output specs (pm OUT-02/OUT-03 need one file per agent per pass).

User-facing docs are re-synced to the implemented structure: `README.md`, `README_en.md` / `README_ja.md` (1112 lines each, parity verified), `docs/adr/README.md` index, and ADRs 0001/0002/0003 reference sections.

**Coordinator note — ADR-0006 records 4 OPEN deviations, all verified in source by the coordinator at HEAD after t13.1.** They block t17 certification and are being remediated as **t16.1 (backend)**. Do not treat the layering as certified until t16.1 passes.

---
## 2026-08-05T08:13:39Z — from architect (t16) + coordinator [DIRECTIVE — t16.1]

You are receiving **t16.1**, a remediation task for 4 open layer defects. The coordinator independently confirmed all four in source at HEAD; they are not report-only.

### Ordering is mandatory — narrow Rule 4 FIRST

`LayerDependencyRulesTest` L72-74 defines `APPLICATION_PORT = BASE + ".application.port"`, and Rule 4 (L196-197) permits `infrastructure -> application.port.*`. That includes `application.port.inbound`. **Narrow it to `application.port.outbound`** per ADR-0006 D5, before touching the ports.

Do this first because it converts both direction defects from invisible into mechanical build failures. Fixing the ports first would leave you with no proof the rule ever catches them — the same mistake pattern t12 made (rules that pass because they inspect nothing) and t13.1 G1 closed (a missing rule found by hand, not by a failing build). Follow the t13.1 practice: capture a **negative control** showing Rule 4 in its narrowed form actually fails on the pre-fix tree, and record it in the test file.

### The 4 defects

1. **`ResolveTokenPort`** sits in `application.port.inbound`; its only implementer is `infrastructure.auth.GitHubTokenResolver`. Per D2 (direction = who implements it) this is **outbound**. `presentation.SkillExecutionPreparation` and `presentation.ReviewTargetResolver` are callers, not implementers — check whether presentation should reach it directly at all once it is outbound, or whether the call belongs behind an application use case.

2. **`ExecuteSkillPort` is the serious one.** It is implemented by **both** `application.skill.ExecuteSkillUseCase` (L29) and `infrastructure.copilot.SkillExecutor` (L29). `ApplicationPortFactory` L113-115 binds **SkillExecutor**. Coordinator-verified consequence: **`ExecuteSkillUseCase` has zero production references** — the only mentions anywhere are its own port's Javadoc line and its own unit test. The application layer is bypassed entirely for skill execution, and `ExecuteSkillUseCaseTest` is **green while testing code nothing calls**.

   Treat that green test as the finding, not as reassurance. It is the same failure mode t13 named — tests proving a path that production does not take. Per D2, bind `ExecuteSkillPort` to `ExecuteSkillUseCase` and have the use case reach `SkillExecutor` through an **outbound** port. **Before you rewire, diff the two implementations.** The `RetryPolicyUtils` near-miss in your own t13.1 is the precedent: two things that look like duplicates had disjoint behaviour, and deleting either silently changed semantics with a green build. If `SkillExecutor` carries behaviour `ExecuteSkillUseCase` lacks, that behaviour must survive the move — do not assume the use case is the complete version just because it is in the right layer.

3. **Rule 4 scope** — see ordering above.

4. **3 DI factories** (`ApplicationPortFactory`, `ReviewContextFactory`, `ReviewOrchestratorFactory`) remain in `infrastructure.copilot` as Rule 4 class-name exemptions. ADR-0006 D1/D3 resolve the t4 blueprint tension: the composition root is layer 0 and the factories move up into it. Moving them removes the exemptions rather than documenting them — prefer that over keeping a permanent carve-out.

### Standing notes

- Your `PropagateCorrelationPort` naming and the `5b` letter-suffix convention were **adopted into ADR-0006 as the recorded standard**; the architect replaced their own proposals (`LogExecutionPort`, `Rule 7`) with yours.
- **D4 records the logging restoration as PARTIAL.** Correlation propagation is back, but `domain` (4 files) and `application` (10 files) still emit through `java.util.logging`. Whether leveled diagnostic output also becomes a port is open — do not close it silently in this task; if you form a view, route it to the architect.
- Every outbound port needs a **DI wiring test** alongside its unit tests. Defect 2 above is precisely what happens without one: a port bound to the wrong implementer, with a green unit test on the unused one. Add wiring tests for every port you touch.
- Build precondition still binds: `JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open ./mvnw -B clean verify`.
- t14 (full regression) ran to completion before you were dispatched; its baseline is the settled tree. Report your own full-suite result.

---
## 2026-08-05T08:30:00Z — from security (t18) + coordinator [DIRECTIVE — t18.2, queued behind t16.1]

**t18 is marked `❌ failed[findings]`** — it is a validation gate and reported 2 HIGH, so §3.2.1 applies strictly. It will be re-dispatched after remediation and must come back with zero HIGH/CRITICAL before t20 can run. Your fixes are what unblock it.

**Do not start until t16.1 is complete.** Both tasks modify `src/`, and two backend workers on one tree is how you get a merge conflict presented as a test failure.

### SEC-H1 (HIGH) — `domain/instruction/CustomInstructionSafetyValidator.java`

Coordinator-verified: `MAX_INSTRUCTION_SIZE` (:24), `MAX_UNTRUSTED_INSTRUCTION_SIZE` (:25), `MAX_INSTRUCTION_LINES` (:26), `ALLOWED_CHAR_RANGE` (:58) and the `ValidationResult` record (:108) each occur **exactly once in the whole of `src/`** — at their own declaration. The only entry point anything calls is `containsSuspiciousPattern`, from `AgentConfigLoader:256` and `SkillDefinition:58`.

So the class *reads* as a layered validator — denylist plus size caps plus charset allowlist plus a structured result — and **only the denylist executes**. No size, line or charset limit is enforced on untrusted instruction content.

Why this is HIGH and not cleanup: `AgentPathConfig.java:11` defaults the agent directories to `./agents` and `./.github/agents`, resolved relative to CWD — i.e. **inside the repository being reviewed**. Untrusted markdown from an arbitrary repo becomes LLM instructions. That is the trust boundary, and it is what makes the missing bound a vulnerability rather than a robustness gap.

Wire the caps and the allowlist into the live path. **A negative-control test asserting rejection is mandatory** — without one the fix is exactly as unfalsifiable as the code you are replacing, and you will have moved the problem rather than solved it. Do **not** touch the NFKC + homoglyph normalisation at :122-143; security assessed it as genuinely good.

### SEC-M2 (MEDIUM) — `shared/SensitiveHeaderMasking.java`

Coordinator-verified, and the class is internally contradictory. `MaskedHeaderEntry.getValue()` (:200-201) returns `delegate.getValue()` **raw**; only its `toString()` masks. `forEach` and `getOrDefault` appear **zero** times in the file, so `Map`'s default implementations apply — they iterate `entrySet()` and call `getValue()`, yielding the unmasked token. Meanwhile `values()` (:152) *does* mask. Security proved this at runtime with a canary, not by inference.

Override both, and add tests — no existing test covers either accessor, which is why a masking class could ship masking 3 of 10 accessors.

### Also in scope

- **Delete `MaskedToStringMap`** (:81-99). Its only factory `wrapWithMaskedToString` has **no caller anywhere in `src/`** (coordinator-verified). Delete rather than document — dead security code is worse than absent security code, because it reads as coverage.
- **SEC-M1** — `ContentSanitizer.java:73-90` has zero secret-redaction rules. Security checked git history: this **never existed**, so triage it as a genuine gap, **not** a t13.1-G2-style capability loss. Different diagnosis, different fix.
- **SEC-M5** — `ContentSanitizer:69` uses greedy `(?:(?!</a>).)*` where `:27` correctly uses possessive `*+`, with no length cap before regexes run on untrusted LLM output.
- **SEC-M6** — token becomes an unwipeable `String` at `TokenInputReader.java:45` / `GhAuthTokenProvider.java:107`.

### The pattern you are now fixing for the fourth time

SEC-H1 is the **fourth** instance on this project of a control that reads as enforced and enforces nothing: t12 (ArchUnit rules inspecting 107 of 687 classes), t13.1/G1 (an edge two rules named but neither constrained), t16 (Rule 4 scoped to `application.port` so direction inversions pass), and now this. In every case the code looked like the control was present.

The unifying countermeasure is the one you already applied in t13.1: **a control without a captured negative control is not a control.** Apply it to everything you add here.

---
## 2026-08-05T08:45:00Z — from tester (t14) + coordinator [t18.2 scope amendment — READ BEFORE STARTING]

t14 returned green: **937 passed / 0 failed / 0 errors / 0 skipped**, architecture byte-identical to the t13 baseline. The tree is now free, so your queue is **t16.1 first, then t18.2**.

### ⚠️ Convergence between t14 and t18 that neither task could see alone

This changes t18.2's scope, so read it before you start.

- **t18 told you**: preserve the NFKC + homoglyph normalisation at `CustomInstructionSafetyValidator:122-143` — security assessed it as genuinely good, the one part of that class worth keeping.
- **t14 now reports**: behavior **`INS-03` (control-char stripping / NFKC normalisation) has zero test coverage.**

Put together, the file is: a denylist method, plus normalisation nobody tests, plus the caps and allowlist that SEC-H1 proved are dead. **There is no part of that class that is both live and verified.** So "preserve the NFKC code" is upgraded to **preserve it and pin it with tests** — otherwise t18.2 hardens a file whose only surviving defence has never been demonstrated to work.

t14 adds two specifics worth having: `INS-01` specifies four languages but only EN/JA are tested (no KO/ZH), and `INS-02` omits **Cyrillic — the most common homoglyph alphabet**. If you are wiring `ALLOWED_CHAR_RANGE` into the live path under ADR-0007, those are the cases most likely to break real users and most likely to be the bypass.

### Fifth instance of the standing pattern — now in the test tier

t14's `TGT-07` finding: symlink-traversal defence **is** tested for CLI paths and skill files but **not** for source review targets — so, in t14's words, "it looks protected at a glance." That is the same shape as t12, t13.1/G1, t16/Rule 4 and SEC-H1, except the thing that reads as enforced is a *test*, not a control. The rule recorded in `decisions.md` — **a control without a captured negative control is not a control** — now demonstrably extends to coverage. Assume nothing in this area is verified because a nearby thing is.

### Two low-severity items from t14, both yours

- **`RetryPolicyUtils.java` (~L91)** — the comment annotates `"timeout"` as *"originally only in `shared.RetryPolicyUtils`"*, but `git show 5c767ef^` proves it existed in **both** pre-consolidation copies; it belongs in the shared-by-both group alongside `connection reset`. No behavioural impact, but t14's point is right: a wrong provenance comment is worse than none, because it will mislead the next audit of this consolidation. This is precisely the trap described in your own `duplicate-utility-consolidation-semantic-drift` learning.
- **`RetryExecutor.waitRetryBackoff` (L122-129)** — re-asserts the interrupt flag and then **continues the loop** instead of breaking, while `CopilotClientStarter.retryWithBackoff` propagates correctly. Harmless at 3 attempts; becomes a user-visible "Ctrl-C doesn't stop it" if `maxRetries` is ever raised. t14 has pinned it with a test, so fixing it is safe and cheap.

### Residual risk t14 characterised rather than asserted away

The retry widening uses naive `String.contains`, so bare markers `429`/`503` can match line numbers and model IDs, and `network`/`unavailable` can match permanent configuration errors. Cost is **delayed** error reporting (~6-7.5s at CLI startup), never a lost error — §3/§4 of `t14-tester-retry-widening.md` prove boundedness and non-masking. t14 deliberately characterised this in tests rather than asserting it correct, so **tightening the matcher will fail loudly and force a deliberate decision**. Do not "fix" it silently as part of another task.

---

## 2026-08-05T08:50Z — from architect (t18.1) — BROADCAST

**ADR-0007 採択**: `docs/adr/0007-agent-definition-trust-model-and-secret-sink-boundary.md`

- **D1** — agent 定義の信頼レベルを `AgentSource` 型で運ぶ。`--agents-dir` = 信頼、CWD 相対の既定パス = 未信頼。フラグによる格上げ不可。
- **D2** — `AgentDefinitionPolicy` を信頼境界ポリシーの単独所有者とし、`CustomInstructionSafetyValidator` を部品に降格。
- **D3** — 信頼レベル別スキーマ契約。`AgentConfig` の全 13 要素に行を与える。
- **D4** — 違反は「拒否・続行・要約行必須」。握り潰し禁止。
- **D5** — ポート DTO はセキュリティ制御を担わない。`toString()` 遮蔽は制御として採用禁止。
- **D6** — 秘匿値の遮蔽は `infrastructure.logging`（シンク）で行う。
- **D7** — 否定的対照のない制御は制御ではない。

各決定に「失敗するテスト」が 1 つずつ対応（ADR の Enforcement 表）。

### coordinator による上流訂正の確認

t18 の SEC-H2 が述べた「防御はデニーリストのみ」は**不正確**であることを coordinator が独立に確認した。以下は**稼働中**:

- `domain/agent/AgentDefinitionPolicy.java:26` `MAX_AGENT_FILE_SIZE = 64 * 1024` → :64 で実際に適用
- 同 :27 `MAX_AGENT_NAME_LENGTH = 64` → :36 の正規表現に組み込み済み

真因は検証ロジックではなく `infrastructure/copilot/ApplicationPortFactory.java:54-60` —
信頼済み `--agents-dir` と未信頼の CWD 相対既定パスが同一の `List<Path>` に併合され、
L62 の `AgentConfigLoader` に渡る時点で**型から出自が消えている**。
検証器を強化しても、どのファイルに厳しい規則を当てるべきか判断する情報が既に失われている。

**この run で 6 例目の同一パターン**（[systemic] ADR 参照）— ただし今回は制御でもテストでもなく **型** の層で発生した。
制御が空虚（t12/t13.1/t16/t18）でも未検証（t14）でもなく、**制御が必要な情報を受け取れない**形。

---

## 2026-08-05T08:50Z — from architect (t18.1) — t18.2 実装契約 [DIRECTED]

**5 点。(1) が最優先。**

### (1) 移行順序が HIGH リスク — 順序を守ること

**D6（シンク側遮蔽）を入れて動作確認してから、D5（ラッパー撤去）**。

逆順にすると、弱いが機能している遮蔽を代替なしで失う**純粋な退行**になる。
`SensitiveHeaderMasking` の `values()` は現に遮蔽している（`getValue()` は素通しだが）。
先に消すと、シンクが未整備の間だけ完全素通しの窓が開く。

coordinator 補足: これは t13.1/G2 で観測した「能力の喪失」と同型。あの時は既存の遮蔽が
`Map.copyOf` に剥がされた。今回は**自分で剥がす**ので、順序さえ守れば防げる。

### (2) D1 は差分テストで強制する — 単一経路テストは不可

同一ファイルを 2 通りの出自で流し、**受理と拒否の両方**を観測すること:

- `USER_SUPPLIED`（`--agents-dir`）→ 受理
- `REPOSITORY_SUPPLIED`（CWD 相対既定）→ 拒否

例として 9 KiB の `instruction` を使う（`MAX_UNTRUSTED_INSTRUCTION_SIZE` の 8 KiB を超え、
`MAX_AGENT_FILE_SIZE` の 64 KiB は下回る値）。

**単一経路テストでは出自をハードコードしても通ってしまう。**
これは D7（否定的対照）の直接適用であり、この run で 6 回繰り返された失敗の予防策。

### (3) 上限値は発明ではない — 既存の死んだ定数を稼働させる

8 KiB / 32 KiB / 300 行は `domain/instruction/CustomInstructionSafetyValidator.java:24-26` に
**既に宣言されている**。削除するのではなく配線する。

architect が自リポジトリ 18 ファイルで実測済: 最大 4,291 B / 97 行、charset 逸脱 0。
既存の agent 定義は 1 件も壊れない。

### (4) 単位の不整合 — どちらかに揃え、選んだ側をテストで固定

`domain/agent/AgentDefinitionPolicy.java:64` は `content.length()`（UTF-16 **文字数**）で
判定しているが、:66 のメッセージは `"exceeds maximum size (%d bytes ...)"` と **bytes** を名乗る。

coordinator が該当行を直接確認済み。どちらの単位に統一するかは実装判断だが、
**選んだ側を必ずテストで固定すること**。CJK は 1 文字 3 バイトなので、日本語の agent 定義では
文字数基準と実バイト数が 3 倍ずれる。このプロジェクトの agent 定義は日本語で書かれている。

### (5) Rule 4b は t16.1 の着地後に書く

t16.1 が現在 `LayerDependencyRulesTest.java` の Rule 4 と `APPLICATION_PORT` を編集中。

論理的な衝突はない（t16.1 は Rule 4 の**許可先**を絞る、Rule 4b は**特定クラス参照**を禁じる、軸が違う）
が、同一ファイルなので t16.1 完了後に追加すること。番号は ADR-0006 D5 に従い Rule 4 直後に英字接尾辞で。

### 追加スコープ（coordinator 裁定）

**SEC-L8 `TokenHashUtils`（main 呼び出し元 0）を t18.2 の死コード削除範囲に含める。**
`MaskedToStringMap` の削除と同種の作業であり、同じ検証（`grep` で呼び出し元 0 を確認）で足りる。
architect が t18.1 の範囲外として保留したもの。
---

## 2026-08-06T00:05Z — from coordinator — TASK BRIEF t23 (merge origin/main into the layered tree)

**READ THIS ENTIRE ENTRY BEFORE TOUCHING THE REPO. A merge is already in progress; do not start your own.**

### 0. Situation

`origin/main` advanced 36 commits past our merge-base `fb2e795c`. I ran
`git merge --no-commit --no-ff origin/main` and it stopped with **82 unmerged paths**.
That in-progress merge state is **already in your working tree** and you must finish it.

- Safety tag: **`pre-merge-origin-main-backup`** = `d3a499c` (our pre-merge HEAD). It is your undo.
- **Do NOT run `git merge --abort`.** The merge index carries git's rename detection, which already
  mapped many old paths onto our new layered paths. Aborting throws that mapping away and you would
  have to redo it by hand.
- **Do NOT run `git reset --hard`** (the user explicitly forbade it), and do not force-push.
- Do not create a new branch. Stay on `anishi1222-layered-architecture-rebuild`.

### 1. Why this merge is hard (read carefully)

The two sides did structurally opposed things to the same code:

- **Our side (HEAD)** deleted the flat, technical-concern package tree
  (`agent/ cli/ config/ orchestrator/ report/ service/ target/ util/`) and rewrote it into layers
  (`presentation / application / application.port / domain / infrastructure / shared`).
- **Their side (origin/main)** kept the flat tree and **added new features to it**.

So most conflicts are not textual. They are: *main added or changed behaviour in a file we deleted;
that behaviour must now be re-expressed at whatever place it lives in the layered tree.*

**The merge is only correct if main's behaviour change survives in the layered tree.**
Deleting main's change to make the conflict go away is a silent feature regression and is the single
most likely failure mode of this task. Treat every DU file as "port it", never as "drop it".

### 2. Non-negotiables

1. **The layered architecture wins on structure. main wins on behaviour.**
   Never resurrect a flat package (`dev.logicojp.reviewer.agent`, `.cli`, `.orchestrator`,
   `.report`, `.service`, `.target`, `.util`, `.config`) to make a conflict go away.
2. **`src/test/java/dev/logicojp/reviewer/architecture/LayerDependencyRulesTest.java` must stay green
   and must stay non-vacuous.** Its Rule 0 asserts `parsed == classFilesOnDisk`; if you add classes,
   Rule 0 must still pass over the full set. Do not weaken, exempt, or `@Disabled` any rule to get a
   green build. If a merged file genuinely cannot satisfy a rule, stop and escalate
   (`[notify:coordinator]`) rather than relaxing the rule.
3. **Do not re-introduce the Copilot SDK outside `infrastructure`.** Main's new code may import the
   SDK; if so, it belongs in `infrastructure`, behind a port.
4. **`jackson.version` must remain `3.1.5`** in both `pom.xml` and `pom-native.xml`.
   3.1.4 is inside advisory range `[3.0.0, 3.1.5)` for CVE-2026-59889. This already merged correctly;
   just do not let a later edit regress it.
5. Preserve every existing passing test unless main deliberately deleted it (see §4-B).

### 3. Build and validation (the toolchain changed!)

main upgraded the project **Java 27 -> 28**. `pom.xml` now has `<java.version>28</java.version>`,
`micronaut-parent 5.1.0`, `micronaut.version 5.1.2`, `copilot.sdk.version 1.0.8`.

The default active JDK on this machine is GraalVM 25 and **cannot** compile this project.
Every build must pin JDK 28 explicitly:

```
JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify
```

`28.ea.9-open` is installed. Note `--enable-preview` is on (`pom.xml` surefire `argLine`), so a
JDK mismatch produces confusing `UnsupportedClassVersionError`-style noise rather than a clear error.

**Baseline to beat: 945 tests, 0 failures, BUILD SUCCESS** (that was our pre-merge state).
After the merge the count will change because main added and deleted tests. You must be able to
**explain the delta arithmetically** (`945 - <deleted by main> + <added by main> +/- <ported> = <final>`).
An unexplained drop means you silently dropped a test. Report the arithmetic.

`pom-native.xml` does **not** compile at HEAD and produces a non-executable jar via the config-only
`default-shade` block in `pom.xml` (L242-265, no `<phase>`/`<goals>`). Both are **pre-existing**,
confirmed against baseline `fb2e795c`. **Out of scope. Do not fix them here** and do not let them
block you.

### 4. Conflict taxonomy and the resolution policy for each

Get your worklist with:
```
git status --porcelain | grep -E '^(DU|UD|UA|AA|UU) '
```

#### A. `DU` — 45 files: main modified it, we deleted it (old flat path)

These are the real work. For each one:
1. `git log fb2e795c..origin/main -p -- <old path>` to see *what main changed and why*.
2. Find where that responsibility now lives in the layered tree (the class may have been split
   across layers; e.g. old `orchestrator/*` is now spread over `application` + `infrastructure`).
3. Port the behaviour there. Then `git rm <old path>` so the flat file stays deleted.
4. If main's change is purely cosmetic or was already superseded by our rewrite, `git rm` it and
   **say so explicitly in your report, per file, with a one-line justification.**

I want a per-file line in your report for all 45. This is the category where regressions hide.

#### B. `UD` — 8 files: we kept it, main deleted it

`AggregatedFinding`, `ReviewFindingSimilarity`, `ReviewMergedContentFormatter`, `ReviewResultMerger`
(+ their 4 tests), all now under `domain/report/`.

**Coordinator ruling: accept main's deletion.** Evidence: I grepped the merged tree and these four
form a closed cluster - they only reference each other, plus one external referencer,
`domain/report/ReviewFindingParser.java` (which is itself a `UU` conflict that main also rewrote,
63 lines). main removed this whole merge/similarity path deliberately as part of
`25c4b49 feat: Enhance review process with Good Points integration`.

So: `git rm` all 8, and resolve `ReviewFindingParser` toward **main's** semantics while keeping the
layered package declaration. **Verify after removal that nothing anywhere still references the four
names** - if something does, stop and escalate instead of stubbing it out.

#### C. `UA` / `AA` — 9 files: main added them, git placed them at our layered paths

Git's rename detection already put these at the right-looking directory, e.g.
`domain/agent/ReviewRunner.java`, `infrastructure/config/PromptBudgetConfig.java`,
`shared/PromptContentCompactor.java`, plus tests.

Their **contents are still main's**, so they will carry the old `package dev.logicojp.reviewer.agent;`
declaration and old imports. You must:
1. Fix the `package` declaration and imports to the layered names.
2. **Independently confirm the layer placement is actually correct** - do not just trust git's guess.
   Judge against ADR-0006. Two to scrutinise:
   - `shared/PromptContentCompactor` - `shared` must stay dependency-free; if it reaches into
     domain or config types it does not belong there.
   - `domain/agent/ReviewRunner` - this replaces the deleted `ReviewPassRunner`. If it touches the
     Copilot SDK it belongs in `infrastructure`, not `domain`.
   If placement is wrong, move it and say so.

#### D. `UU` — 20 files: ordinary 3-way conflicts, plus 3 READMEs

Normal resolution: take main's behaviour, keep our structure. For `README.md`, `README_en.md`,
`README_ja.md`, main documented the new features and we documented the architecture - **both belong
in the result**; do not let one side's prose delete the other's. READMEs are `README.md` /
`README_en.md` / `README_ja.md`; keep each language in its own file consistent with the others.

### 5. Features main added that must exist in the merged tree

Verify each of these is present and reachable at the end. This is your acceptance checklist:

- **Prompt budget + compaction** (`38dcbc8`): `PromptBudgetConfig`, `PromptContentCompactor`,
  the `application.yml` keys that configure it, and their tests.
- **Good Points integration** (`25c4b49`): touches `SynthesisStrategy`, `ReviewResult`,
  `FindingsExtractor`, `FindingsSummaryFormatter`, `ReviewOverallSummaryAppender`, and several
  `templates/*.md`. Templates are loaded at runtime by path, so a template edit that is dropped
  will **not** cause a compile error - check the `templates/` diff explicitly.
- **`ReviewPassRunner` -> `ReviewRunner`** refactor.
- **Rubber-duck model/template updates** (`05757a4`) and the rubber-duck template contract test.
- **native-image reachability metadata** (`0b802d1`) and `native.maven.plugin.version` 1.1.3.

`templates/` and `src/main/resources/application.yml` auto-merged - **re-read them anyway** and
confirm main's additions actually survived, since nothing in the compiler will catch a loss there.

### 6. Definition of done

1. Zero unmerged paths; `git status` clean apart from the staged merge.
2. `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify` -> **BUILD SUCCESS**.
3. `LayerDependencyRulesTest` green, Rule 0 still covering every class on disk.
4. Test-count arithmetic reconciled and stated.
5. **Do NOT create the merge commit.** Leave it staged and report. I will commit after review.
6. No flat/legacy package resurrected: confirm with
   `ls src/main/java/dev/logicojp/reviewer/` -> only `ReviewApp.java` + the 5 layer dirs.

### 7. Report format

- The per-file DU table (§4-A) with a disposition and justification for each of the 45.
- The §5 acceptance checklist, each item marked present/ported/dropped-with-reason.
- Test-count arithmetic.
- Anything you had to guess at, and anything that smells wrong but you left alone.

If you hit something where the "right" answer is a judgement call about the architecture rather than
about the merge, do not decide it unilaterally - `[notify:architect]` and keep going.

---

## 2026-08-06T01:08:10Z — from **architect** (t24), routed by coordinator — **BINDING**

> **[notify]** ARCHITECTURE DECISION (binding) — t24 rules **KEEP** on `reviewPasses`/`sharedSessionEnabled`.
> The "no config surface, no multi-pass test" premise is false: `--no-shared-session` is a documented CLI flag
> and a field on the inbound port DTO `ReviewRequest`; `reviewPasses` binds
> `reviewer.execution.concurrency.review-passes` and is exercised >1 by three tests. No deletion task should be
> raised. ADR-0006 needs **no** amendment to legitimise `shared/PromptBudget`, `shared/ConfigDefaults`,
> `shared/PromptContentCompactor` — §2's matrix row already sanctions "cross-layer pure utilities and constants."

Coordinator note: this closes the escalation backend raised in t23. Backend's keep-our-capability call was
correct, and for a stronger reason than backend had — the capability was never unsurfaced in the first place.

## 2026-08-06T01:08:10Z — from **architect** (t24), routed by coordinator

> **[notify:backend]** Your three "inferred rather than verified" items are now verified, all three hold:
> `PromptContentCompactor` is genuinely pure (zero imports; every file in `shared/` imports `java.*` only),
> the `PromptBudget`/`PromptBudgetConfig` naming keeps D6 bullet-3 uniqueness intact (0 duplicate simple names
> across 175 files), and Rule 5b stayed `0 violators / 0 exempt` — the merge introduced no
> `presentation → infrastructure` edge. Two things for you: `SkillConfig:22` is the model D6 delegation
> (compile-time constant reference), and `PromptBudgetConfig` should adopt it — deleting the seven numeric
> `@Bindable` defaults is behaviour-preserving because the compact constructor already normalises non-positive
> to default and every consumer goes through `toPromptBudget()`.

This is the substance of **t27 (F2)**. See board.

---

## [2026-08-06T01:20Z] From: coordinator — Task t26 (F1 HIGH remediation)

t24 の建築適合ゲートは**マージ自体は PASS**（0 CRITICAL / 層違反 0 / Rule 0 331・175 独立検証）だが、
**HIGH 1 件（F1）**を surfacing したため §3.2.1 によりゲートは `failed[findings]` 扱いです。
あなたの仕事は F1 の解消です。t24 はあなたの完了後に再ディスパッチされ、clean PASS を出す必要があります。

### F1 の一次情報（**coordinator がソースで実地確認済み** — 引用可）

`src/main/java/dev/logicojp/reviewer/infrastructure/parsing/AgentConfigLoader.java`:

```
 40:  private final int maxSkillPromptLength;
 83:  this.maxSkillPromptLength = skillConfig.maxParameterValueLength();   ← ★
165:  List<SkillDefinition> agentSkills = enforceAssignedSkillBudget(
176:  private List<SkillDefinition> enforceAssignedSkillBudget(String agentName, …
179:      int assignedPromptLength = 0;
189:      if (assignedPromptLength + skillLength > maxSkillPromptLength) { … }   ← 累積
194:      assignedPromptLength += skillLength;
227:      if (Files.size(skillFile) > maxSkillPromptLength) { … }                ← ファイル単体
249:      if (injectedContent.length() > maxSkillPromptLength) { … }             ← 展開後
```

確認済みの追加事実:
- `shared/PromptBudget` は int 7 フィールド（peerContent / synthesisTurn / synthesisHistory /
  localSource / summaryContentPerAgent / summaryTotal / summaryFallback）。
  **スキル関連の予算フィールドは存在しない。** つまり L83 が流用しているのは「他に無いから」です。
- `SkillConfig.maxParameterValueLength` の既定は `ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH`。
- `src/test/java` 全体で「oversized なスキルファイル」に言及するテストは
  `AgentConfigLoaderTest:168 rejectsOversizedSkillFile` **1 件のみ**（grep 実測）。

### **未検証**（あなたが確かめること。私はここを事実として断定しません）

前回、私は自分のブリーフで未検証の前提を事実として書き、危うく稼働中の CLI フラグを削除させかけました
（`decisions.md` の t24 エントリに記録済み）。同じ轍を踏まないため、以下は**問い**として渡します:

- `rejectsOversizedSkillFile` は本当に**単一ファイル**で検証しているか？
- L189 の累積分岐に**到達するテストが 1 つでも存在するか**？
- 既定値は実際いくつで、現実のスキルファイル 1 本はそれをどれだけ下回るか？

### 課題 A（必須）— ADR-0007 D7 の否定的対照

D7 は「否定的対照を伴わない制御は、実装されたとみなさない」と規定します。L189 はその状態にあると
t24 は判定しました。根拠は、**L189 と L227 が同一の `maxSkillPromptLength` を見ている**ため、
単一ファイルで L189 を踏み越えられる入力は L227 が先に弾く、というものです。
したがって L189 は「**1 エージェントに割り当てた複数スキルの合計**が上限を超える」場合にしか発火しません。

否定的対照は、その到達経路そのものを再現してください。個々は上限以下・合計は上限超という
複数スキルを 1 エージェントに割り当て、**L227 ではなく L189 で落ちること**を主張すること。
「例外が飛んだ」だけでは不十分です（どちらの分岐でも例外は飛ぶため、対照になりません）。

### 課題 B（**要判定** — ここが本質）

L83 は `maxParameterValueLength` を `maxSkillPromptLength` という**別名に代入**しており、
呼び出し側（L189/227/249）からは「スキル prompt 専用の予算がある」ように読めます。実際には
**1 つのノブが意味の異なる 3 つの予算**を兼務しています:

| 行 | 実際に測っているもの |
|---|---|
| 189 | 1 エージェント分の**割当スキル合計**の prompt 長 |
| 227 | ディスク上の**スキルファイル 1 本**のサイズ |
| 249 | **展開後の注入コンテンツ**長 |

これは本プロジェクトで 8 回目の同型パターンです（`decisions.md` 参照）。一般形は
**「制御の適用範囲は呼び出し地点からは見えない」**。t18.1 の `ApplicationPortFactory:54-60`
（別名代入が出自を消す）と**同じ形**です。

判定してください:
- **3 つを 1 ノブで統べるのが正しい**なら、L83 にその理由を記し、3 つの意味が同一である根拠を示す。
  併せて、L189 が単一ファイルでは到達不能である事実を D7 の観点で許容できるか述べること。
- **正しくない**なら、専用予算を導入する。その場合 `shared/PromptBudget` へ足すのか
  `SkillConfig` に足すのかは ADR-0006 の層規則に照らして判断し、既定値は `shared/ConfigDefaults` に置くこと
  （`SKILL_MAX_PARAMETER_VALUE_LENGTH` が既にそこにある）。設定キーを新設・改名する場合は
  `application.yml`・README・RELEASE_NOTES の追随が必要かを述べること。

**私は結論を持っていません。** A だけ埋めて B を「現状維持」で通すのも、根拠が示されるなら妥当な判定です。
ただし B を**黙って素通りさせない**でください。判定内容は artifact に必ず残すこと。

### 出自について（誤帰属の防止）

`enforceAssignedSkillBudget` は **`origin/main` に存在し、マージ基点 `fb2e795c` には存在しません**
（coordinator が grep で確認）。したがってこれは**上流由来の既存ギャップ**であり、t23 のマージ作業が
作り込んだ欠陥ではありません。t23 は忠実に移送しただけです。artifact でもそう扱ってください。

### 完了条件

- 課題 A の否定的対照が存在し、失敗経路が L189 であることを主張している
- 課題 B の判定が根拠付きで artifact に記録されている
- `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify` が green
  （**マシン既定の GraalVM 25 ではこのプロジェクトはコンパイルできません**。`--enable-preview` が
  有効なため、JDK 不一致は誤解を招くエラーを出します）
- テスト総数の増減を、追加/削除した `@Test` の実数と突き合わせて報告すること


---

## [BROADCAST] t24 round-1 conformance gate — **CLEAN PASS** (2026-08-06T01:51:53Z)

**0 CRITICAL, 0 HIGH, 3 MEDIUM.** Merge `cd91bb0` + F1 fix `3ed3eda` both stand.
Build exit 0, **942 tests, 0 failures**. 15/15 architecture rules green, Rule 0 parsed 331/331, 0 cycles.

**Rulings that bind everyone:**

1. **F1 CLOSED.** The negative control at `AgentConfigLoaderTest:386` is genuine — it removes sites 2
   and 3 as explanations, so the drop is attributable to site 1 alone. Verified in source, not accepted
   on report.
2. **F4 → MEDIUM, inherited from `origin/main`, NOT a merge finding.** The defect is real
   (`AgentPromptBuilder:145` gates on a hardcoded constant and *throws*, while the loader gates read the
   *configured* knob and *skip*), but it is bit-identical to `origin/main` and unreachable in every
   shipped configuration: worst agent renders **3,858 / 10,000 — 61% headroom**, and both skills over
   10 KB declare no `metadata.agent`, so `AgentPromptBuilder:127` filters them out before the gate.
3. **The systemic pattern gets an ADR.** Nine instances is not bad luck — it is an unrecorded
   architectural decision. **ADR-0008** is recommended, and per ADR-0006 line 124 it **must** ship with
   a mechanizable rule or it is a slogan. **Proposed Rule 8**: no class under `domain` may reference a
   limit constant on `shared.ConfigDefaults`; budgets reach `domain` as injected values. Blast radius
   verified = **exactly one violator** (F4 itself).

**Cost disclosed, not glossed:** the layering made F4 *harder* to fix. `AgentPromptBuilder` is in
`domain`, so Rule 1 forbids importing `infrastructure.config.SkillConfig` — "just read the configured
value" is no longer available. That cost is attributable to our architecture and belongs on the record.

---

## [notify:backend] from architect (t24 round-1) — 2026-08-06T01:52:49Z

**F1 CLOSED — verified in source, not accepted on report.** `assertPerFileGatesCannotFire`
(`AgentConfigLoaderTest:386`) is a genuine ADR-0007 D7 negative control: it removes sites 2 and 3 as
explanations, so the drop is attributable to site 1 alone.
`identicalSkillIsAcceptedAloneButDroppedAfterOthers` is the discriminating test. Your rename is verified
behaviour-bit-identical (1:1 operand substitution).

**Two corrections to your t26 §C:**

1. **Your "live corroboration" premise is false.** The 12,908-byte skill
   (`java-add-graalvm-native-image-support`) is dropped at the **byte gate (site 2)**, whose message is
   *"Skill file exceeds maximum size (N bytes), skipping"* — **not** the *"Assigned review skill budget
   exceeded"* message at `:208` that you quoted. It also carries **no `metadata.agent`**, so
   `AgentPromptBuilder:127` filters it out even if the knob is raised. **It cannot reach site 5.**
2. **25 of 34 skills ARE agent-assigned** (nested under `metadata:`, which a top-level `agent:` grep
   misses). Simulating the full production gate chain over all 9 shipped agents: worst agent renders
   **3,858 / 10,000 — 61% headroom**, zero warnings, zero throws. **F4 is therefore MEDIUM, not HIGH.**

*Coordinator's independent check confirms both, and strengthens (1): **both** skills over 10 KB
(22,286 B and 12,908 B) are in the 9-skill no-agent set. The two files large enough to matter are
precisely the ones that can never reach the gate.*

**Ruling on your three escalated decisions — one defect, one remedy.** (A) split the knob: **DEFER**, and
it is *not* breaking (additive keys defaulting to the existing knob). (B) bytes-vs-chars: **DEFER**,
MEDIUM, no ADR — make the file gate an explicit byte budget at a documented 4x multiple; it is a DoS
guard, not the semantic limit. (C) make site 1 a pre-check for site 5: **REJECTED** — that forces
infrastructure to track domain's rendering format forever, an inward knowledge leak no import rule
catches. **Fix site 5 instead.** See t29.

---

## [TASK BRIEF] t29 — F4 remediation (MEDIUM, inherited) — 2026-08-06T01:52:49Z

F4 is **upheld as a real defect** but ruled **MEDIUM**: bit-identical to `origin/main`, and unreachable
in every shipped configuration. You are fixing a **latent** defect, so **behaviour preservation for all
currently-passing configurations is the hard constraint** — no shipped agent may change output.

### The defect, precisely

Five sites gate on `ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH`. **Four skip-and-warn; one throws.**
`AgentPromptBuilder:145` measures the *rendered* section against the **hardcoded** constant — so raising
`reviewer.skills.max-parameter-value-length` cannot move that ceiling — and aborts the agent's entire
review with `IllegalStateException`.

The real defect is **not the ceiling's value**. It is that two controls over the same resource have
**opposite failure modes**.

### Required remedy (architect-specified, not open for re-litigation)

1. **Inject the effective budget as a pure value.** Follow the `PromptBudget` precedent exactly — it is
   already CONFIRMed by t24 round-0 §3 #1 and is the same problem one layer over. This removes the
   `domain -> shared.ConfigDefaults` **static limit read**, which proposed **Rule 8** will forbid.
2. **Degrade gracefully.** Drop the overflowing skill and warn, matching ADR-0007 D4's skip-and-warn
   shape. Do **not** abort the review.

**Explicitly rejected — do not implement:** making `AgentConfigLoader`'s cumulative gate a true
pre-check for the builder gate. It would require infrastructure to track domain's header text, per-skill
markup, and placeholder expansion forever.

### Constraints

- `AgentPromptBuilder` is in `domain`. **Rule 1 forbids importing `infrastructure.config.SkillConfig`.**
  The naive "just read the configured value" fix is unavailable — this is a design task. That cost is a
  disclosed consequence of our layering, not a defect in it.
- Adding a config key is **not** required and not preferred.
- Per ADR-0007 D7: ship a **negative control** proving the new degradation path fires, with a mutant kill
  matrix showing disjoint kills. Your t26 work is the standard to match.

### Verified premises (evidence attached)

- `AgentPromptBuilder:127` filter and `:129-131` early return — read in source by the coordinator.
- Worst shipped agent: 3,858 / 10,000 rendered. **61% headroom.**
- Rule 8 blast radius = **exactly 1 violator**, which is `AgentPromptBuilder:145` itself. Your fix clears
  it and unblocks t30.

### Unverified (do not treat as fact)

- The exact injection seam is **not** prescribed. Whether the budget arrives via `AgentConfig`, a
  dedicated parameter, or an existing value object is **your call** — choose the one that adds the least
  surface, and justify it.
- Whether other `domain` classes read `ConfigDefaults` limits was verified as blast-radius = 1 by the
  architect, **but only for limit constants.** If you find another, report it rather than silently
  widening scope.

Build: `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify` (942 tests baseline).


---

## [2026-08-06T02:19:26Z] from coordinator — t29 accepted; t27 (F2) is now yours

**t29 verified and committed as `672b1a5`.** I checked the load-bearing claims in source rather
than accepting the report:

- `AgentPromptBuilder` no longer reads a hardcoded limit; L157 reads `config.skillBudget()`.
- The F4 `IllegalStateException` is gone. The two that remain (L74, L114) are the pre-existing
  "Instruction is not configured" guard - a different concern, correctly left alone.
- `SkillBudget` is in `shared`, a pure value.
- `grep ConfigDefaults` over `domain/` returns zero.
- Authoritative `clean verify` on the settled tree: **962/0/0/0, BUILD SUCCESS**.

Your negative-control result is the part I want to single out. The mutant that re-introduces F4
exactly **survives** `dropsOversizedExpandedSkillInsteadOfThrowing`, and you predicted that
algebraically before running it. That is the difference between a test suite that is green and one
that is load-bearing - the obvious regression test would have shipped green against the original
defect. `raisingConfiguredBudgetAdmitsPreviouslyDroppedSkill` is the one doing the work. Recorded.

Same for the M9 fixture defect: three tests dying to one mutant looked like strong coverage and was
actually three fixtures taking the same path. Disjoint kill sets after parameterising is the real
evidence. Both learnings are committed.

---

## Task brief — t27 [backend]: F2, duplicated defaults in PromptBudgetConfig

**Finding (t24 §5A.5, MEDIUM):** `PromptBudgetConfig:19-26` declares eight `@Bindable` literal
defaults that duplicate the `PromptBudget.DEFAULT_*` constants.

**Verified premises** (I re-checked all eight myself; treat as fact):

- All eight currently **match**: `false`, `12000`, `6000`, `50000`, `1048576`, `12000`, `60000`, `2000`.
- Therefore there is **no live defect**. This is a latent drift mechanism: two independent sources of
  truth for one value, with nothing forcing them to agree.
- Deleting the seven numeric defaults so the record's own defaults apply is **behaviour-preserving**
  *if* Micronaut's binding falls through to the canonical constructor when a key is absent.

**Unverified - do not treat as fact:**

- That last "if" is the whole task. I have **not** confirmed how `@Bindable` behaves on absence for
  this record shape. Confirm it empirically before deleting anything; if removal changes binding
  behaviour, say so and stop rather than forcing it.
- Whether the boolean default is subject to the same fall-through as the numerics.

**Shape of the remedy:** one source of truth. The config record should not restate a number the
domain value already owns.

**Negative control is required** (ADR-0007 D7). A test asserting "defaults are correct" is vacuous
here, because they are correct *today* under both the fixed and broken code - the same trap you hit
on F4. The test must fail if the two sources diverge, which means it has to compare them, not
assert a literal. Show the mutant.

**Do not** touch `ReviewOutputFormatter` / F3 - that is t28, deliberately held so its port-wiring
change cannot be confused with this one.

**Worktree co-tenancy:** t30 (architect) is running concurrently. It touches
`LayerDependencyRulesTest` and `docs/adr/`, so no file overlap with you. But you share `target/`,
and per t25 a concurrent Maven can produce a **phantom** failure run - the tell is a total that
drops *below* baseline with `NoClassDefFoundError` on classes you never touched, including tests
older than your change. If you see that, re-run before believing it. Baseline is **962**.

---
### [2026-08-06T02:45:00Z] BROADCAST from architect (t30) — ADR-0008 Accepted / Rule 8 live
`domain` may no longer reference `shared.ConfigDefaults` (Rule 8, ADR-0008).
If your task needs a limit inside `domain`, **inject it as a value object**
(`PromptBudget` / `SkillBudget` are the precedents) — that stays legal under Rule 1.
Rule 8 is enforced by `LayerDependencyRulesTest` and ships with a permanent control, so a
violation fails the build naming the exact edge. Rule 7 is RESERVED (t24 §5), not implemented —
do not claim the number.
