# t24 — Post-merge Architecture Conformance Re-check

**Verdict: PASS — the staged merge may be committed as-is.**

Findings: **0 CRITICAL, 1 HIGH, 2 MEDIUM**. None is a layering violation; none blocks the commit.
All three are follow-up work, itemised in §6.

| Decision | Verdict |
|---|---|
| 3-A `reviewPasses` / `sharedSessionEnabled` | **KEEP** — the question's premise is false (§2) |
| 3-B #1 `PromptBudget` split | **CONFIRM** (+ MEDIUM finding F2) |
| 3-B #2 `SKILL_MAX_PARAMETER_VALUE_LENGTH` → `shared/ConfigDefaults` | **CONFIRM** — exemplary D6 |
| 3-B #3 `enforceAssignedSkillBudget` → `infrastructure/parsing` | **CONFIRM placement** (+ HIGH finding F1) |
| 3-B #4 `PromptContentCompactor` → `shared` | **CONFIRM** — verified pure |
| 3-C Rule 0 scope | **PASS — parsed 331/331** (§4) |
| 3-D ADR-0006 amendment for `shared/` value carriers | **Not required for the placements**; a *different* amendment is recommended (§5) |

---

## 1. Build evidence

Command (as mandated, unpiped, exit code captured on the same line):

```
JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify > /tmp/t24-build.log 2>&1; echo $?
```

- `MAVEN_EXIT_CODE=0`, `BUILD SUCCESS`
- `Tests run: 939, Failures: 0, Errors: 0, Skipped: 0`
- Merge state at time of check: `MERGE_HEAD=5844456`, `HEAD=d3a499c`, staged and **not committed** — unchanged by me.

A green build is not evidence the architecture survived; §3–§4 are.

## 2. Decision 3-A — KEEP (the premise is false)

The item was posed as "a multi-pass capability with no YAML surface and no test exercising
`reviewPasses > 1`". **Both halves are false.** Ruling the premise first, per the D3 precedent:

**`sharedSessionEnabled` is live user-facing functionality**, not dormant config:

| Surface | Evidence |
|---|---|
| CLI flag | `presentation/parser/ReviewOptionsParser.java:211` — `case "--no-shared-session"` |
| Documented help | `presentation/CliUsage.java:48` |
| **Inbound port contract** | `application/port/inbound/ReviewRequest.java:41` — `boolean noSharedSession` |
| Domain state | `domain/review/ReviewContext.java:27` |
| Tests | 5 files, incl. `ReviewPassRunnerTest`, `ReviewContextTest`, `ReviewRunRequestFactoryTest` |

Removing it would be a **CLI breaking change** and would delete a field from an inbound port DTO.
Per `clarification.md`, CLI options break only with an ADR plus migration notes. Nothing here justifies that.

**`reviewPasses` is both surfaced and tested:**

- YAML surface exists: `infrastructure/config/ExecutionConfig.java:24`,
  `@Bindable(defaultValue = "1") int reviewPasses` inside `@ConfigurationProperties("concurrency")`
  nested in `@ConfigurationProperties("reviewer.execution")` → bindable key
  `reviewer.execution.concurrency.review-passes`. Absence from the *shipped* `application.yml`
  is not absence of a surface — Micronaut binds the key from user YAML, env, or system properties regardless.
- Three tests exercise `> 1`: `ReviewExecutionModeRunnerTest:37` (`.reviewPasses(2)`),
  `ReviewResultPipelineTest:92` (`finalizeResults(…, 2)` — the test t23 restored),
  `ReviewPassRunnerTest:36` (`containsExactly(1, 2, 3)`).

**Ruling: KEEP.** These are preserved capabilities, not dead code. Backend's asymmetry argument
(keeping is widening; defaults `1`/`true` are behaviourally identical to `main`) holds, and the
surface/test evidence makes it moot. No follow-up deletion task is warranted.

While verifying this I found **F3** (§6) — a real config-key defect in the same feature.

## 3. Decision 3-B — four placement calls

Authority: ADR-0006 §2 import matrix. The `shared` row reads
「層をまたぐ純粋ユーティリティ・定数」 — *cross-layer pure utilities and **constants*** — which is
broader than D6's defaults-ownership clause and is the governing text for #1, #2 and #4.

### #1 `shared/PromptBudget` + `infrastructure/config/PromptBudgetConfig` — CONFIRM

Correct, and the only shape expressible: `domain` prompt builders read the budgets, and Rule 1
forbids `domain` from seeing Micronaut types. The split is exactly D6 bullet 2 (binder in
`infrastructure.config`, values in `shared`). `PromptBudget` is a pure record of eight
`DEFAULT_*` constants with non-positive normalisation — squarely "constants" under the §2 row.
Distinct simple names (`PromptBudget` vs `PromptBudgetConfig`) avoid the D6 bullet-3 trap.

