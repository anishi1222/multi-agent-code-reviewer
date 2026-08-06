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

## architect [t16] — 2026-08-05

**Decision**: ADR-0006 `docs/adr/0006-ports-and-adapters-layering.md` is adopted as the architecture of record, establishing D1–D7. The three most consequential: **D2** — port direction is determined by *who implements it*, not by where it is filed, so an inbound port whose only implementer lives in `infrastructure` is a layer defect; **D4** — any cross-cutting capability displaced by a purity rule must return as an `application.port.outbound` port, never be silently dropped; **D5** — every allowed-imports matrix row requires exactly one enforcement rule, and new rules take a letter suffix rather than renumbering.

**Rationale**: The rewrite kept producing the same species of defect — a structural violation that survived because the enforcement layer had a hole rather than because anyone disagreed about the target shape. t12 shipped six rules that inspected 107 of 687 classes; t13.1 G1 found an unguarded presentation→infrastructure edge that two adjacent rules mentioned but neither constrained; t16 then found Rule 4 scoped to `application.port` instead of `application.port.outbound`, which is exactly why two port-direction inversions passed every build. D5 makes the matrix row, not the rule list, the thing that must be complete — a row with no rule is itself a defect. D2 gives a mechanical test for direction so it stops being a filing convention. D4 generalises the t13.1 G2 near-loss: purity rules displace capabilities, and without a standing rule the displaced capability disappears along with the tests that guarded it.

## coordinator [t16 verdict] — 2026-08-05

**Decision**: t16 is marked ✅ PASS despite reporting 4 HIGH findings, and remediation is split into a new task t16.1 (backend) which t17 now depends on.

**Rationale**: The §3.2 rule maps HIGH/CRITICAL in a `[DONE]` report to `❌ failed[findings]`. That rule targets defects *in the task's own deliverable*. t16's deliverable is documentation — ADR-0006, three READMEs at verified EN/JA parity, the ADR index, and cross-references — and it is complete and internally consistent. The four HIGHs are pre-existing code defects that the act of documenting the structure *uncovered*, in a task with no charter to fix code. This is the t2 and t13 precedent: enumerating the defect was the value delivered, not a failure to deliver. Marking it failed would penalise the behaviour that found the problem. The findings are not waived — they become t16.1, and t17 cannot certify the layering until it passes. This precedent remains confined to analysis and documentation tasks; the validation gates (t17, t18, t20, t22) keep strict §3.2.1 treatment, where a HIGH means a real defect in what that gate was asked to certify.

**Secondary note**: t16 also caught its own mid-flight staleness — t13.1 landed while ADR-0006 was being drafted and invalidated three claims (the logging port's name, the new rule's number, and two deviation statuses). A re-verification sweep before publishing corrected all three. Publishing a stale ADR would have made the architecture of record wrong on its first day.

## coordinator [t18 verdict] — 2026-08-05

**Decision**: t18 is marked `❌ failed[findings]` and will be re-dispatched, rather than passed with carry-forward remediation as t2, t13 and t16 were.

**Rationale**: The distinction recorded at the t16 verdict is load-bearing here, so it is worth stating why it cuts the other way. t2, t13 and t16 were **analysis and documentation** tasks whose HIGH findings described *pre-existing defects in the codebase they were examining* — the deliverable itself (a dependency analysis, a cleanup, an ADR) was sound, and marking it failed would have punished the task for looking carefully. t18 is a **validation gate**. Its deliverable *is* the verdict. A gate that reports 2 HIGH has, by construction, not certified anything, so passing it would record a certification that was never issued and would let t20 proceed on a security review that found unbounded untrusted input reaching an LLM. §3.2.1 applies strictly to gates for exactly this reason.

The practical consequence is not punitive: t18 touched no code and its findings are excellent. It is re-dispatched after t18.1/t18.2 so that the *clean* pass is a real artifact rather than an assumption.

**Remediation split**: SEC-H1 (dead controls) and SEC-H2 (denylist-only defence) compound — the allowlist that would bound H2 *is* H1's dead code — so they are fixed together but by different roles: architect decides the trust model (t18.1, design-only, dispatched immediately), backend implements it (t18.2, queued behind t16.1 to avoid two backend workers on one tree).

---

## coordinator [systemic] — 2026-08-05

**Decision**: Adopt as a standing project rule — **a control without a captured negative control is not a control.** Every architecture rule, security validator, masking wrapper or sanitiser added from this point must ship with a test that *fails when the control is removed or weakened*, and the non-vacuity of the control's subject set must itself be asserted.

