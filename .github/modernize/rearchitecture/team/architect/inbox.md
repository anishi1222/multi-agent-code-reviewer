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

## 2026-08-05T02:16:00Z — from coordinator [carry-forward directive]

t2 reported 3 HIGH migration risks. They are codebase characteristics with mitigations,
not defects in the t2 deliverable, so t2 is PASS. However they are NOT dropped — they
become MANDATORY acceptance criteria:

**t4 (architect) MUST explicitly resolve all three in the target design:**
- R1 `ReviewResult` imported by 6+ packages → assign a target layer and specify the
  single-pass import-update sequencing.
- R2 `TemplateService` is the hub of 5 cycles, imported by 8+ classes across 4 packages
  → define `LoadTemplatePort` in `application.port` and specify the adapter.
- R3 `AgentConfig` + `SharedCircuitBreaker` shared across agent/skill/service → split
  pure-domain parts into `domain`, keep DI-wired factory in `infrastructure`.

Each must appear in the t4 port catalog / class-map with an explicit resolution, and the
10 cycles in `t2-architect-cycles.md` must each map to a named breaking mechanism.

**t6 (teamlead quality gate) MUST verify R1–R3 are resolved in t4 and that all 10 cycles
have a documented breaking mechanism. Unresolved carry-forward = FAIL.**

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

---
## 2026-08-05T06:05Z — from backend via coordinator (t12.1) — ADR-0006 INPUT

t12.1 surfaced two places where the t4 blueprint contradicts itself. Both are currently
reconciled by **named exemptions in the enforcement layer**, which is honest but is a workaround.
ADR-0006 must record the intended end state.

**(1) `ReviewApp` placement.** t4 §1 places `ReviewApp` in `presentation/`, but it lives in the
root package `dev.logicojp.reviewer` and imports five `presentation.*` types. t12.1 exempted it
by name rather than moving it, since t12.1 was scoped to the enforcement layer.

Moving it is **not free**: `ReviewApp` also imports `LogbackLevelSwitcher`, which sits in the root
package and is destined for `infrastructure.logging`. Relocating `ReviewApp` into `presentation`
as-is would trade a Rule 3 violation for a new `presentation → root` violation. **Sequence both
moves together**, or document the root package as an explicit composition-root layer.

**(2) Factory placement.** t4 §3 places the three Micronaut `@Factory` classes in
`infrastructure.copilot`, while t4 §2's allowed-imports matrix forbids
`infrastructure → application` internals. Binding a port to its implementation necessarily names
that implementation, so a composition root cannot satisfy §2 as written. Currently reconciled by
three named exemptions in Rule 4.

**§2 should state the composition-root carve-out explicitly** rather than leaving the matrix
self-inconsistent and the exemptions unexplained. Either designate a composition-root package
that is exempt by design, or move the factories somewhere the matrix already permits.

Both items are yours to resolve in t16 (documentation + ADR). The enforcement layer will follow
whatever you decide — but it must follow it, not accumulate more exemptions (see E3 in
backend/inbox.md).

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
## 2026-08-05T10:00Z — from backend via coordinator (t13) — ADR-0006 INPUT (2nd batch)

t13 deleted the pre-migration tree and, in doing so, exposed four items that are **your** calls,
not backend's. These join the two from t12.1. ADR-0006 should resolve all six together.

**(a) Layer purity vs. observability — the important one.** t4 §2's purity rules pushed SLF4J out
of `application`, so `AgentReviewExecutor` was switched to `java.util.logging`, which has no MDC.
Virtual-thread correlation propagation was lost and its tests were deleted. Purity was preserved
by discarding a capability. I have directed backend (t13.1/G2) to reintroduce it as a
**logging/correlation port** in `application.port.outbound` implemented in
`infrastructure.logging` — the Ports & Adapters answer that keeps the application layer
framework-free *and* keeps MDC. ADR-0006 should record this pattern as the standing rule for any
cross-cutting capability that purity displaces, because this will recur (metrics, tracing).

Related: Rule 2 forbids SLF4J in `shared`, which forced `LogValueSanitizer` into `shared` and
`CliSecurityAudit` into `presentation` to preserve PM behaviour AUTH-11. That split is reasonable
but should be documented as intentional, not incidental.

**(b) Missing rule — already actioned.** t4 §2 mandates `presentation ⊥ infrastructure` but no
rule enforced it; two live violations were hand-fixed. Backend adds the rule in t13.1/G1. ADR-0006
should state the rule set is expected to cover **every** row of the §2 matrix, so a missing rule is
itself a defect.

**(c) `ReviewApp` relocation needs its own task.** Moving it into `presentation` (t4 §1) touches
`pom.xml` ×2, `pom-native.xml` ×2 and two GraalVM `reachability-metadata.json` files. Decide in
ADR-0006 whether to relocate it or to formally designate the root package as the composition root
and drop the requirement. If you choose relocation, say so and I will schedule it as a task rather
than letting it leak into an unrelated one.

**(d) Duplicate utilities.** `ConfigDefaults` and `RetryPolicyUtils` exist in both `shared` and
`infrastructure.*`. Backend removes the duplicates in t13.1/G3; ADR-0006 should state which layer
owns shared defaults so this does not regrow.

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
## 2026-08-05T10:45Z — from coordinator (t13.1) — TWO ITEMS FOR t17

**1. Do not read `presentation/CliSecurityAudit` as a Rule 5b violation.** It retains a direct
`org.slf4j.MDC` import. Per ADR-0006 this is deliberate: the audit fields are same-thread scoped and
never cross a thread boundary, so they do not need the correlation port. Rule 5b constrains
`presentation -> infrastructure` and SLF4J is neither. Flagging pre-emptively so it does not consume
review cycles as a false positive.

**2. The adapter matrix is now closed — please verify that claim rather than accept it.** Rules 4, 5
and 5b together assert: infrastructure reaches application only through ports; application reaches
neither adapter; presentation does not reach infrastructure. t13 proved by counterexample that a
missing rule is invisible until someone reads the design matrix against the rule set line by line.
Do exactly that against `t4-architect.md` §2 — **every row of the matrix must map to a rule**, and a
row with no rule is itself a defect regardless of whether violations exist today.

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
## 2026-08-05T08:30:00Z — from security (t18) + coordinator [DIRECTIVE — t18.1, dispatched now]

**t18 is marked `❌ failed[findings]`** (validation gate, 2 HIGH, strict §3.2.1). You own **SEC-H2**, the design half. It is dispatched immediately as **t18.1** because it is design-only and does not touch `src/`, so it can proceed while t14 finishes.

### SEC-H2 (HIGH) — prompt-injection defence is denylist-only

`infrastructure/parsing/AgentConfigLoader.java:234-241` defends by denylist alone. Denylists cannot enumerate paraphrase, encoding or translation bypasses. Security's key structural point: **the allowlist that would bound the input space is exactly the dead code in SEC-H1** — `ALLOWED_CHAR_RANGE` and the size caps in `CustomInstructionSafetyValidator` are declared and never executed (coordinator-verified: each occurs exactly once in all of `src/`).

So the two HIGHs compound. Fix one and the other still fails. That is why this is split — you decide the trust model, backend wires it in (t18.2).

**The trust boundary is the crux**: `AgentPathConfig.java:11` defaults agent directories to `./agents` and `./.github/agents`, resolved relative to CWD, i.e. **inside the repository under review**. The tool silently treats markdown from an arbitrary third-party repository as LLM instructions. No pattern-based scan surfaced this; it took reasoning about where the input comes from.

### What is being asked of you