Verified every consumer goes through `toPromptBudget()`; no accessor is read directly.

→ but see **F2**: the `@Bindable` literals re-declare the same defaults.

### #2 `SKILL_MAX_PARAMETER_VALUE_LENGTH` → `shared/ConfigDefaults` — CONFIRM

The hoist was *required*: `domain/agent/AgentPromptBuilder:145` needs the constant and `domain`
may not import `infrastructure`. This is D4 in action (a purity-displaced capability returning legitimately).

The delegation is **exemplary** and is the pattern F2 should adopt:

```java
// infrastructure/config/SkillConfig.java:22
public static final int DEFAULT_MAX_PARAMETER_VALUE_LENGTH = ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH;
```

A compile-time constant reference — one source of truth, drift structurally impossible.

### #3 `enforceAssignedSkillBudget` → `infrastructure/parsing/AgentConfigLoader` — CONFIRM placement

Placement is right. The decisive test is ADR-0007 D2 (`AgentDefinitionPolicy` is the *sole* owner
of trust policy): does this control branch on trust level? **It does not** — it applies a uniform
size budget regardless of `AgentSource`. It is a *resource-limit* control, not a trust-level
control, so D2's monopoly does not claim it, and an input-sizing control belongs at the adapter
boundary alongside the sibling `firstSuspiciousField` rejection. Its shape also matches ADR-0007
D4 exactly: skip the offending skill, continue the loop, emit a warning.

→ but the control is unverified. See **F1 (HIGH)**.

### #4 `shared/PromptContentCompactor` — CONFIRM (checked hardest, as instructed)

Verified directly rather than trusting Rule 2's aggregate:

- **Imports**: I dumped every `^import` across all 15 files in `shared/` — the union is
  `java.*` only. `PromptContentCompactor` itself declares **zero imports**.
- **Signature purity**: `compact(String, int)`, `compactKeepingTail(String, int)`,
  `compactSourceBlocks(String, int)` — no domain vocabulary crosses the boundary.
- **Genuinely cross-layer**: consumed by `application/review/LocalSourcePrecomputer` **and** three
  `domain` classes (`RubberDuckPromptBuilder`, `SynthesisStrategy`, `SummaryPromptBuilder`).
- **Consistent with established precedent**: `shared` already houses equally algorithmic pure
  mechanisms — `CircuitBreaker`, `RetryExecutor`, `PlaceholderUtils`, `TokenHashUtils`.

I considered and rejected the alternative that it belongs in `domain`: `application → domain` is
legal, so `domain` would also satisfy both consumers. It stays in `shared` because it carries no
business vocabulary — it is a character-budget string mechanism, not a review rule. That said,
the *criterion* I just applied is nowhere written down; that is the substance of §5.

Rule 2 corroborates: 34 classes inspected, **0 violators, 0 exempt** (exact-match semantics, so an
empty exemption set is a real assertion, not an unchecked default).

## 4. Decision 3-C — Rule 0 scope: PASS, parsed **331/331**

**The number requested: `[arch] Rule 0: parsed 331/331 classes`.**

Layer census from the post-merge build:

```
domain 58 · application 52 · infrastructure 115 · presentation 69 · shared 34   = 328
root: ReviewApp, $ReviewApp$Definition, ReviewApp$GlobalOptions                 = 3
                                                                          total  331
```

The arithmetic reconciles exactly; no class is unaccounted for. All other rules were green with
exact-match exemption sets (Rule 1 `0/0`, Rule 2 `0/0`, Rule 3 `2 violators/2 exempt` = the two
composition-root classes, Rule 4 `9/9` = 3 named factories + 6 derived bean definitions,
Rule 5 `0/0`, **Rule 5b `0/0` — the merge introduced no `presentation → infrastructure` edge**,
Rule 6a/6b no cycles, Rule 6-scope full coverage).

**However, Rule 0 alone cannot answer 3-C.** `analyseBytecode()`
(`LayerDependencyRulesTest.java:103–125`) computes `classFilesOnDisk` by walking `target/classes`,
then parses *those same files* into `dependencies`; Rule 0 asserts the two are equal. **Both sides
derive from one walk.** It therefore proves "the parser saw everything that was compiled" — never
"everything in source was compiled". A merge-added source file that failed to reach
`target/classes` would shrink both sides together and stay green while escaping every layer rule.
That is this run's standing pattern — *a control's scope of application is invisible at the call
site* — and it is precisely the t12 failure mode. The five named anchors mitigate but do not close
it, since none of them is a merge-added file.

So I asserted the scope independently:

| Check | Result |
|---|---|
| Every `src/main/java/**/*.java` has a matching `.class` | **175/175, 0 missing** |
| Every merge-added/modified main source has a matching `.class` | **28/28, 0 missing** |

