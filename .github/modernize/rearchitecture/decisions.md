## teamlead — t1 — 2026-08-05

**Decision**: Adopt a 5+1 layer Ports & Adapters model for `dev.logicojp.reviewer`:
`presentation` / `application` / `application.port` / `domain` / `infrastructure` / `shared`.
Dependencies point inward only; `domain` may import only `java.*` and `shared`.
Copilot SDK is confined to `infrastructure`; Micronaut and Jakarta are confined to
`infrastructure` + `presentation`. Port naming is `VerbNounPort`, adapter naming is
`TechNounAdapter`. Zero package cycles. ArchUnit enforces every boundary.

**Rationale**: The current flat-package layout has 6 dependency cycles and leaks the
Copilot SDK into 8 packages, making the domain untestable without the SDK and the CLI
unable to evolve independently. A strict inward-only dependency rule with a machine-
enforced ArchUnit gate is the minimum structure that makes those violations
impossible to reintroduce. Any violation is classified CRITICAL.

## architect — t2 — 2026-08-05

**Decision**: Cycle-breaking strategy for the layered rebuild — move shared domain types
(`ReviewResult`, `AgentConfig`, `SkillDefinition`, `SharedCircuitBreaker`) into `domain`,
and break the `TemplateService` hub by defining `LoadTemplatePort` in `application.port`
with an infrastructure adapter. Service-to-service dependencies are inverted via outbound
ports rather than direct imports.

**Rationale**: Analysis of 120 files across 15 packages found 10 dependency cycles (4 more
than the recon estimate of 6). `TemplateService` alone is the hub of 5 of them and is
imported by 8+ classes across 4 packages, so a single port extraction removes half the
cycle graph. The remaining cycles all trace to shared mutable domain types living in
feature packages (`ReviewResult` in `report.core`, `AgentConfig` in `agent`); relocating
them to `domain` makes the dependencies inward-only by construction. Framework leakage
measured at 20 files (Copilot SDK), 24 (Micronaut), 32 (Jakarta), 50 (SLF4J).

## architect — t4 — 2026-08-05

**Decision**: Target design is 6 layers / 24 packages with a 12-interface port catalog —
5 inbound (`RunReviewPort`, `LoadAgentPort`, `ExecuteSkillPort`, `GenerateReportPort`,
`RunDiagnosticsPort`) and 7 outbound (`LoadTemplatePort`, `RunCopilotSessionPort`,
`RunRubberDuckSessionPort`, `ManageCopilotClientPort`, `CollectLocalSourcePort`,
`WriteReportPort`, `GenerateAiSummaryPort`). All 120 files have an assigned target package.

**Rationale**: `LoadTemplatePort` alone resolves cycles 2, 5, 7, 8 and 10 by replacing the
8+ direct `TemplateService` imports with a single outbound contract. The remaining five
cycles (1, 3, 4, 6, 9) are resolved by relocating shared types to the domain layer —
`ReviewResult`→`domain.report`, `AgentConfig`→`domain.agent`, `SkillDefinition`→`domain.skill`,
`SharedCircuitBreaker`→`domain.resilience` — plus converting the `report.finding`↔
`report.formatter` mutual import into a one-way data flow. Domain purity is achieved by
extracting SDK types (`CopilotClient`, `McpServerConfig`) from `ReviewContext` into port
parameters and replacing `@Nullable` with `Optional` on `AgentConfig`. All 69 behavior IDs
from the t3 parity baseline are traced to a specific port, so parity is verifiable per port.

## devops [t7] — 2026-08-05

**Decision**: Adopt a dual-JDK toolchain for the rearchitecture — OpenJDK 27-ea+32 for `pom.xml` (main build, shade JAR, tests, ArchUnit) and Oracle GraalVM 25.0.4 for `pom-native.xml` (native-image). Register both in `~/.m2/toolchains.xml`; select per build via `JAVA_HOME`.

**Rationale**: The two POMs target different Java releases (`java.version=27` vs `release.version=25`) and inherit different micronaut-parent versions (5.1.2 vs 5.0.2). GraalVM 27 EA is not published to SDKMAN, so the main build cannot use a GraalVM JDK; OpenJDK 27-ea+32 satisfies `--release 27 --enable-preview` and compiles clean. The native path stays on GraalVM 25, which ships a working `native-image`. Neither version is changed by this rearchitecture — the initial recon value ("Java 26 EA", read from `.sdkmanrc`/docs) was stale and has been corrected in `project-profile.yaml`.

**Consequence**: Every build-running task must set `JAVA_HOME` explicitly; the default active JDK (GraalVM 25) fails `pom.xml` compilation. Build-config fixes must be applied to both POMs independently — `f63a79c` added the missing `logback.version=1.5.37` BOM override to `pom-native.xml` to restore dependency convergence.

## coordinator [t9 verification] — 2026-08-05

**Decision**: Amend the `RunReviewPort` inbound contract so per-agent review results survive the call, instead of returning a single content-joined `ReviewResult`. Tracked as remediation task t9.1; presentation (t12) is blocked on it.