**Rationale**: This is now the **fourth** occurrence of the same failure shape on this project, and it has cost more remediation than any other class of defect:

1. **t12** — six ArchUnit rules ran against 107 of 687 classes. The shaded ASM rejected Java 27 class files, swallowed the error, and left the rules inspecting only Micronaut synthetics. All six passed. Green build.
2. **t13.1/G1** — a `presentation → infrastructure` edge that two adjacent rules *mentioned* but neither constrained. Green build.
3. **t16** — Rule 4 scoped to `application.port` instead of `application.port.outbound`, permitting the direction inversions that t16.1 now fixes. Green build.
4. **t18/SEC-H1** — a validator declaring size caps, a line cap, a charset allowlist and a structured result, of which only the denylist is ever called. Green build.

In every case the control **read** as enforced. Code review, type checking and the test suite all passed it. The common defect is not carelessness but that *absence of enforcement is invisible* — nothing fails when a rule constrains nothing, and nothing fails when a validator validates nothing. Only an assertion that the control *can* fail distinguishes the two states.

The countermeasure has already been demonstrated to work here: t12.1's Rule 0 (`parsed == classFilesOnDisk`) turns a silently-empty subject set into a build failure; t13.1's Rule 5b shipped with a negative control; t15's CVE scan fired non-vacuity controls at rows 6 and 9 and thereby earned the right to report "0 CVEs". Each of those is the same idea applied to a different control. Elevating it from per-task practice to a recorded rule is what stops a fifth instance.

**Consequence**: t16.1 and t18.2 are both required to ship negative controls. t22 (final conformance) should verify the property holds across the enforcement surface rather than re-discovering individual gaps.

**Amendment (same day, t14)** — the count is now **five**, and the fifth instance shows the pattern is not confined to controls. t14 found that symlink-traversal defence (`TGT-07`) **is** tested for CLI paths and skill files but **not** for source review targets: in its words, "it looks protected at a glance." Here the thing that reads as enforced is a *test*, not a rule or a validator. The same invisibility applies — an untested behaviour and a tested one are indistinguishable from the outside unless coverage is asserted against a specification rather than eyeballed.

**Amendment 2 (same day, t18.1)** — the count is now **six**, and the sixth instance is the first at the *type* layer. `ApplicationPortFactory:54-60` merges trusted `--agents-dir` paths and untrusted CWD-relative defaults into one `List<Path>`; by the time `AgentConfigLoader` receives it at :62, provenance has been erased by the type. Note what this does **not** look like: the controls here are neither vacuous (t12/t13.1/t16/t18) nor untested (t14). `AgentDefinitionPolicy`'s 64 KiB cap and name-charset regex are live, applied, and would pass any test written against them. The failure is that the control **cannot receive the input it needs to decide** — it is asked "is this file acceptable?" when the only answerable question is "is this file acceptable *given where it came from*?"

This is why t18's own root cause ("the defence is denylist-only") was wrong in a way its evidence could not reveal: security read the validator and found it weak, but the validator was not the thing that failed. Strengthening it would have produced a stronger control with the same defect.

**Generalisation across all six**: the pattern is not "controls are weak." It is that a control's *scope of application* is invisible at the call site — what it parsed (t12), what edge it covers (t13.1), what package prefix it matches (t16), whether it is called at all (t18), whether it is exercised (t14), and now what it is permitted to know (t18.1). In every case the code reads as protective and the build is green. The countermeasure is unchanged and has worked every time it was applied: **assert the scope, not just the outcome** — `parsed == classFilesOnDisk`, a negative control that must fail, a differential test where the same input is accepted on one path and rejected on the other.


t14 also supplied the counter-example that proves the rule is achievable: its **892 + 45 = 937 baseline reconciliation** is a non-vacuity control applied to a test *result*, and it is what allows "937 passed" to mean something stronger than "937 tests ran". Adopt that shape — reconcile against a prior baseline — wherever a count is reported as evidence.

## architect [t18.1] — 2026-08-05

**Decision**: ADR-0007「agent 定義の信頼モデルと秘匿値シンク境界」を採択。D1 信頼レベルは `AgentSource` 型で運ぶ（`--agents-dir` = 信頼 / CWD 相対既定 = 未信頼、格上げ不可）。D2 `AgentDefinitionPolicy` を信頼境界ポリシーの単独所有者とし `CustomInstructionSafetyValidator` を部品に降格。D3 信頼レベル別スキーマ契約。D4 違反は拒否・続行・要約行必須。D5 ポート DTO はセキュリティ制御を担わず `toString()` 遮蔽は制御として不採用。D6 秘匿値の遮蔽は `infrastructure.logging`（シンク）で行う。D7 否定的対照のない制御は制御ではない。