The 28 span all five layers (`domain` 11, `infrastructure` 7, `presentation` 4, `application` 3,
`shared` 3). Rule 0's denominator therefore genuinely covers the merge-added surface.

**3-C: PASS.** The architecture rules did apply to the merged code — verified, not assumed.

## 5. Decision 3-D — no amendment needed for the placements; a different one is recommended

**Do the placements need ADR-0006 amended? No.** The §2 matrix row already sanctions `shared` as
「層をまたぐ純粋ユーティリティ・定数」. `ConfigDefaults` and `PromptBudget` are constants carriers;
`PromptContentCompactor` is a pure utility. All three are within the row as written. D6 is narrower
(defaults ownership) but does not contradict it. No text change is required to legitimise t23's work.

I did not build on D3, per instruction.

**What is actually missing is enforcement, in two places** — both instances of ADR-0006's own
principle at line 124: *"a matrix row with no enforcement rule is itself a defect."*

**(a) `shared` has a purity rule but no membership criterion.** Rule 2 constrains what `shared`
may *import*; nothing constrains what may *live* there. Any logic at all — including business
rules — can be parked in `shared` provided it imports only `java.*`, and every rule stays green.
`shared` has the strictest purity requirement and the weakest natural resistance to accretion.
I applied an unwritten criterion in §3 #4; it should be written down. Suggested D6 addendum:

> `shared` に置いてよいのは、**2 層以上から参照され、業務語彙を持たない**純粋な機構・定数に限る。
> 単一層からしか参照されない型は、その層に置く。

**(b) D6 bullet 3 states a convention with no rule behind it.** It requires simple class names to
be unique under `dev.logicojp.reviewer` and even notes this is 機械的に検査可能 — yet no rule checks
it. I verified it mechanically: **0 duplicates across 175 files**, so it holds today, and t23
deliberately protected it by naming `PromptBudget` / `PromptBudgetConfig` distinctly. But Rule 5b
exists precisely because "designed but unenforced" let two violations reach production.

This is cheap to close — no new infrastructure, reusing the keyset Rule 0 already builds:

> **Rule 7 (proposed):** group `dependencies.keySet()` by simple name (excluding generated
> `$…$Definition` types and nested types); assert every group has size 1.

Both are recommendations for a follow-up task, not merge blockers. ADRs were untouched by the
merge, so the §2 matrix and the D5 mapping table are unchanged — **D5 conformance is intact:
8 matrix rows ↔ 8 enforcement rules, no gap, and no rule broader than its row.**

## 6. Findings

**F1 — HIGH — `enforceAssignedSkillBudget` has no negative control.** *(ADR-0007 D7)*

`AgentConfigLoader:189` caps the *cumulative* prompt length of skills assigned to one agent. No test
reaches that branch. Firing it requires (i) ≥1 skill whose `metadata.agent` matches the agent name,
(ii) cumulative `name+description+prompt` over budget, (iii) each file individually under the
per-file cap. The nearest test, `rejectsOversizedSkillFile`, satisfies none: it writes a single
500-char file against a 100 budget, so it trips the *per-file* cap at line 227 and returns long
before line 189 — and its skill carries no `metadata.agent`, so the budget branch would be skipped
anyway. The per-file cap (227) and the cumulative budget (189) are different controls; only the
former is tested. ADR-0007 D7 is explicit that a control without a negative control is not a
control, and this repeats the t18/SEC-H1 shape exactly. The code looks correct — this is an
unverified control, not a known defect, which is why it does not block the merge.
**Owner: tester or backend. Fix: one test asserting an over-budget assigned skill is dropped
while the under-budget ones survive.**

**F2 — MEDIUM — `PromptBudgetConfig` re-declares its defaults, contra D6.** *(ADR-0006 D6 bullet 2)*

D6 requires `infrastructure.config` to reference `shared` and **not redefine defaults**. The no-arg
constructor complies (`PromptBudget.DEFAULT_*`), but the eight
`@Bindable(defaultValue = "12000")` annotations restate the same numbers as string literals — a
second source of truth. All eight currently match, so there is no live defect. The drift mechanism
is live though: change `PromptBudget.DEFAULT_PEER_CONTENT_MAX_CHARS` and the `@Bindable` default
silently wins at runtime, while unit tests calling `new PromptBudget()` see the new value — green
build, divergent production behaviour. `PromptBudgetConfigTest:37` looks like it guards this but
pins only the Java-constructor path, never the Micronaut binding path (the systemic pattern again).
**Fix (mechanical, and behaviour-preserving): delete the seven numeric `@Bindable` defaults.**
Unbound ints then arrive as `0`, and `PromptBudget`'s compact constructor already normalises
non-positive to the default — identical results, one source of truth. Safe because no consumer
reads the config accessors directly; all go through `toPromptBudget()`. Contrast `SkillConfig:22`,
which already does this correctly.

