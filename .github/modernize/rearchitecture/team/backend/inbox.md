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