**Rationale**: SEC-H2 を「検証器の強化」として扱うと直らない。真因は `ApplicationPortFactory:54-60` で信頼済み `--agents-dir` と未信頼の CWD 相対既定パスが同一の `List<Path>` に併合され、`AgentConfigLoader` に渡る時点で型から出自が消えていること。検証器をいくら強くしても、どのファイルに厳しい規則を当てるべきかを判断する情報が既に失われている。よって是正は型と境界の側で行う。同じ理由で D5/D6 も「ラッパーを強くする」ではなく「制御を、制御できる境界に移す」形を選んだ。

---

## coordinator [t18.1 verdict] — 2026-08-05

**Decision**: ワーカーが上流ワーカーの所見を訂正した場合、coordinator は**訂正の側を独立に検証してから**採用する。今回は 3 点（稼働中の上限値の実在、真因の所在、単位の不整合）をすべてソースで確認したうえで t18 の SEC-H2 の記述を上書きした。

**Rationale**: 下流が上流を訂正できる構造は健全だが、無検証で採用すると「最後に喋ったワーカーが正しい」になる。t9 のポート欠陥、t12 の ArchUnit 空虚、t16/t18/t14 の各所見と同様、判定の根拠はワーカーの報告ではなく coordinator 自身がソースで見た事実に置く。今回は訂正が正しかったが、訂正が誤っている可能性は同じ手順でしか排除できない。

---

## coordinator [context-file drift] — 2026-08-05

**Decision**: `.github/copilot-instructions.md` のような**エージェントのコンテキストに自動注入されるファイル**は、大規模なパッケージ再編を含むタスクの完了条件に明示的に含める。t13（レガシー削除）の完了条件に入っていなかったため、削除済みの 9 パッケージを「現在の構成」として記述したまま t14〜t18 の全ワーカーに配布され続けた。

**Rationale**: 通常のドキュメントは「読まれなければ害がない」が、自動注入されるファイルは**読まないという選択肢がない**。誤りの伝播速度が違う。実害も確認できている — この run の初期 recon で coordinator 自身が「Mustache テンプレート」と誤認したのはこのファイルが出典であり、それが `project-profile.yaml` にも複写された。t18.1 が指摘するまで 2 系統で生き延びた。

**適用**: 今後 `pom.xml` の座標変更やパッケージ再編を伴うタスクは、`.github/copilot-instructions.md` / `AGENTS.md` / `README.md` の該当節を deliverable に含めるか、明示的に別タスクへ委譲すること。

## architect [t24] — 2026-08-06

**Decision**: KEEP `reviewPasses` / `sharedSessionEnabled` even though `origin/main` deleted both.
ADR-0006 requires **no** amendment to house `shared/PromptBudget`, `shared/ConfigDefaults`,
`shared/PromptContentCompactor`.

**Rationale**: The question was posed on a false premise. `--no-shared-session` is a documented CLI flag and a
field on the inbound-port DTO `ReviewRequest`; `reviewPasses` binds `reviewer.execution.concurrency.review-passes`
and is exercised with a value >1 by three tests. So the capability was never "config-surface-less dead code" —
deleting it would have removed a documented, tested, user-facing flag. On `shared/`, ADR-0006 §2's matrix row
already sanctions "cross-layer pure utilities and constants", so the three new `shared/` members are covered by
the existing text.

**Coordinator note (process defect, recorded deliberately)**: the false premise was **mine** — it originated in the
t24 brief I wrote, which asked the architect to rule on whether a capability "with no YAML surface and no test
exercising `reviewPasses > 1`" was dead code. I asserted that as background fact without verifying it. Had the
architect answered the question as posed rather than checking its premise, we would have deleted a working CLI
flag. This is the project's own systemic pattern — *a control's scope of application is invisible at the call
site* — turned on the coordinator: **a question's premise is invisible at the point of answering it.** This is the
2nd occurrence (after ADR-0006 D3, t16.2). Countermeasure: briefs must cite evidence for load-bearing premises, or
mark them explicitly as unverified assumptions to be checked first. Captured in
`learnings/architect/rule-the-premise-before-the-question.md`.