**F3 — MEDIUM — `reviewPasses` banner reads a different config key than the executor.**

`presentation/formatter/ReviewOutputFormatter.java:26` reads
`@Value("${reviewer.execution.review-passes:1}")`, but the bound key is
`reviewer.execution.**concurrency**.review-passes` (`ExecutionConfig.ConcurrencySettings`).
Two different keys: setting the real one runs N passes but never prints the
`"Review passes: N per agent"` banner (line 49–50); setting the formatter's key prints the banner
while still running one pass. Architecturally this is `presentation` reaching past the port
boundary to bind an infrastructure config key by string, instead of receiving the value through the
inbound port — which is how the two drifted apart. **Not merge-introduced**: `ReviewOutputFormatter`
is untouched by the merge and `origin/main` has no `review-passes` key at all. Reported because it
is a live user-visible defect in the very capability 3-A asked me to rule on, and because the fix
is a layering fix. **Owner: backend, follow-up task.**

I made **no source changes**. F2 and F3 are both small, but each requires a design choice
(F2: drop the literals vs. introduce `String` constants; F3: correct the key vs. route the value
through the port), and my charter limits me to design and review output. Escalated rather than fixed.

## 7. Explicitly not reported

Per the task's out-of-scope list, and confirmed still out of scope: `pom-native.xml` not compiling
at HEAD; `default-shade` config-only in `pom.xml`; the disclosed test-coverage reduction from the
10 `ours` test conflicts; `presentation/CliSecurityAudit`'s deliberate `org.slf4j.MDC` import;
ADR-0006 D3's false premise and Deviation #8 (t16.2); the `UD` deletion ruling.

## 8. Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t23-backend.md` — index; located the three detail files.
- `.github/modernize/rearchitecture/artifacts/t23-backend-validation.md` — build/test baseline, the
  `945 − 26 + 20 = 939` arithmetic, the layer-purity grep audit, and §"Things I inferred rather than
  verified", which directed my independent checks in §3 and §4.
- `.github/modernize/rearchitecture/artifacts/t23-backend-feature-ports.md` — the six ported
  features and the `PromptBudget` split rationale; source of the 3-B #1/#2/#4 claims I re-verified.
- `.github/modernize/rearchitecture/artifacts/t23-backend-conflict-dispositions.md` — the 82
  conflict dispositions; confirmed no disposition placed a type in a layer inconsistent with §3.
- `.github/modernize/rearchitecture/clarification.md` — the CLI-compatibility constraint that
  decided 3-A.
- `docs/adr/0006-ports-and-adapters-layering.md` — §2 matrix, D5 mapping table, D6.
- `docs/adr/0007-agent-definition-trust-model-and-secret-sink-boundary.md` — D2/D4/D7 for 3-B #3.

## 9. Evidence Mapping

| Upstream | → | This task's evidence |
|---|---|---|
| `t23-backend-validation.md` § build/test | → | §1: independently re-run, exit 0, 939/0/0 |
| `t23-backend-validation.md` § layer-purity grep audit | → | §4: Rule 1/2/5b all `0 violators, 0 exempt` on bytecode, corroborating the grep |
| `t23-backend-validation.md` § "inferred rather than verified" | → | §3 #4 (imports read directly), §4 (175/175 + 28/28 scope proof) |
| `t23-backend-feature-ports.md` § `PromptBudget` split | → | §3 #1 CONFIRM; F2 |
| `t23-backend-feature-ports.md` § `--no-shared-session` restored | → | §2: CLI surface evidence → 3-A KEEP |
| `t23-backend-feature-ports.md` § multi-pass test restored | → | §2: `ReviewResultPipelineTest:92` → 3-A premise false |
| `t23-backend-conflict-dispositions.md` § per-category policy | → | §3: no disposition contradicts the §2 matrix |
| `clarification.md` § CLI compatibility | → | §2: removal would be an unjustified breaking change |
| ADR-0006 §2 `shared` row | → | §3 #1/#2/#4 CONFIRM; §5 no amendment needed |
| ADR-0006 D5 + line 124 | → | §5: 8 rows ↔ 8 rules intact; the two enforcement gaps |
| ADR-0006 D6 | → | §3 #2 exemplary; F2 deviation; bullet 3 verified 0 duplicates |
| ADR-0007 D2 / D4 | → | §3 #3 CONFIRM placement |
| ADR-0007 D7 | → | F1 HIGH |

## 10. Test Results

- Command: `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify`
- Exit code: **0**
- Passed: **939** · Failed: **0** · Errors: **0** · Skipped: **0**
- Architecture rules: 11/11 green, every exemption set exact-matched.