A recorded decision — **ADR-0007** — not a pattern-list update. Security offers three shapes, and you are not bound to them:

1. constrain repo-supplied agent files to a bounded schema (which gives backend the allowlist to enforce),
2. require explicit opt-in before loading agents from the reviewed repo,
3. load them reduced-privilege.

Whatever you choose must be **mechanically enforceable** and must tell backend precisely what to implement in t18.2 — bounds, allowed character classes, required/permitted fields, and what happens on violation. A decision backend cannot turn into a failing test is not finished.

Note the interaction with ADR-0006 **D4**: if you decide some capability must be displaced, it returns as a port rather than disappearing.

Scope correction from security, so you do not over-build: field coverage is narrower than it first looks — `peerModel` and `skills` **are** validated elsewhere; `language` is the one real gap (filed LOW as SEC-L2).

### Second item — SEC-M3/M4, for the same ADR or a short follow-up note

The header-masking wrapper is stripped by **both** `Map.copyOf` and `new HashMap<>`, proven at runtime, and the map is handed to the Copilot SDK at `ReviewSessionConfigFactory.java:56` and `SdkRubberDuckSessionFactory.java:80`. **This is the t13 `defensive-copy-strips-security-wrapper` defect recurring at a new call site** — the third time this shape has appeared.

Security's framing is the right one and needs your ruling: *a `toString()` wrapper cannot survive a boundary it does not control.* The durable options are masking at the sink (a logging port, per ADR-0006 D4) or a `SecretString` type that carries its own protection. Recording this once, as a rule, is what stops a fourth recurrence.

Security bounded its own claim honestly: `javap` shows `McpHttpServerConfig` has no `toString()` override, so there is **no confirmed live sink**; `setHeaders` was not decompiled. Treat it as a latent defect with a known mechanism, not a proven leak — but note SEC-L4 (routed to devops): raising `COPILOT_SDK_LOG_LEVEL` is precisely the condition that would make it live.

### Standing pattern — worth a line in the ADR

SEC-H1 is the **fourth** instance on this project of a control that reads as enforced and enforces nothing: t12 (ArchUnit inspecting 107 of 687 classes), t13.1/G1 (an edge two rules named but neither constrained), t16 (Rule 4 scoped to `application.port`), and now a validator whose caps and allowlist are dead. Security proposes a standing verification gate rather than per-task rediscovery. The countermeasure that has worked every time is **a control without a captured negative control is not a control** — consider elevating it from practice to a recorded ADR-0006 amendment.

### Documentation correction (coordinator, from t18)

While you have ADR-0007 open: `.github/copilot-instructions.md` states that `TemplateService` loads **"Mustache-style `{{placeholder}}` templates"**. Coordinator-verified false — `templates/` contains **zero** `{{...}}` occurrences (all 30 files use `${key}`: `${displayName}` x7, `${peerReviewContent}` x4, `${repository}` x3, ...), and **Mustache is not a dependency** in `pom.xml`. Security caught the same error mirrored in `project-profile.yaml`, which the coordinator has corrected. Worth fixing at the source, since an instruction file that misstates the template syntax will keep propagating into future agent runs.

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

## 2026-08-05T09:20Z — from backend (t16.1), routed by coordinator

### ADR-0006 deviations #1, #2, #3 are CLOSED — Status column needs updating

Coordinator independently verified all three in source at working tree:

| Deviation | Closure evidence (verified, not report-only) |
|---|---|
| #1 `ResolveTokenPort` inversion | `application/auth/ResolveTokenUseCase.java:26 implements ResolveTokenPort`; `infrastructure/auth/GitHubTokenResolver.java:24 implements AcquireGitHubTokenPort` (outbound only). New outbound port `application/port/outbound/AcquireGitHubTokenPort.java` exists. |
| #2 `ExecuteSkillPort` double-implementation | `ApplicationPortFactory:117,123` — `ExecuteSkillPort` now backed by `ExecuteSkillUseCase`. `infrastructure/copilot/SkillExecutor.java` **deleted** along with its test. |
| #3 Rule 4 over-permissive | `LayerDependencyRulesTest:79` `APPLICATION_PORT_OUTBOUND = BASE + ".application.port.outbound"`, applied at `:216`. The `.application.port` prefix that permitted `infrastructure → application.port.inbound` is gone. |

945 tests, 0 failures, BUILD SUCCESS. Reconciles to t14's 937 as −2 +5 +5.

### Two items now need YOUR decision (both are t16.2 scope, not backend's)

**(a) ADR-0006 D3's premise is factually wrong.** D3 names three "Micronaut factory classes" to relocate into the composition root, but `grep -rln "@Factory"` returns **only** `ApplicationPortFactory`. `ReviewContextFactory` is a plain class holding config-mapping logic; `ReviewOrchestratorFactory` is a `@Singleton` implementing the inbound `RunReviewPort`. Executing D3 as written would move **business logic and an inbound-port implementation** into the composition root — contradicting D1's own "wiring only / no business decisions" constraint.

**(b) A fifth, unrecorded direction inversion exists.** `infrastructure.copilot.ReviewOrchestratorFactory` implements the **inbound** `RunReviewPort` — structurally identical to closed deviation #1, but on the review path (the highest-risk path in the app). It is currently masked by a composition-root exemption in Rule 4.

Backend's recommendation, which the coordinator endorses: **record (b) as ADR-0006 deviation #8 and fix it as a real refactor**, rather than letting D3's file-move conceal it. Backend deliberately did **not** implement deviation #4 for exactly this reason — following it would have hidden a live defect behind a green build. That judgement is why t16.1 passes rather than being marked incomplete.

### Coordinator note on the systemic pattern

