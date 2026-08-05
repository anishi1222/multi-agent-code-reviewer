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