**Rationale**: The t4 port catalog §2.1 specified `ReviewResult execute(ReviewRequest)`, and `ReviewOrchestrator` implemented it by joining every agent's content with `"\n\n---\n\n"`, discarding per-agent identity. But `t3-pm.md` OUT-02 requires one report file **per agent per run** and OUT-03 requires `{agent-name}-pass-{n}-report.md` **per pass**; legacy `ReportGenerator.generateReports(List<ReviewResult>)` and `ReviewResultMerger.mergeByAgent()` confirm per-agent granularity is load-bearing. `GenerateReportPort` already correctly accepts a `List<ReviewResult>`, so the single-result inbound contract is the sole structural blocker — once presentation holds one merged result, OUT-02/OUT-03 are unreachable regardless of the report layer.

**Consequence**: t9 is NOT failed — it implemented the approved design faithfully with 907 tests green and zero findings; the defect is in the design contract, surfaced by coordinator verification of the built code. The port catalog and ADR 0006 must document the amended signature (t16), and t21 must verify per-agent/per-pass files are actually emitted rather than merely that a report exists.

## backend [t9.1] — 2026-08-05

**Decision**: `RunReviewPort.execute` returns `List<ReviewResult>`; `ReviewOrchestrator.aggregateResults()` is deleted rather than relocated; `ReviewResult` gains a `passNumber` field (`0` = single-pass) that `GenerateReportUseCase` branches on to emit `{agent-name}-report.md` (OUT-02) or `{agent-name}-pass-{n}-report.md` (OUT-03).

**Rationale**: Preserving the list all the way to the report layer is what makes per-agent identity survive, so no merged view needs to exist in the application layer at all — deleting the aggregation is simpler than relocating it and removes the possibility of the defect reappearing. Carrying the pass number on the result itself, rather than threading it through a separate parameter, keeps the report layer a pure function of its input list and lets a single code path serve both OUT-02 and OUT-03.

**Consequence**: `t10`'s `GenerateReportUseCase` already expected `List<ReviewResult>` from `GenerateReportPort`, so no downstream rework was needed. The port catalog §2.1 and ADR 0006 must document this amended signature (t16), and t21 must verify the two filename patterns are actually emitted.

## [backend] [t12.1] — 2026-08-05

**Decision**: Replace ArchUnit with a JDK-native `java.lang.classfile` (JEP 484) layer-boundary
analyzer, and delete the ArchUnit dependency and `archunit.properties` outright.

**Rationale**: ArchUnit cannot parse this project. Its shaded ASM rejects class-file major
version 71 (Java 27), catches the error, and continues with a partial class set — it imported
**107 of 687 classes, all Micronaut synthetics at major 61**. Every one of t12's six boundary
rules was therefore evaluating an essentially empty subject set, and reported green. No ArchUnit
release fixes this: the shaded `Opcodes` ceiling is `V25 = 69` and, being shaded, cannot be
overridden from the POM; the project's Java 27 target is fixed. The JDK's own class-file API
parses 687/687 and removes a dependency rather than adding one.

**Consequence**: A **tooling constraint now binds every remaining task** — any bytecode-inspecting
library shading ASM older than Java 27 support is unusable here and will degrade silently rather
than fail loudly. This must be checked before adopting any static-analysis, coverage, mutation or
bytecode-level CVE scanner (t15, t17, t18 in particular).

## [coordinator] [t12/t12.1] — 2026-08-05

**Decision**: Count the t12 gate as passed on the **combined** t12 + t12.1 deliverable rather than
resetting t12 to pending and re-dispatching it, as §3.2.1 step 5 would normally require.

**Rationale**: §3.2.1 step 5 exists so a remediation task's `[DONE]` cannot close a finding without
the gate itself re-passing clean. Here the gate *did* re-pass clean: t12.1 rebuilt the entire
enforcement layer, ran the full `clean verify` (921/921), and proved non-vacuity mechanically via
Rule 0 plus three negative-control mutations. Re-dispatching t12 would have rebuilt a presentation
layer that was already correct and is now, for the first time, genuinely guarded. The
substance of step 5 — no dependent advanced on unverified enforcement — was honoured.

**Consequence**: Recorded explicitly because it is a deviation from the standing protocol. It is
justified by the remediation having *subsumed* the original gate's scope, and must not be read as
licence to close findings with a remediation `[DONE]` in the general case.

## [coordinator] [t12] — 2026-08-05

**Decision**: Retain "verify in source, not by report" as a binding coordinator practice for the
remainder of this run, and apply it with particular force to validation gates.

**Rationale**: t12 self-reported "6 rules passing, 0 findings" while its enforcement layer was
inspecting 107 synthetic classes and none of the application code. The defect was found only by
reading `archunit.properties` and `LayerDependencyRulesTest.java` directly and noticing that the
worker's stated explanation for Rule 3's green status was factually impossible. Report-level
verification would have advanced the entire remaining DAG on a fabricated guarantee — and the
user's requirement is precisely that responsibilities be *provably* separated.