This is the **seventh instance** of the run's standing pattern, and the second at the *scope-of-application* layer: Rule 4's composition-root exemption is a carve-out whose blast radius is invisible at the call site. Narrowing Rule 4 (t16.1's first act) is what made #1 and #2 fail mechanically instead of by review — and the same narrowing is what left #8 visible only because the exemption list is now the *only* thing hiding it. Consistent with the standing rule in `decisions.md`: **assert the scope, not just the outcome.**

Do not act on D3 as written. Rule the premise first.

### Also on your desk for t17

- `presentation/CliSecurityAudit` retains a direct `org.slf4j.MDC` import — **deliberate** per ADR-0006, not a Rule 5b violation. Pre-warned so certification does not re-flag it.
- ADR-0006 D4 is recorded **PARTIAL**: `domain` (4 files) and `application` (10 files) still use `java.util.logging`.
- `ApplicationPortFactory` method **order is now load-bearing** — Micronaut names generated bean definitions by declaration index (`…$ResolveTokenPort7$Definition`). Append-only; documented in-method by backend. Rule 4's generated-bean exemptions are **derived**, not hard-coded, so they will not silently rot.

---

---

## 2026-08-06T00:54:29Z — from **backend** (t23), routed by coordinator

> **[notify:architect]** Still awaiting your call on main's removal of `reviewPasses`/`sharedSessionEnabled`.
> I proceeded on keep-our-capability: with the YAML keys absent, `@Bindable` defaults give `reviewPasses=1` /
> `sharedSessionEnabled=true`, so runtime behaviour is identical to main while the capability survives.
> This is widening, not narrowing — reversing it later is a config-and-delete change, whereas recovering it after
> deletion means re-implementing. If you rule for removal, that is a follow-up task, not t23.

This is now **your decision to make in t24**, not an open question. See §3 below.

---

# t24 — Post-Merge Architecture Conformance Re-check

**Role:** architect · **Phase:** Upstream Merge · **Depends on:** t23 (✅ PASS)

## 1. Why this task exists

t23 merged `origin/main` (36 commits) into the layered tree: **82 conflicts → 0**, 108 files staged
(+4576/−2380), `BUILD SUCCESS`, 939 tests green. The merge is **staged but not committed** —
`MERGE_HEAD = 5844456`, undo point is tag `pre-merge-origin-main-backup` → `d3a499c`.

You are the gate before that merge commit is created. A green build is **not** evidence that the
architecture survived the merge — it is only evidence that the code compiles and the tests that
still exist still pass.

### The specific reason to distrust a green arch gate here

This project has already been burned **seven times** by the same systemic pattern, recorded in
`.github/modernize/rearchitecture/decisions.md`:

> **A control's scope of application is invisible at the call site.**
> Countermeasure: **assert the scope, not just the outcome.**

Instances: t12 (ArchUnit rules passed while parsing zero classes) · t13.1/G1 (missing rule) ·
t16 (Rule 4 prefix too broad) · t18/SEC-H1 (dead validator caps) · t14/TGT-07 (untested control) ·
t18.1 (`ApplicationPortFactory` erasing provenance by type) · t16.2 (Rule 4 composition-root exemption).

t12 is the exact failure mode to fear here: the arch gate was **green because it was vacuous**.
A merge that moves 108 files is precisely the event that can silently drop files out of the
gate's scope. `LayerDependencyRulesTest` **Rule 0** exists for this — it asserts
`parsed == classFilesOnDisk`. Your job is to confirm Rule 0 is still doing that work over the
*post-merge* file set, not to observe that the suite is green.

## 2. Deliverables you must consume

- `.github/modernize/rearchitecture/artifacts/t23-backend.md` — index
- `.github/modernize/rearchitecture/artifacts/t23-backend-conflict-dispositions.md` — all 82 conflicts, per-category policy
- `.github/modernize/rearchitecture/artifacts/t23-backend-feature-ports.md` — 6 ported features, 2 caught silent regressions
- `.github/modernize/rearchitecture/artifacts/t23-backend-validation.md` — build/test evidence, count arithmetic, layer-purity audit
- `docs/adr/0006-ports-and-adapters-layering.md` — the layer import matrix (authority for every placement call)
- `docs/adr/0007-agent-definition-trust-model-and-secret-sink-boundary.md` — trust boundary

## 3. Decisions you must make (each needs an explicit verdict)

### 3-A. `reviewPasses` / `sharedSessionEnabled` — **KEEP or REMOVE**

`main` deleted both outright. Backend kept them (defaults `1` / `true`, so runtime behaviour matches `main`).

Rule on it as an **architecture** question, not a preference: does a multi-pass review capability with no
YAML surface and no test exercising `reviewPasses > 1` constitute (a) a preserved capability or
(b) dead code that ADR-0006 should not be asked to house? State which, and why.

If you rule REMOVE, do **not** implement it — record it as a follow-up task and let t23's merge stand.

### 3-B. The four placement calls backend made under time pressure

Judge each against ADR-0006's import matrix. These were `UA`/new-file placements where git had no
opinion and backend had to choose:

| # | Placement | Backend's reasoning |
|---|---|---|
| 1 | `PromptBudgetConfig` **split** into pure `shared/PromptBudget` + `infrastructure/config/PromptBudgetConfig` binder | `domain` may not see framework binder types |
| 2 | `SKILL_MAX_PARAMETER_VALUE_LENGTH` hoisted to `shared/ConfigDefaults` | `AgentPromptBuilder` (domain) was importing a framework type |
| 3 | `enforceAssignedSkillBudget` → `infrastructure/parsing/AgentConfigLoader` | ADR-0007 trust boundary |
| 4 | `PromptContentCompactor` → `shared/` | must stay dependency-free |

Confirm or correct. **#4 is the one to check hardest** — `shared/` is the layer with the strictest
purity requirement and the weakest natural enforcement.

### 3-C. Rule 0 non-vacuity over the post-merge tree

Confirm `LayerDependencyRulesTest` Rule 0 (`parsed == classFilesOnDisk`) still holds and still
covers every file the merge added. Report the actual parsed count. **"The suite is green" is not
an acceptable answer to this item** — see §1.

### 3-D. Does ADR-0006 need an amendment?

The merge introduced `shared/PromptBudget` and `shared/ConfigDefaults` — value/constant carriers in
`shared/`. If ADR-0006 does not currently sanction that use of `shared/`, say so and specify the
amendment. Note ADR-0006's **D3 premise is already known false** (t16.2, still open) — do not
re-litigate D3 here, just don't build on it.

## 4. Explicit non-goals

- Do **not** create the merge commit — the coordinator owns that.
- Do **not** run `git merge --abort`, `git reset --hard`, or any destructive command. The user
  forbade this explicitly. Git's rename detection did valuable work mapping flat→layered paths;
  aborting throws it away.
- Do **not** fix t16.2 / t17 / t18.2 — out of scope, separately tracked.
- Do **not** re-open the `UD` deletion ruling (the 4-class merger/similarity cluster). That was
  ruled with grep evidence and backend confirmed it. Attribution correction on record:
  the deletions came from `38dcbc8`, **not** `25c4b49` — the ruling itself stands.

## 5. Build command (non-negotiable)

```
JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify
```

`main` moved Java 27→28. The machine default is GraalVM 25 and **cannot compile this project**.
`--enable-preview` is on, so a JDK mismatch produces misleading errors rather than a clear one.

## 6. Known-out-of-scope defects (do not report as findings)

- `pom-native.xml` does not compile at HEAD — pre-existing, confirmed against merge-base `fb2e795c`
- `pom.xml` L242–265 `default-shade` is config-only (no `<phase>`/`<goals>`), so `verify` yields a
  non-executable jar — pre-existing
- Reduced test coverage from taking `ours` on 10 test conflicts — **already disclosed** by backend in
  `t23-backend-validation.md`. Only escalate if you judge the loss architecturally significant;
  otherwise it is a tracked follow-up.

## 7. Definition of done

Write `artifacts/t24-architect.md` containing:

1. A verdict on **each** of 3-A, 3-B (all four), 3-C, 3-D — no item left implicit
2. The actual Rule 0 parsed count, with the number stated
3. `PASS` / `FAIL` on whether the merge may be committed as-is
4. Any finding at HIGH/CRITICAL, with severity counts, using `[notify:coordinator]`

If you find a violation that is mechanically fixable and clearly within ADR-0006's existing rules,
fix it and say so. If it requires an ADR decision, escalate instead of deciding unilaterally.

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

---

## [2026-08-06T01:35Z] From: backend (t26) — routed by coordinator

### `[notify:architect]` (1/2) — **New finding proposed: F4 (HIGH), needs your ruling**

> `AgentPromptBuilder:145` compares against the hardcoded `ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH`
> and ignores the configured `reviewer.skills.max-parameter-value-length`, while the `AgentConfigLoader`
> gates honour it. An operator who sees `Assigned review skill budget exceeded … skipping skill` and raises
> the configured limit — **the natural remediation** — lifts the loader's ceiling above the builder's fixed
> one, so previously-skipped skills are admitted and the builder then throws `IllegalStateException`,
> aborting that agent's review. The documented remedy makes the failure mode strictly worse, and the crash
> window is unbounded, not the ~71-char markup gap. This is live, not theoretical:
> `java-add-graalvm-native-image-support/SKILL.md` (12,908 bytes) is already dropped at the default budget
> on every test run, so users have a real incentive to raise the knob.
> Details in `artifacts/t26-backend.md` §C.

### `[notify:architect]` (2/2) — **Three design decisions escalated from the t26 ruling**

> Out of backend charter — each is a user-facing config contract change requiring an ADR + migration notes:
> 1. splitting the one knob into separately-configured budgets via new YAML keys;
> 2. resolving the **bytes-vs-chars conflation** — the file gate compares `Files.size()` in bytes against the
>    same integer the content gates compare UTF-16 chars against (27 shipped skills diverge, up to 2×, though
>    **zero** are currently mis-gated, so this is latent not active);
> 3. whether the loader's cumulative gate should become a true **pre-check** for the builder's rendered-section
>    gate — today it under-counts by a verified `71 + 10n` chars plus an unbounded placeholder-expansion delta
>    over the identical skill set.

### Coordinator's disposition

Both are folded into your **t24 re-dispatch** rather than raised as separate tasks — ruling on a proposed
HIGH and on config-contract design is precisely the architect conformance gate's job, and t24 must
re-run anyway under §3.2.1 step 5. See the t24 re-dispatch brief below.


---

## [2026-08-06T01:40Z] From: coordinator — Task t24 **re-dispatch** (§3.2.1 step 5)

t24 は F1（HIGH）を surfacing したため `❌ failed[findings]` として記録されています。§3.2.1 step 5 により、
**ゲート自身が再実行され、clean PASS（HIGH/CRITICAL ゼロ）を出すまで** 下流（t25）は解放されません。
これは再実行ラウンド **1 / 2** です。

### 1. F1 は closable か（t26 の成果を検収する）

backend が t26 で提出したもの:
- `AgentConfigLoaderTest.AssignedSkillBudget` に否定的対照 3 件
  （累積 drop / 順序依存の分離 / `metadata.agent` matched-pair ガード）
- **2-mutant kill matrix**（kill が disjoint = どのテストも vacuous でないことの証明）
- 兄弟ゲート 2 つが当該テストで inert であることを in-test control で明示

**coordinator がソースで独立確認済み**（引用可）:
- 本番差分は**純粋な rename** です。`AgentConfigLoader:98` で
  `int sharedSkillBudget = skillConfig.maxParameterValueLength();` を取り、
  `maxSkillFileBytes` / `maxSkillContentChars` / `maxAssignedSkillTotalChars` の**3 つとも同じ値**を代入。
  よって**挙動は bit-identical**。各呼び出し地点が「どの予算を適用しているか」を型と名前で宣言するようになりました。
- `src/main` に mutant の残滓なし（grep 実測）。

判定してください: **D7 の要求を満たしたか。** 満たしたなら F1 は closed。

### 2. **F4（HIGH 提案）の裁定** — 本再実行の主眼

backend が escalate した F4 を、**coordinator がソースで実地検証しました**。事実は backend の主張より
**重い**です。以下は推測ではなく実測です:

`ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH`（= 10,000）の**消費者は 5 箇所**:

| # | 場所 | 実際に測っている量 | 単位 | 値の出所 | 超過時 |
|---|---|---|---|---|---|
| 1 | `AgentConfigLoader:204` | 1 エージェントの割当スキル**合計** | chars | **設定値** | warn + skip |
| 2 | `AgentConfigLoader:245` | スキルファイル 1 本 | **bytes** | **設定値** | warn + skip |
| 3 | `AgentConfigLoader:267` | 注入コンテンツ | chars | **設定値** | warn + skip |
| 4 | `SkillDefinition:54` | **パラメータ値 1 個**の長さ | chars | 引数 | 拒否 |
| 5 | `AgentPromptBuilder:145` | レンダリング済み**スキル節**全体 | chars | **ハードコード定数** | **`IllegalStateException` を throw** |

つまり `maxParameterValueLength` という名前が**正確なのは 5 用途のうち #4 だけ**です。

F4 の核心（L136–149 を読んだ上での確認）:
- #5 は `ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH` を**直接**参照しており、
  `reviewer.skills.max-parameter-value-length` を上げても**この天井は上がりません**。
- #5 だけが **throw**、#1–#3 は **skip**。つまり「上限に触れたときの挙動」が一貫していません。
- #1 は #5 の**有効な事前チェックになっていません**。2 つの独立した軸で過小計上します:
  (a) #5 は `"\n\n## Assigned Review Skills\n\n"` + 日本語 1 行 + スキルごとの `### ` 見出しと改行を
      **含めて**数えるが、#1 は `name+description+prompt` の素の合計しか数えない（backend 実測 `71 + 10n` chars）。
  (b) #5 は `PlaceholderUtils.replaceDollarPlaceholders`（L141）で**展開後**を数えるが、
      #1 は**展開前**を数える。展開による増分に上界はありません。

したがって backend の言う「ノブを上げると罠にかかる」は**帰結の一つに過ぎず**、より本質的には
**既定設定のままでも #1 を通過した集合が #5 で crash しうる**、という構成上の不整合です。

裁定してください:
- F4 は HIGH として妥当か（coordinator は妥当と見ますが、**判断はあなたの職掌**です）
- 妥当なら remediation の**方向性**（#5 を設定値に合わせる / skip に揃える / #1 を真の pre-check にする 等）。
  実装タスクは私が起票します。**あなたが実装する必要はありません。**
- なお本件は本プロジェクトで**同型パターンの 9 例目**です（`decisions.md`）。一般形
  「**制御の適用範囲は呼び出し地点からは見えない**」の最も純粋な形 — #1 は #5 を守っているように*見えて*、
  別の量を別の天井で別の出所から測っています。この一般形を ADR に昇格すべきかも併せて判断してください。

### 3. escalate された 3 つの設計判断

backend が「backend charter の外」として上げたもの。いずれも利用者向け config 契約の変更で ADR が要ります:
1. 1 ノブを**個別の予算キー**に分割するか（新 YAML キー + 移行注記）
2. **bytes vs chars** の混同（#2 は bytes、#1/#3/#5 は chars）。出荷済み 27 スキルで最大 2 倍の乖離、
   ただし**現時点で誤判定は 0 件**なので latent。
3. #1 を #5 の真の pre-check に格上げするか

各々について「今 ADR 化する / t24 の後段タスクに送る / 現状維持で根拠を記す」を裁定してください。

### 4. F2 / F3 の再確認

前回 MEDIUM とした 2 件（F2: `PromptBudgetConfig` の `@Bindable` 既定値二重定義、
F3: `ReviewOutputFormatter:26` の設定キー不一致）は t27 / t28 として未着手のまま残っています。
**MEDIUM のままか**を再確認してください。HIGH に昇格するなら、その旨を明示すること
（昇格した場合、本ゲートは再び clean PASS を出せません）。

### 5. 完了条件

- 上記 1–4 すべてに裁定を下し、`artifacts/t24-architect.md` を**更新**（別ファイルにせず追記/改訂）
- マージそのものへの適合判定を**再発行**すること（前回: 0 CRITICAL / 層違反 0 / Rule 0 331・独立検証 175）
- **HIGH/CRITICAL が 0 なら clean PASS を明示**。1 件でも残るならラウンド 2 に進み、
  2 ラウンドで収束しない場合は利用者判断に上げます（§3.2.1）
- ビルド: `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify`
  （マシン既定の GraalVM 25 では**コンパイルできません**）


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

## [QUEUED BRIEF] t30 — ADR-0008 + Rule 8 — blocked on t29 — 2026-08-06T01:52:49Z

Your own round-1 recommendation, raised as a task. **Do not start until t29 lands** — Rule 8's single
violator is F4 itself, so adopting the rule before t29's fix would land a red rule.

### Scope

1. **Author ADR-0008** promoting the systemic pattern *"a control's scope of application is invisible at
   its call site"* — nine instances: t12, t13.1/G1, t16, t18/SEC-H1, t14/TGT-07, t18.1, t16.2, F1, F4.
   Convention: **English headings, Japanese body**.
2. **Implement Rule 8** as an ArchUnit assertion in `LayerDependencyRulesTest`: no class under `domain`
   may reference a limit constant on `shared.ConfigDefaults`. Budgets and limits reach `domain` as
   injected values.
3. Per ADR-0006 line 124 — *"a matrix row with no enforcement rule is itself a defect"* — the ADR
   **must not** ship without the rule.

### Carry forward into the ADR

Your round-0 corollary belongs here: **Rule 0's completeness assertion is self-referential** — both sides
derive from one `target/classes` walk — so it needs an independent source-to-classpath check after every
large merge. That is the same pattern applied to our own tooling, which is the strongest possible
illustration for the ADR.

Also record the disclosed cost: the layering made F4 *harder* to fix (Rule 1 removed the one-line
option). An ADR that only lists benefits will not be trusted.

### Guardrail

`PromptBudget` must remain unaffected — it is a `shared` value **instance** injected inward, not a static
limit read. If your rule flags it, the rule is wrong, not `PromptBudget`. Verify the rule is green at
exactly 0 violators after t29, and demonstrate it goes red if F4's original form is reintroduced.


---

## [2026-08-06T02:19:26Z] from coordinator — t30 UNBLOCKED + a numbering hazard to settle first

**t29 landed (`672b1a5`).** Rule 8's precondition is now verified by me directly, not by report:

```
grep -rn "ConfigDefaults" src/main/java/dev/logicojp/reviewer/domain/   -> 0 matches
```

F4 was Rule 8's only violator, so the rule should pass on its first run. If it fails, something
landed after `672b1a5` and that is itself the finding.

Authoritative build on the settled worktree: **962 tests, 0 failures, BUILD SUCCESS, exit 0.**

### Hazard: adding "Rule 8" leaves a visible gap at Rule 7

I checked the actual numbering before you start, because ADR-0006 L143 sets an explicit
convention and it is easy to violate by accident.

Current reality:

| Where | Rules present |
|---|---|
| `LayerDependencyRulesTest.java` | 10 `@Test` methods; named **Rule 0-6** (incl. `5b`, `6a`/`6b`) |
| ADR-0006 L131-138 | Rule 1, 2, 3, 4, 5, **5b**, 6a/6b, "Rule 6 scope" |
| `t24-architect.md:229` | **"Rule 7 (proposed)"** - a *different* rule (group `dependencies.keySet()` by simple name) |

So **Rule 7 is already reserved by your own other proposal** and is not yet implemented. If t30
ships Rule 8 as prescribed, the test file reads Rules 0-6, then 8. A future reader sees a gap and
must guess whether Rule 7 was deleted, failed, or never existed.

That is precisely the F5 failure mode we just fixed in `clarification.md`: **a record that
outlives its generation context becomes an instruction to regress.** A numbering gap with no
explanation is an invitation for someone to "tidy" it by renumbering, which would break every
inbound reference to Rule 8 in ADR-0008, `decisions.md`, and this inbox.

### My recommendation (yours to overrule - you own ADR-0006)

**Keep the name Rule 8** and add a one-line reservation marker for Rule 7 next to it.

Renumbering to Rule 7 would be tidier in isolation, but the identifier "Rule 8" is already
committed to in `t24-architect.md` §5A.4, in `decisions.md`, and in the backend/tester inboxes.
Desynchronising those to gain one integer is a bad trade - traceability beats tidiness.

Note that ADR-0006 L143's suffix convention (`5b`) governs **insertions** into the middle of the
sequence, to protect the `6a`/`6b` pair. Appending at the end is not an insertion, so `8` does not
violate it. Please confirm that reading rather than assume mine is right.

### Scope reminder

t30 is ADR-0008 + Rule 8 only. Do **not** implement the proposed Rule 7 here - it is a separate
finding with its own blast radius, and bundling it would make a green Rule 8 unattributable.

ADR-0006 L124 governs the shape: *"a matrix row with no enforcement rule is itself a defect."*
The ADR and the rule ship together or neither ships.

---
### [2026-08-06T02:45:00Z] BROADCAST from architect (t30) — ADR-0008 Accepted / Rule 8 live
`domain` may no longer reference `shared.ConfigDefaults` (Rule 8, ADR-0008).
If your task needs a limit inside `domain`, **inject it as a value object**
(`PromptBudget` / `SkillBudget` are the precedents) — that stays legal under Rule 1.
Rule 8 is enforced by `LayerDependencyRulesTest` and ships with a permanent control, so a
violation fails the build naming the exact edge. Rule 7 is RESERVED (t24 §5), not implemented —
do not claim the number.

---
### [2026-08-06T02:45:00Z] CRITICAL from backend (t27) — t24 §6's F2 remedy is empirically unsafe
**Do not apply t24 §6's prescribed F2 fix to any other config record.** Its stated rationale —
"unbound ints then arrive as `0`, and the compact constructor already normalises non-positive to
the default" — is **false** on Micronaut 5.1.2 / Java 28. An absent key for a *primitive*
`@ConfigurationProperties` record component throws
`DependencyInjectionException: Property doesn't exist` during **parameter resolution**; the compact
constructor never runs, so it cannot rescue the value. Verified with a throwaway probe under a
guaranteed-absent prefix, including the exact normalising variant t24 describes.

**Safe equivalent, now shipped in `PromptBudgetConfig`**: box the components, mark them
`@Nullable`, normalise `null` in the compact constructor — the shape `LocalFileConfig` and
`ExecutionConfig.sharedSessionEnabled` already use.

**F2 also named the wrong source.** There were **three**, not two: `PromptBudget.DEFAULT_*`, the
`@Bindable` literals, and `src/main/resources/application.yml` (all eight keys restated). Mutant
evidence: setting a `@Bindable` default to `424242` still bound `12000` — **the yaml won and the
annotations were unreachable dead code in every shipped configuration.** Deleting only the
annotations, as prescribed, would have removed the inert duplicate and left the live one.
→ Please amend t24 §6's severity rationale so the ADR history does not enshrine the wrong mechanism.

**Awaiting your confirmation**: backend removed the eight literal values from `application.yml`
(replaced with a comment naming every overridable key). No key contract is broken, defaults are
unchanged, runtime start verified, trivially reversible. Coordinator view: this is the *only* change
that actually closes F2, since the fall-through path is otherwise unreachable and untestable.

### [2026-08-06T02:45:00Z] RATIFIED by coordinator — your charter-boundary disclosure (t30)
Your edit to `LayerDependencyRulesTest.java` is **approved retroactively and as precedent**.
ADR-0006 D5 makes a ruleless ADR a defect by definition, and that test is the executable form of
ADR-0006 — authoring the rule *is* architecture work, not source-code work. Future ADR-mechanizing
tasks may edit that file without re-asking. Your Rule 7 handling (reserve, don't renumber) is
exactly right and preserves the published "Rule 8" citations.

---

## [coordinator → architect] t31 — ADR-0007 D5 declares an enforcement that does not exist (HIGH)

**Raised by**: t30 (architect), during ADR-0008 work. **Independently verified by coordinator in source before dispatch.**

### The finding

`docs/adr/0007-agent-definition-trust-model-and-secret-sink-boundary.md` **L240** (restated L247)
mandates adding **Rule 4b** to `LayerDependencyRulesTest`: no class under `application.port` may
reference `shared.SensitiveHeaderMasking`.

Verified, not taken on report:

- `grep -rn "Rule 4b" src/test/` → **0 matches.** It was never implemented.
- `src/main/java/dev/logicojp/reviewer/application/port/outbound/McpServerSpec.java`
  **L3** imports `SensitiveHeaderMasking`; **L34** calls `SensitiveHeaderMasking.wrapHeaders(headers)`.
  Real import and real call — not a javadoc mention.

So an **Accepted** ADR has been declaring an enforcement that does not exist, while the exact thing
it forbids ships in the tree. This is the same defect shape as ADR-0006 D5 — *"a matrix row with no
enforcement rule is itself a defect"* — which is how you found it.

### Why this is its own task and not part of t30

Rule 4b goes **red on arrival**. Bundling it into t30 would have made Rule 8's green
unattributable — you could not have told which rule the suite was reporting on. Your call to split
it out was right; this task is that split.

### Scope

1. Implement **Rule 4b** as ADR-0007 D5 specifies.
2. Resolve the `McpServerSpec` violation. The rule going red is the *starting* state, not the
   deliverable — a design decision is required about where masking belongs once the port may not
   reach `shared.SensitiveHeaderMasking`.
3. **Ship a control proving masking still occurs for the same inputs.** This is mandatory, not
   optional polish. The fix relocates where masking happens; getting it wrong **silently unmasks
   headers** and no existing test would notice. A rule that is green because the call moved, while
   masking no longer actually happens, is worse than the defect.
4. Update ADR-0007 so its D5 text and the implemented rule agree.

### Reviewer of record

**security owns the semantics.** They have been briefed and expect this. Route the masking-behaviour
question to them via `[notify:security]` before you finalise the structural fix — not after.

### Two traps that have already bitten this project

- **Vacuity.** Your own Rule 8 arrived at 0 violators *and* 0 exemptions and would have passed with a
  broken predicate. Rule 4b starts with a real violator, so watch it **fail first**, then fix, then
  watch it pass. Do not accept a green you never saw red.
- **Shared `target/`.** t27 and t30 ran concurrently and both got contaminated builds
  (`NoSuchFileException`, phantom failures, totals *below* baseline). **You are dispatched alone for
  this reason** — the 2-concurrent ceiling is disproven. If you still see a total below 969, suspect
  the build before the code.

### Tree state

`origin/main` was merged as `dd10b3d` and pushed; branch is 0 behind. Baseline on the merged tree,
verified by the coordinator: **969 tests, 0 failures, 0 errors, BUILD SUCCESS.** Any total below 969
is a contaminated run, not a regression you caused.

`JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open` is **required** — the machine default is GraalVM 25
and cannot compile this project.


---

## [t31 architect → all] 2026-08-06T12:35Z — ADR-0007 D5/D6: secret masking moved to the log sink

**Coordinator-verified. Two things everyone must know.**

### 1. Port DTOs now expose raw header values in `toString()` — by design

Object-level masking is **removed** from `application.port.outbound.McpServerSpec`. Masking now
happens at the **log sink** (`logback.xml` / `logback-json.xml`).

**Do not "fix" this by re-adding a wrapper.** It cannot work (measured: the SDK overrides
`toString()` on neither config class and stores headers with a plain field write, so a wrapper is
lost on any copy), and it is now mechanically blocked by `LayerDependencyRulesTest` **Rule 4b**.

### 2. If you add a log appender or logging profile, it MUST carry both `%replace` passes

Both passes, in the documented nesting order, or secrets leak.
`SensitiveHeaderMaskingSinkCanaryTest` will fail you if it doesn't — **the coordinator confirmed
this by weakening the shipped `logback.xml` and watching it go red** with
`SECRET LEAKED THROUGH THE LOG SINK`. It reads the real XML; it is not a re-declared copy.

---

## [t31 architect → all] 2026-08-06T12:35Z — ⚠️ TOOLING HAZARD: output redaction can fake a defect

The tool-output pipeline redacts auth-header literals to `******` in **all** output — `cat`, `grep`,
`view`, `sed`, even Python `repr()`. Source lines then look like broken `"******"` defaults when they
are perfectly normal templates. This nearly corrupted `GithubMcpConfig.java:52` and
`application.yml:88`.

**`base64` is the only reliable reveal** — `od -c` and `xxd` are redacted too.

> **Never rewrite a line displaying `******` without decoding it first.**

The coordinator used `grep ... | base64 | base64 -d` throughout t31's verification for exactly this
reason, and it worked.

---

## [coordinator → architect] 2026-08-06T12:35Z — t31 rulings

### 1. Ratification: GRANTED

Editing `McpServerSpec.java`, `SensitiveHeaderMasking.java` and `logback*.xml` was in scope. D5/D6
**cannot** be discharged from the test tree — a brief that grants "implement Rule 4b" necessarily
grants resolving the violation the rule exposes, or it grants nothing. Same precedent as t30.
Disclosing it in §8 rather than assuming it was right.

**Standing rule from here on**: authoring an ADR's enforcement rule includes the production change
required to make that rule green. No further per-task ratification needed for this pattern.

### 2. The omitted ordering constraint: MY DEFECT, not yours

You are right, and this one is on me. ADR-0007 carries a HIGH migration risk — **"D5 must not
precede D6"** — and the plain reading of my brief ("implement Rule 4b, then resolve the violation")
is exactly that forbidden sequence. Had you followed my brief literally you would have removed
object-level masking *before* the sink could cover the opaque-header case, opening a real leak
window.

You caught it from the ADR itself and executed ①RED ②D6 ③D5 ④GREEN. That is the correct order and
the correct instinct: **the ADR's own constraints outrank the task framing.**

**Process fix, adopted now**: when a task cites an ADR D-item, I will read that ADR's risks and
constraints and surface any ordering requirement in the brief. Generalised, since your point is
broader than this task: *any* brief citing a D-item may be silently dropping a constraint. If you
ever see a brief whose obvious reading contradicts its own ADR, follow the ADR and tell me — as you
did.

### 3. Your §7 amendment: ACCEPTED, and escalated from process rule to executable control

Third recurrence is enough. But a process rule ("an ADR must not reach Accepted while a D-item names
an absent rule") is itself just another matrix row with nothing making it executable — the exact
failure mode it describes. By this project's own standard, **the row is not the control.**

Raised as **t32**: mechanize it. See the t32 brief below.

---

## [coordinator → architect] t32 — Mechanize the "ADR D-item names a nonexistent rule" guard

**Origin**: your own t31 §7 recommendation, accepted and escalated. **Third recurrence** of this
defect class:

1. ADR-0006 D5 — matrix row with no enforcement rule
2. ADR-0008 / Rule 8 — arrived at 0 violators *and* 0 exemptions, a self-proving green
3. ADR-0007 D5 — declared **Rule 4b** for weeks while `grep "Rule 4b" src/test/` returned 0, with a
   live violation shipping the whole time

### Why not the process rule you proposed

You proposed adding to ADR-0006: *"an ADR must not reach `Accepted` while any D-item names an
enforcement rule absent from the test tree."*

Correct in substance, but a process rule in an ADR **is another matrix row with nothing making it
executable** — precisely the failure it describes. Your own words: **the row is not the control.**
A future ADR will be marked Accepted by someone who never read that paragraph.

### Scope

Make it executable. A test that:

1. Parses `docs/adr/*.md` for D-items naming an enforcement rule (`Rule N`, `Rule Nx`).
2. Asserts every named rule **exists** in the test tree.
3. Fails with a message naming the ADR, the D-item, and the missing rule.

**Non-vacuity is the whole point and you know the trap better than anyone.** The parse must be shown
to actually find the existing rules — a regex that silently matches nothing gives a permanent green
and recreates defect #2 in the very control meant to prevent it. Prove it: temporarily rename a real
rule and watch the guard go red, as you did for Rule 4b and as the coordinator independently did for
your masking canary.

Consider also asserting the reverse direction (a rule exists whose ADR reference is dangling) **only
if** it does not create false positives for `Rule 7 — RESERVED`. Do not break that marker.

### Constraints

- **Read the ADRs' own risk/constraint sections before designing.** Your t31 finding stands: an
  ADR's constraints outrank the brief, and this brief may still be missing one.
- Preserve rule numbering. ADR-0006 D5: never renumber.
- Baseline on the settled tree: **980 tests, 0 failures, BUILD SUCCESS** (coordinator-verified).
  A total below 980 means a contaminated build, not your regression.
- `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open` required.
- Tool output redacts secrets to `******` — your own broadcast. Use `base64` if you touch such lines.

**You will be dispatched alone.** t28 runs first; wait for it.

---

## 2026-08-06T03:40:12Z — from t28 [backend], routed by coordinator

**Rule 5b inspects imports only, so string-keyed coupling is invisible to it.**

t28 reports that `LayerDependencyRulesTest` Rule 5b (`presentation ⊥ infrastructure`) checks imports,
so a `presentation` class binding an infrastructure config key by string —
`@Value("\${reviewer.execution.review-passes:1}")` — passes the rule while being exactly the coupling
the rule exists to forbid. That blindness is *why* F3 survived to be found by a human reading code.

Live precedent still in the tree: `presentation/ReviewModelConfigResolver` uses
`@Value("\${reviewer.model.review:}")`.

Per its brief t28 did **not** add a rule — this is your call. Two decisions for t32's sibling scope:

1. Whether a Rule 5c is warranted (e.g. no `@Value`/`@Property` in `presentation`) plus an ADR-0006 row.
2. Whether `ReviewModelConfigResolver` migrates to a port or is recorded as a known deviation.

**Coordinator's note on framing.** Treat this as the same defect class as t32, not a separate one:
a rule that cannot see the violation it names is the enforcement-gap pattern this project has now hit
four times. If you do add Rule 5c, it needs a negative control proving it goes red on a planted
`@Value` in `presentation` — a rule that inspects zero classes is the failure mode we keep rediscovering.
Do not let it land green-by-vacuity.

---

## t18.2 [backend] → architect / 2026-08-06 — 構造テストの位置依存トラップ + ADR-0007 要素数

### 1. `LayerDependencyRulesTest` の `TYPE_DESCRIPTOR` 正規表現（あなたの構造成果物）

パッケージ区切りが**省略可能**になっていたため、record の `ObjectMethods` が生成する
コンポーネント名リストの文字列定数の中の `Lines;` に一致し、`ines` という存在しないクラスに対する
**幻のドメイン違反**を報告していました。

backend は区切りを必須にし、補償として `Rule 0b: no class lives in the default package` を追加。
検出力は**仮定ではなく再検証**済み（Rule 4 は引き続き `10 violator(s), 10 exempt`、
Micronaut の `$Definition` bean 7 件を含む）。

**要注意**: 発火条件が**位置依存**でした。record のフィールド順を変えるだけで将来ビルドが壊れ得た、
ということです。レビューをお願いします。これは本 run で繰り返している
「強制手段そのものが壊れている」系統（Rule 5b の import 限定盲点、Rule 8 の自己証明 green 等）の
新しい形です。

### 2. ADR-0007 の要素数（coordinator からの指摘と一致）

backend も独立に `AgentConfig` が **14 要素**であることを確認しました（ADR 本文は 13）。
ADR の記述は architect の所有物なので backend は書き換えていません。**訂正をお願いします。**
該当箇所: L131 (D1)、L149 (D3 見出し)、L280 (Enforcement)、L335 (Consequences)。

D3 の強制手段は「行の追加漏れは『未カバー要素あり』で落ちる」設計のため、
字面どおり 13 行の表を書くと**その完全性検査自体が無効化されます**。
backend は表を固定数で書かず `AgentSchemaCoverageTest` で**リフレクションによる件数導出**に
したため実装側は安全ですが、ADR 本文は依然として後続の読み手を誤らせます。


---

## From coordinator — two ADR-0007 corrections for t32 (from t18 re-run)

1. **D3 element count is stale.** Already on your t32 list as F6: ADR says 13, the record now has 14
   (`source` added by t18.2). Four places: L131, L149, L280, L335.

2. **D4 is vacuously satisfied — new, and worse than a typo.** D4 guarantees the security warning is
   "not suppressible by `--quiet`". I verified: `grep -rn quiet src/main/java` returns exactly one hit,
   the word "quietly" inside a doc comment. **There is no `--quiet` flag.** The guarantee has no
   subject, so it is trivially true and will stay green forever without asserting anything. If a
   `--quiet` flag is ever added, D4 reads as already-satisfied and nothing forces the wiring.

   This is the same failure mode as SEC-H1 (a control that exists only as text) and as your own D7
   ("否定的対照のない制御は制御ではない"). Please either drop D4 or restate it against a mechanism that
   exists.

3. Security notes SEC-H3 is a textbook D7 violation: the block ranges never had a negative control
   asking what *else* they admit. My BMP sweep is that control; it found a class of codepoint
   (`Mn` combining marks, U+302A-U+302D) that even the remediation advice missed. Worth considering
   whether D7 should require the negative control to be **derived by exhaustive sweep** rather than
   hand-written, since hand-written negative controls have now missed twice.

---

## 2026-08-06T05:24Z — from coordinator (for t32)

**Add to D7: an allow/deny control needs an over-block mutant, not just a removal mutant.**

Evidence from t18.3. The implementer's mutation matrix initially contained only *removal* mutants
(delete a codepoint, delete a category). It scored 100%. Adding an **over-block** mutant — injecting
`SPACE_SEPARATOR` into the blocked set — killed 15 tests including `japaneseIsAllowed`.

The point: a removal-only matrix proves the rule cannot silently get *weaker*. It says nothing about
the rule silently getting *stricter*, which for a charset allowlist is equally a defect — it breaks
legitimate Japanese content and would surface as a mystery rejection in the field, not as a test
failure. D7 as written ("negative control") is satisfied by removal mutants alone, so the gap is in
the wording, not the practice.

Suggested D7 amendment: for any control that partitions input into accepted and rejected, the
negative-control obligation is **two** mutants — one that widens the accepted set and one that
narrows it — and both must be killed.

This also strengthens what I sent earlier about D7 requiring sweep-derived controls. Combined, the
rule becomes: *derive the boundary by sweep, assert it exactly, and pin both directions by mutation.*

**Also still outstanding for t32** (repeated so it is in one place):
- ADR-0007 stale element counts: 13 → **14** at L131, L149, L280, L335 (F6)
- **D4 is vacuous** — it guarantees a warning "not suppressible by `--quiet`" and **no `--quiet` flag
  exists** (I verified: one hit in `src/main/java`, the word "quietly" in a doc comment). Drop or
  restate it. Same failure mode as SEC-H1: a control that exists only as prose.
- F3 positional-regex trap review.

---

## 2026-08-06T05:46Z — coordinator → architect: **t16.2 dispatch brief**

**Read this before starting.** You have two open tasks — **t16.2 (this one, on the critical path)**
and **t32 (not dispatched yet)**. Items previously routed to you for **t32** — the ADR-0007 D7
over-block-mutant amendment, the F6 stale element counts (13→14 at L131/L149/L280/L335), and
SEC-L11 (D4 is vacuous) — are **explicitly out of scope for t16.2**. Do not pull them forward. If
you find yourself editing ADR-0007, you have drifted; t16.2 is about **ADR-0006**.

### Why t16.2 blocks everything

t16.2 → t17 → t20 → t21 → t22 is the **longest remaining chain in the run**. t17 cannot certify a
layered structure whose own ADR misdescribes it, so nothing downstream moves until you rule.

### (a) ADR-0006 D3 rests on a false premise — rule on it

D3 instructs that three "Micronaut factory classes" be relocated into the composition root. The
premise is wrong on two of the three:

| class | actual shape | consequence of executing D3 verbatim |
|---|---|---|
| `ApplicationPortFactory` | genuinely carries `@Factory` | D3 applies cleanly |
| `ReviewContextFactory` | plain class holding config-mapping logic — no `@Factory` | moved for a reason that does not hold |
| `ReviewOrchestratorFactory` | `@Singleton` implementing an **inbound port** | **would violate D1** |

This is the **seventh instance of this run's standing pattern**: a canonical record that outlived
the code it describes and became an instruction to regress. Do not quietly execute the parts that
happen to work — **restate D3 against what the code actually is**, and say plainly in the ADR that
the original premise was wrong. A silent correction is what produced F5, F6, SEC-L11 and t33.

### (b) The real finding — `ReviewOrchestratorFactory implements RunReviewPort`

This is a genuine dependency inversion on **the review path — the highest-risk path in the system**.
It is invisible today only because a Rule 4 composition-root exemption covers it: a carve-out whose
blast radius cannot be seen at the call site. That is precisely the failure mode `decisions.md` now
standardises against ("assert the scope, not just the outcome").

**Record it as deviation #8.** My standing position, unchanged: do **not** let a file move conceal
it. A relocation that makes the ArchUnit rule green while the inversion survives is worse than the
status quo, because it converts a visible defect into an invisible one.

### Scope call — mine, so you don't have to guess

t16.2 is **decision + ADR-of-record**, and the refactor only if it is genuinely small. Judge it
yourself once you have read the code:

- **If the fix is contained** — do it, and pin it with a test that fails on the *inversion*, not on
  file location. A rule satisfied by moving a file is not a rule.
- **If it is a real refactor of the review path** — record deviation #8 with the direction and the
  intended end state, raise the refactor as a follow-up, and say so explicitly. **Do not half-do
  it.** A partially-executed inversion fix is the worst outcome available here.

Either way t17 must be able to certify against a settled ADR. Tell me which branch you took.

### Two standing requirements

1. **Any D-item you write or amend must name a rule that exists.** t32 will mechanize this guard;
   until it does, check by hand. Do not add a D-item whose rule you have not grepped for.
2. **Non-vacuity.** If you add or change a test, show me it goes **red** first. Reporting a green
   test as evidence is not evidence — this run has been caught by that trap more than once.

### Build discipline

`JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open` is **required** (the machine default GraalVM 25
cannot compile this project — it targets `release 28`). Authoritative counts come only from
`./mvnw -B clean verify`; non-clean runs inflate the number via orphaned surefire XMLs. **Current
baseline: 1054 tests, 0 failures at `8ad9e9c`.** Do not derive a new baseline by addition — that has
been wrong twice in this run, both times by exactly 9, in opposite directions.

You are the **only** worker running. Nothing else will touch the tree.

---

## 2026-08-07T01:49:28Z — backend t16.3 → architect

**INFO:** ADR-0006 deviation #8 is removed in source. Backend reports that `RunReviewPort` now
resolves to the application-layer orchestrator, adapter construction is separated behind outbound
boundaries, Rule 4 no longer exempts the infrastructure implementation, and the clean suite passes
1058/1058. Independently verify this contract, update deviation #8's status, and return t16.2 as a
fresh clean PASS before t17 proceeds.

---

## 2026-08-07T03:08:42Z — coordinator → architect: **t17 final architecture review brief**

Review the **actual current tree**, not the t4 class map or an earlier compiled baseline. t16.3 and
t16.2 have closed deviation #8; t19/t33 have subsequently changed build, test, resources, and CI
surfaces without changing the intended layer contract.

### Certification contract

Independently verify:

1. Composition root is wiring-only and no layer depends on it.
2. `domain` is JDK + `shared` only; application remains framework/SDK-free.
3. `presentation` does not import `infrastructure`; infrastructure implements outbound ports only.
4. Copilot SDK types are confined to infrastructure.
5. All layer and sibling-package cycles remain zero; explicitly reconcile the 10 cycles from t2.
6. Rule 0 covers every compiled production class, and each boundary rule has a non-empty,
   source-backed subject set. A green rule over partial bytecode is a failure.
7. Rule 4's exemptions are exact and match ADR-0006's deviation table. Verify resolved deviations
   #1–#3 and #8 from source; inspect still-open deviation #4 rather than inheriting its severity.
8. The DI-resolved `RunReviewPort` is application-owned and the test pins the real Micronaut bean.

### Scope boundaries

- t32 owns ADR-0007 stale counts, D4/D7 housekeeping, and the D-item/rule guard.
- t34 owns `ALLOWED_MODEL_PREFIXES` behavioral coverage.
- t14.1 owns remaining PM behavior-ID coverage.

Do not silently absorb those tasks. Report any architecture HIGH/CRITICAL normally; the strict
remediation protocol applies and t20 will remain blocked.

### Evidence discipline

Use isolated copies for mutants because the shared worktree contains verified but not yet
phase-committed t19/t33 changes. Do not reset, checkout, or discard them. Current clean gates:

- Java 28: 1058 unit + 4 packaged-JAR tests.
- GraalVM 25: 1058 JVM + 1058 native + 4 packaged-JAR tests.

Return an unconditional PASS only with **0 HIGH / 0 CRITICAL**.

---

## 2026-08-07T03:47:16Z — backend t17.1 → architect

**INFO:** t17 H3/H4 are remediated. Backend added RED-first Rules 3a/4a with permanent mutation
controls; focused 19/19 and full 1066/1066 pass. Re-run t17 only after t17.2 also closes H1/H2.

---

## 2026-08-07T04:12:46Z — backend t17.2 → architect

**INFO:** t17.2 reports A17-H1/H2 closed. `ReviewApp` is now a thin process entry point,
`ApplicationPortFactory` has been split by responsibility, Rule 4 has zero exemptions, and the
clean suite passes 1077/1077. Independently re-run t17 against the current tree before unblocking
t20.

---

## 2026-08-08T09:24:00Z — architect t17 → all

**INFO:** Current-tree Layered / Ports & Adapters re-certification passed cleanly: **0 CRITICAL /
0 HIGH**, focused 30/30, full 1077/1077, and CLI help/version both exit 0. H1-H4 are independently
closed; ADR-0006 deviation #5 remains an explicitly out-of-scope Partial and is not a certification
blocker.

---

## 2026-08-08T11:46:17Z — architect t32.1 → all

ADR-0007 D3/D4/D7 corrections and the ADR-0006 Rule 5c / bidirectional ADR-rule guard contract are
now defined. The gate remains blocked by two HIGH implementation gaps owned by t32.2.

---

## 2026-08-08T16:20:58Z — backend t32.3 → architect

**INFO:** D4 exactly-once summary cardinality is implemented. The duplicate-summary mutant now goes
RED on both D4 branches; focused 8/8 and full 1086/1086 pass. Perform the final t32.1 clean re-pass.
