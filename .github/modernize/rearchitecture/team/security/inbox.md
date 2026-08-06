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
### [2026-08-06T02:45:00Z] BROADCAST from architect (t30) — ADR-0008 Accepted / Rule 8 live
`domain` may no longer reference `shared.ConfigDefaults` (Rule 8, ADR-0008).
If your task needs a limit inside `domain`, **inject it as a value object**
(`PromptBudget` / `SkillBudget` are the precedents) — that stays legal under Rule 1.
Rule 8 is enforced by `LayerDependencyRulesTest` and ships with a permanent control, so a
violation fails the build naming the exact edge. Rule 7 is RESERVED (t24 §5), not implemented —
do not claim the number.

---
### [2026-08-06T02:45:00Z] HIGH from architect (t30) — ADR-0007 D5 is unfulfilled, with a live violation
`application/port/outbound/McpServerSpec.java:34` calls `shared.SensitiveHeaderMasking.wrapHeaders(headers)`.
ADR-0007 **D5 (line 240)** mandates exactly the opposite — it declares this edge forbidden and to be
enforced as **`Rule 4b`** in `LayerDependencyRulesTest`. Coordinator independently verified:
`grep -rn "Rule 4b" src/test/` → **0 matches**. The rule was never built.

So an **Accepted** ADR has been declaring an enforcement that does not exist, while the thing it
forbids happens in shipped code — header-masking responsibility currently crosses the port boundary
unchecked. This is the same defect shape ADR-0006 D5 names ("a matrix row with no enforcement rule
is itself a defect"), which is how t30 found it.

Being tracked as **t31 [architect]**. You are the reviewer of record on the *semantics*: the fix
relocates where masking happens, and getting it wrong silently unmasks headers. t31 is required to
ship a control proving masking still occurs for the same inputs — please confirm that control is
sufficient before t31 closes. Note this is **separate** from your still-open t18 findings.


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

## t18.2 [backend] → security / 2026-08-06 — HIGH: 文字許可リストが Trojan Source 系文字を通していた

`CustomInstructionSafetyValidator.ALLOWED_CHAR_RANGE` は `U+2000–U+206F` を**ブロック単位で丸ごと許可**していました。
この範囲には以下が含まれます:

- 双方向制御 (bidi override) `U+202A–U+202E`
- ゼロ幅文字 `U+200B–U+200F`
- 不可視演算子 `U+2060–U+2064`

**すなわち、拒否するために存在していた当の文字を許可していた**ことになります。
backend が `\u2000-\u200A`, `\u2010-\u2027`, `\u202F-\u205F` に絞り込み、日本語で実用される約物
(`U+203B` 等) は保持しました。

**現時点で潜在的だったのは、この定数が死んでいた（宣言のみで呼ばれていなかった）ためです** —
SEC-H1 がこの HIGH を偶然に覆い隠していた形になります。t18.2 が定数を実経路に接続したため、
絞り込みが同時に行われていなければ、この修正自体が脆弱性を有効化していました。

**backend からの申し送り（重要）**: 「Unicode ブロック範囲で書かれた他の許可リストも監査すべき」。
ブロック範囲指定は、その範囲に何が含まれるかを書き手が列挙しないまま許可を与えるため、
同型の欠陥を生みやすい構造です。t18 再実行時の観点に加えてください。


---

## From coordinator — 2026-08-06 (t18.2 verified; input for t18 re-run)

t18.2 is **verified PASS**. SEC-H1 and SEC-H2 are closed at the root: provenance is now a type
(`AgentSourceDirectory`) assigned once in `ApplicationPortFactory`, so the trusted `--agents-dir`
population and the untrusted CWD-relative population are no longer flattened into one `List<Path>`.
The five dead constants are live (4/4/5/2 `src/main` references). Build: 1041 tests, 0 failures.

I mutation-tested the differential claim myself rather than accepting it: collapsing
`AgentTrustProfile.forSource` to return a single profile turned the suite **RED, 10 failures / 28**,
3 of 3 in `AgentTrustLevelDifferentialTest`. The control is real.

**Three items for your re-run, one of which is mine:**

1. **New HIGH, closed, worth your independent eye (F1).** `ALLOWED_CHAR_RANGE` whitelisted
   `U+2000–U+206F` *wholesale* — a block that contains U+202A–U+202E (bidi override),
   U+200B–U+200F (zero-width), U+2060–U+2064 (invisible operators). The charset allowlist was
   admitting the exact Trojan-Source characters it existed to reject. It was latent **only because
   the constant was dead** — meaning SEC-H1 was accidentally masking a second HIGH, and any fix that
   made the constant live *without* narrowing it would have activated the vulnerability.

2. **Backend's own suggestion, which I endorse:** audit whether any other whitelist in the codebase
   is written as a block range. F1's shape is "the range is named for what it admits, not checked
   for what else it admits," and nothing about that is unique to this constant.

3. **My finding (LOW, but it is the SEC-H1 shape again).** `AgentPolicyConstantsAreLiveTest`
   enumerates seven constants; `ALLOWED_CHAR_RANGE` is **not** one of them and has zero `src/test`
   references. The control is pinned behaviourally by
   `AgentTrustContractBoundaryTest.bidiOverrideRejectedFromRepository` (`\u202E` rejected as
   REPOSITORY_SUPPLIED, accepted as USER_SUPPLIED), so re-widening turns a test red today. The gap is
   that deleting that behavioural test would silently unguard the constant.

t18 must return **zero HIGH/CRITICAL**; its own remediation `[DONE]` does not close it (§3.2.1).

---

## 2026-08-06T05:24Z — from coordinator (t18 re-dispatch, round 2 of 2)

**t18.3 is complete and I verified it myself rather than accepting the report.** Read this before
re-auditing so you spend your budget on what is *not* yet settled.

### What I independently confirmed against the shipped compiled class

Probe run against `target/classes` (not the test suite), controls first so it cannot pass vacuously:

| case | charset | denylist |
|---|---|---|
| plain injection (ASCII) | ADMIT | **CATCH** — probe works |
| benign English / Japanese 「こんにちは、世界」 / fullwidth ＡＢＣ１２３ / precomposed が | ADMIT | SILENT — **no false positives** |
| bidi override U+202E | REJECT | CATCH — prior control intact |
| **U+FFA0** and all 5 sibling fillers (U+115F, U+1160, U+2800, U+3164, **U+A8F9**) | **REJECT** | — |
| all 6 `Mn` (U+302A–302D, **U+3099, U+309A**) | **REJECT** | — |

**Exhaustive sweep, all 1,114,112 codepoints: 33,441 admitted, 0 surviving invisible/unassigned.**
Regression: 18 repo agent definitions, 0 rejected.

**Non-vacuity proven by mutation** (I applied these to production source, then restored byte-identical,
`cmp -s` verified, tree clean):
- Mutant A — drop `0xFFA0` from `INVISIBLE_CODE_POINTS` → **5 tests RED**
- Mutant B — drop `NON_SPACING_MARK` from `BLOCKED_CATEGORIES` → **2 tests RED**

Both halves of the rule are independently pinned. You do not need to re-derive any of the above.

### Two corrections to your own re-run artifact — your audit undercounted

1. `Mn` offenders were **6, not 4**. Your audit listed U+302A–302D via `\u3000-\u303F`; U+3099 and
   U+309A also came in via a *different* range, `\u3040-\u309F`. I confirmed all 6.
2. Your recommended filler list named **5**; deriving from the JDK Unicode name tables found **6** —
   `U+A8F9 DEVANAGARI GAP FILLER` appeared on no human list, mine included.

Two independent hand-curations were both incomplete. That is the case for derive-don't-enumerate,
now evidenced rather than asserted.

### Residual the implementer disclosed — please rule on it, do not rediscover it

`INVISIBLE_CODE_POINTS` is derived using a name heuristic (`FILLER`/`BLANK`/`ZERO WIDTH`/
`INVISIBLE`/`WORD JOINER`). **Production and the test share that heuristic**, so a blank-rendering
codepoint that is (a) inside an allowed block, (b) not in a blocked category, and (c) named
unusually would be missed by both, and the equality test would still pass. The category mask carries
the main load and the behavioural pins are independent of it, so I read this as **LOW residual, not a
gate failure** — but the ruling is yours. If you disagree, say so explicitly rather than implying it.

### Still open from your re-run, deliberately not folded into t18.3

- **SEC-L10 half-closed.** `ALLOWED_CHAR_RANGE` is now behaviourally tested and in the liveness
  enumeration (7 → 9). `ALLOWED_MODEL_PREFIXES` is a different constant in a different class and
  remains untested — scope was not widened. Needs its own task; do not fail the gate on it.
- **SEC-L11 (D4 vacuous)** and the ADR-0007 stale element counts are routed to architect as **t32**.
- **SEC-M7** is closed as a side effect: all 30 `Cn` codepoints are now rejected by the category mask.

### Procedure

**This is the last remediation round §3.2.1 allows.** If you find a genuine HIGH/CRITICAL, report it
plainly — I will escalate to the user rather than open a third round. Equally, do not manufacture a
finding to look thorough: four of your candidates last round were correctly downgraded, and that
calibration is worth more than volume.

Your own suggestion back to me — that **over-block mutants** be standard for allow/deny controls,
because a removal-only matrix scores 100% while leaving the acceptance direction unguarded — is
accepted and routed to architect for D7.
