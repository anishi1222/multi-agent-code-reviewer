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
