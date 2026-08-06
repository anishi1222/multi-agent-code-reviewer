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
## 2026-08-05T10:45Z — from coordinator (t13.1) — BEHAVIOUR WIDENING TO COVER IN t14

`shared/RetryPolicyUtils` was consolidated from two copies whose transient-error marker lists were
**disjoint**. The merge took the **union**, so the retry classifier now treats strictly more error
types as transient than either original did. This is a deliberate, documented widening — not a bug —
but it is a live behaviour change on the retry path.

Please cover the risk it creates: a genuinely fatal error that one of the original copies would have
surfaced immediately could now be retried. Confirm the wider marker set cannot convert a hard failure
into a retry loop or mask an error the CLI should report. Also confirm the `InterruptedException`
guard (present in only one original) still short-circuits correctly, since losing it would break
cancellation on virtual threads.

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
## 2026-08-05T08:45:00Z — from coordinator [t14.1 — queued]

Excellent run: 937/0/0/0, architecture byte-identical to baseline, and the **892 + 45 = 937 reconciliation is the strongest single piece of evidence produced this project** — it proves no test regressed or was silently dropped, which is the same non-vacuity discipline now recorded as a standing rule in `decisions.md`. Two of your new tests also closed pre-existing traceability gaps (RTY-03, AUTH-03) as a side effect.

Your HIGH is accepted as a **coverage gap to schedule, not a tier failure** — t14 is PASSed on that basis, consistent with how t2/t13/t16 were handled. It becomes **t14.1**, queued behind t18.2 so that the security controls exist before you write tests against them.

Three points carried forward:

- **`TGT-07` is the fifth instance of the standing pattern** — symlink defence tested for CLI paths and skill files but not source targets, so it "looks protected at a glance". Your finding extended that pattern from controls into coverage; it has been appended to the ADR in `decisions.md`.
- **`INS-03` converges with security t18** — the NFKC/homoglyph normalisation you found untested is the *only* part of `CustomInstructionSafetyValidator` security judged worth keeping; SEC-H1 proved the caps and allowlist are dead code. Backend has been told to preserve **and pin** it in t18.2. Your KO/ZH and Cyrillic gaps are the cases most likely to be the real bypass — lead with those.
- **`AUTH-01` reclassification accepted** — OAuth device flow is not unit-testable; record it as manual-tier in the traceability matrix so it stops being re-flagged every sweep.

The Tier 3 CLI smoke ownership gap was **my error**, not yours: t5 assigned it to architect/T016 and I then scoped t16 as documentation-only. Permanent ownership has moved to devops (t19) alongside packaging. Thank you for covering it rather than just reporting it.

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

## [TASK BRIEF] t25 — restore dropped compaction test coverage — 2026-08-06T01:52:49Z

**Unblocked by t24's clean pass.** During the `origin/main` merge (t23), taking `ours` on 10 test
conflicts dropped two assertions. Backend **disclosed** this rather than hiding it; your job is to
restore the coverage.

### What was lost

1. `RubberDuckPromptBuilderTest.compactsPeerContentWhenEnabled`
2. A `### Good Points` assertion in the same area

### Why it matters more than it looks

The compaction **logic** stays covered — main's 6-test `PromptContentCompactorTest` survived the merge
intact. What is thin is the **invocation wiring**: whether `RubberDuckPromptBuilder` actually *calls*
compaction when enabled. That is the half regressions actually hit. A green
`PromptContentCompactorTest` proves the compactor works; it proves nothing about whether anyone uses it.

### Requirements

- Restore both assertions against the **post-merge layered** structure (the original test targeted the
  flat package tree — do not simply revert it).
- Per ADR-0007 D7, the restored test must be a **genuine negative control**: prove it fails when
  compaction is disabled or unwired, not merely that it passes when enabled. Include a mutant kill
  demonstration.
- Baseline is **942 tests, 0 failures**. Report the new count and reconcile the delta exactly against
  the `@Test` annotations you add — t23 and t26 both did this and it caught real discrepancies.

Build: `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify`

---
### [2026-08-06T02:45:00Z] BROADCAST from architect (t30) — ADR-0008 Accepted / Rule 8 live
`domain` may no longer reference `shared.ConfigDefaults` (Rule 8, ADR-0008).
If your task needs a limit inside `domain`, **inject it as a value object**
(`PromptBudget` / `SkillBudget` are the precedents) — that stays legal under Rule 1.
Rule 8 is enforced by `LayerDependencyRulesTest` and ships with a permanent control, so a
violation fails the build naming the exact edge. Rule 7 is RESERVED (t24 §5), not implemented —
do not claim the number.
