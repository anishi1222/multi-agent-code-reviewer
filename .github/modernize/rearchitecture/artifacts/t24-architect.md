# t24 — Post-merge Architecture Conformance Re-check

> **Round 1 (2026-08-06) supersedes round 0.** Round-0 content is retained below as the
> record of the original rulings; §1, §6, §8–§10 are revised for round 1, and §5A adds the
> round-1 rulings. Round-0 statements contradicted by round 1 are marked inline.

**Round-1 verdict: CLEAN PASS. Conformance findings: 0 CRITICAL, 0 HIGH, 3 MEDIUM.**
**The merge (`cd91bb0`) and the F1 remediation (`3ed3eda`) both stand. t25 is unblocked.**

| Item | Round-0 | Round-1 ruling |
|---|---|---|
| **F1** cumulative budget has no negative control | HIGH, open | **CLOSED** — verified in source, satisfies ADR-0007 D7 (§5A.1) |
| **F4** `AgentPromptBuilder:145` ignores the configured budget | — | **MEDIUM, not HIGH; inherited from `origin/main`, not a merge finding** (§5A.2) |
| **F2** `PromptBudgetConfig` re-declares defaults | MEDIUM | **MEDIUM — re-confirmed, unfixed, all 8 values still match** (§5A.5) |
| **F3** `reviewPasses` banner reads the wrong key | MEDIUM | **MEDIUM — re-confirmed, unfixed** (§5A.5) |
| Config-contract decision **A** — split the one knob | escalated | **Defer to a follow-up task — and the split must be *additive*, not breaking** (§5A.3) |
| Config-contract decision **B** — bytes vs chars | escalated | **Defer to a follow-up task; no ADR needed — it is not a contract change** (§5A.3) |
| Config-contract decision **C** — make site 1 a pre-check for site 5 | escalated | **REJECT the framing — fix site 5 instead** (§5A.3) |
| Promote the systemic pattern to an ADR? | asked | **YES — ADR-0008, paired with one mechanizable rule** (§5A.4) |

Round-0 decision table (unchanged, all still stand):

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
JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify > /tmp/t24r1-build.log 2>&1; echo $?
```

**Round 1 (authoritative):**

- `MAVEN_EXIT_CODE=0`, `BUILD SUCCESS`
- `Tests run: 942, Failures: 0, Errors: 0, Skipped: 0`
- **Merge state: COMMITTED.** `HEAD=3ed3eda` ("Split the aliased skill budget into named
  unit-bearing fields", t26) ← `cd91bb0` ("Merge origin/main into the layered architecture
  rebuild"). No `MERGE_HEAD`; working tree clean.
- 942 − 939 = **+3**, reconciling exactly with t26's three new `AssignedSkillBudget` tests.

> **Round-0 §1 is superseded.** It recorded `MERGE_HEAD=5844456`, `HEAD=d3a499c`, "staged and
> **not committed**". Both commits are now ancestors of HEAD. The round-0 phrase *"the staged
> merge may be committed as-is"* must be read as *"the committed merge stands"*.

Test counts are read from Maven's console `Tests run:` line, **not** summed from surefire XML
`<testsuite tests=…>` — the latter under-counts parameterized classes by ~9.

A green build is not evidence the architecture survived; §3–§4 and §5A are.

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

## 5A. Round-1 rulings

Every ruling below rests on evidence re-verified against source at `HEAD=3ed3eda`, not on the
proposing artifact's account of it. Two premises handed to me this round turned out to be false
(§5A.2) — the third and fourth time in this run, per `learnings/architect/rule-the-premise-before-the-question.md`.

### 5A.1 F1 — **CLOSED**

t26's remediation is verified in source, not merely reported.
`AgentConfigLoaderTest.java` `@Nested class AssignedSkillBudget` (`:320`), `BUDGET = 1_000`,
`SKILL_LENGTH = 400`:

| Test | Line | What it establishes |
|---|---|---|
| `dropsAssignedSkillOnceCumulativeBudgetIsExceeded` | `:404` | the cumulative branch (`AgentConfigLoader:207`) fires |
| `identicalSkillIsAcceptedAloneButDroppedAfterOthers` | `:426` | **the discriminating control** — the *same* skill is accepted alone and dropped in sequence, an outcome only accumulation can explain |
| `skillsWithoutAgentMetadataAreNotSubjectToTheCumulativeBudget` | `:452` | pins the scope boundary at `:199-202` |

The decisive element is `assertPerFileGatesCannotFire` (`:386`), which asserts each fixture's
file size **and** injected-content length are `≤ BUDGET`. That removes sites 2 and 3 as possible
explanations for the drop, so the observed behaviour is attributable to site 1 alone.

This is exactly what ADR-0007 D7 (「否定的対照のない制御は、制御ではない」) demands, and it is
strictly stronger than the round-0 requirement: the round-0 finding asked only for *a* test that
reaches the branch; t26 delivered a test that reaches the branch **and** an in-test control proving
nothing else could have caused it. Compare `rejectsOversizedSkillFile`, which round 0 showed trips
site 2 and returns long before site 1 — the new helper is precisely the guard against writing
another such test by accident.

Corroborating checks I ran independently:

- `grep -rn "false &&\|&& false" src/main/java/` → **no matches**. No mutant residue shipped.
- `git diff cd91bb0 3ed3eda --stat` → the only `src/main` files touched are `AgentConfigLoader.java`
  and `ConfigDefaults.java`. No other production code changed under cover of the fix.
- The `AgentConfigLoader` diff is **rename-only**: one field `maxSkillPromptLength` became three
  (`maxSkillFileBytes`, `maxSkillContentChars`, `maxAssignedSkillTotalChars`), all three assigned
  from the same `skillConfig.maxParameterValueLength()` at `:98-101`. Every comparison operand is
  substituted 1:1. **Behaviour is bit-identical**; the change buys provenance, not semantics —
  which is the correct scope for a fix landing after a conformance gate.

**Ruling: F1 is closed. It is removed from the finding count.**

### 5A.2 F4 — **MEDIUM, and not a finding of this gate**

Backend proposed HIGH. I confirm the defect is **real** and rule it **MEDIUM**, on four grounds
that I verified rather than accepted. I also record what F4 *does* legitimately establish, which
is more important than its severity.

**The defect, stated precisely.** `AgentPromptBuilder:145` gates the rendered "Assigned Review
Skills" section against `ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH` — a compile-time
constant of 10,000 — and **throws** `IllegalStateException` on breach. Raising
`reviewer.skills.max-parameter-value-length` in `application.yml` cannot move this ceiling.

**Ground 1 — bit-identical to `origin/main`; not merge-introduced.**

| | Expression at the gate |
|---|---|
| `origin/main` (`5844456`) `agent/AgentPromptBuilder:177` | `skillSectionLength > SkillConfig.DEFAULT_MAX_PARAMETER_VALUE_LENGTH` |
| post-merge `domain/agent/AgentPromptBuilder:145` | `skillSectionLength > ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH` |

and `SkillConfig:22` now reads
`DEFAULT_MAX_PARAMETER_VALUE_LENGTH = ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH`.
Both are compile-time constants holding 10,000; both ignore the configured value; both throw.
The restructure changed **which class declares the constant**, not the behaviour. Against
`clarification.md`'s `backend.nfr.sla` — *"match current production baseline; no regression"* —
there is no regression. Round 0 §7 already applied the "pre-existing ⇒ not a merge finding" rule
to `pom-native.xml` and `default-shade`; consistency requires the same treatment here.

**Ground 2 — the cited live trigger does not chain to the crash.** *(premise falsified)*
t26 §C cites `java-add-graalvm-native-image-support/SKILL.md` (12,908 B), dropped on every run, as
the live incentive for an operator to raise the knob. Verified:

- That drop is emitted at **site 2**, the file gate (`AgentConfigLoader:245`), whose message is
  *"Skill file exceeds maximum size (N bytes), skipping"* — **not** the *"Assigned review skill
  budget exceeded … skipping skill"* message at `:208` that t26 quotes.
- Both skills over 10,000 bytes (`java-mcp-server-generator` 22,286 B and the one cited)
  **declare no `metadata.agent`**. `AgentPromptBuilder:127` filters the section to skills whose
  `metadata.agent` equals the agent name, returning early at `:129-131` when none match. Raising
  the knob would admit these two into `config.skills()`, where `:127` filters them straight back
  out. **They can never reach site 5.**

**Ground 3 — no shipped agent is near the ceiling.** I simulated the exact production gate chain
(file gate → content gate → cumulative gate → render) over all 9 shipped agents and the 25
agent-assigned skills:

| agent | assigned | site-1 cumulative | dropped | site-5 rendered | throws? |
|---|---|---|---|---|---|
| best-practices | 7 | 3,717 | 0 | **3,858** | no |
| code-quality | 5 | 2,062 | 0 | 2,183 | no |
| performance | 4 | 1,068 | 0 | 1,179 | no |
| security | 4 | 1,090 | 0 | 1,201 | no |
| waf-* (×5) | 1 each | 1,629–1,791 | 0 | 1,710–1,872 | no |

Worst case consumes **38.6 %** of the budget — **61 % headroom**. Zero agents emit a site-1
warning today; zero agents throw at site 5. *(Correcting my own earlier reading: 25 skills **do**
carry `metadata.agent`, nested under a `metadata:` block, which a top-level `agent:` grep misses.)*

**Ground 4 — the trigger requires a bespoke authoring act.** To fire, an operator must author an
agent-assigned skill whose length lands in the narrow window where site 1 passes and site 5 fails:
`[10000 − H − Δ, 10000]`, where `H` is the fixed rendering markup — measured at **72 chars of
header + ≈10 chars per skill** (141 total for the 7-skill agent, corroborating t26's `71 + 10n`) —
and `Δ` is placeholder expansion. `Δ` is genuinely non-zero: `${repository}` occurs **24 times**
across agent-assigned skills and expands to the target name, so site 5 can exceed site 1 by an
amount no admission gate bounds. Real, but ~140 chars wide against 6,100 chars of headroom.

**Why it is still worth a finding, and what it actually proves.** Site 1 *looks* like it protects
site 5. It does not: different quantity (cumulative admitted content vs. rendered section),
different source (configured vs. hardcoded), different failure mode (skip-and-warn vs. **throw**).
That is this run's systemic pattern in its purest instance yet, and it is the reason F4 matters
more as evidence than as a bug (§5A.4).

The restructure did make one thing genuinely worse, and I record it rather than gloss it: **the
naive fix is no longer available.** `AgentPromptBuilder` is in `domain` and Rule 1 forbids it from
importing `infrastructure.config.SkillConfig`, so "just read the configured value" is not an
option. F4 is now a *design* task, not a one-line change. That cost is attributable to the
layering and belongs on the record.

**Ruling.** F4 is a **MEDIUM product defect inherited from `origin/main`**. It is **not** a merge
conformance finding: not merge-introduced, not a layering or contract violation, and unreachable
in every shipped configuration. It does **not** count toward this gate's HIGH/CRITICAL total.
It **must** be raised as a product-backlog task now, not deferred indefinitely — if any user
authors agent-assigned skills approaching the budget, this becomes HIGH with a crash, not a
degradation. Remediation direction in §5A.3-C.

### 5A.3 The three escalated config-contract decisions

These arrived as three separate questions. They are **one defect with one remedy**, and answering
them separately would produce three partial fixes. I rule them together.

#### A — split `reviewer.skills.max-parameter-value-length` into per-budget keys

**Ruling: DEFER to a follow-up task — and reject the "breaking change" framing.**

The escalation assumed this needs *"an ADR plus migration notes"* because it changes a user-facing
config contract. **It need not be breaking.** The correct shape is **additive with fallback**:

```yaml
reviewer.skills:
  max-parameter-value-length: 10000   # retains its literal meaning (site 4) and remains the default source
  max-file-bytes:            ${reviewer.skills.max-parameter-value-length}
  max-content-chars:         ${reviewer.skills.max-parameter-value-length}
  max-assigned-total-chars:  ${reviewer.skills.max-parameter-value-length}
```

No key is removed, no existing `application.yml` changes meaning, and every current deployment
behaves identically. That reduces the cost from "ADR + migration notes + breaking-change review"
to "an ADR recording the decision" — and it keeps `clarification.md`'s constraint (breaking config
changes only when justified) entirely unengaged.

Deferred rather than done now because: (i) it is not a layering or conformance issue; (ii) t26
already landed the part that mattered architecturally — three named, unit-bearing fields, so each
call site now declares which budget it applies; the residue is surface design; (iii) new config
keys need their own D7 negative controls, and adding that surface at phase 10/11 would widen the
merge's blast radius after its conformance gate and force a third round.

#### B — the bytes-vs-chars conflation

**Ruling: DEFER to a follow-up task. MEDIUM. No ADR required — this is not a contract change.**

Verified: site 2 compares `Files.size()` in **bytes** against the same integer sites 1/3/5 compare
**UTF-16 chars** against. Across the 34 shipped skills, **27 diverge**, worst ratio **2.29×**
(787 B / 343 chars), and **0 files sit in the mis-gated window** (`bytes > 10000 ≥ chars`).
So: **latent, not active**. It is also not silent — the warning at `:246` is byte-denominated and
prints the actual limit — which is why this is MEDIUM and not HIGH.

It stays MEDIUM rather than LOW because this is a Japanese-language project: a ~5,000-char
Japanese skill is rejected at roughly half the nominal budget, and the *key* the operator reads
(`max-parameter-value-length`) is documented in characters.

Recommended direction — **do not add a key**. The file gate's purpose is to avoid reading a huge
file into memory; that purpose is served by a generous constant. Make it an explicit byte budget
defaulting to a documented multiple (4× covers UTF-8 worst case) of the char budget, and let the
char gate own the semantic limit. This is behaviour-**widening** — more files are admitted, then
correctly judged by the char gate — so it is safe, and it makes the two gates non-redundant with
each one's scope visible at its own call site.

#### C — make site 1 a true pre-check for site 5

**Ruling: REJECT the framing. Fix site 5 instead.**

A pre-check that must exactly predict a downstream computation is a **duplicated invariant**:
`AgentConfigLoader` would have to track `AgentPromptBuilder`'s header text, its per-skill markup,
and its placeholder expansion forever. Worse, it makes an infrastructure class depend on a domain
class's *rendering format* — an inward-pointing knowledge leak that no import-level rule would
catch. That is the very class of coupling this run has spent nine findings removing; adopting it
would re-create the systemic pattern in a new place while claiming to fix it.

The real defect is that **four gates skip-and-warn and one throws.** Two controls over the same
resource with opposite failure modes is the inconsistency worth removing.

**Recommended remedy — one change resolving F4, decision A's motivation, and decision C together:**
give `AgentPromptBuilder` the effective budget as an **injected pure value**, exactly as
`PromptBudget` was introduced for the prompt budgets (round-0 §3 #1, already CONFIRMed), and make
its breach **graceful** — drop the overflowing skill and warn, matching ADR-0007 D4's
skip-and-warn shape — instead of aborting the agent's review. This needs no new config key, no
contract change, and it removes the `domain → ConfigDefaults` static read that Rule 8 (§5A.4)
would forbid. `PromptBudget` is the established precedent; F4 is the same problem one layer over.

### 5A.4 Promote the systemic pattern to an ADR — **YES**

*"A control's scope of application is invisible at its call site."* Nine instances are now on the
register (t12, t13.1/G1, t16, t18/SEC-H1, t14/TGT-07, t18.1, t16.2, F1, F4). Nine recurrences of
one shape is not a run of bad luck; it is an unrecorded architectural decision.

**Recommend ADR-0008**, and — per ADR-0006 line 124, *"a matrix row with no enforcement rule is
itself a defect"* — it must ship with at least one mechanizable rule, or it will be a slogan.
The general form is not mechanizable, but its sharpest instance is:

> **Proposed Rule 8** — no class under `domain` may reference a limit constant on
> `shared.ConfigDefaults`. Budgets and limits reach `domain` as injected values.

Blast radius verified: **exactly one violator today** — `AgentPromptBuilder:145`, i.e. F4 itself.
The rule would have caught F4 at the moment it was written, and adopting the §5A.3-C remedy
clears it. `PromptBudget` is unaffected: it is a `shared` value **instance** injected inward, not
a static limit read. A one-violator rule that pays for itself immediately is the right size to
introduce; a broader one would need exemptions on day one.

**Recommendation, not implementation** — ADR authorship and the rule's ArchUnit assertion are a
follow-up task for the coordinator to raise. My charter is design and review output only.

### 5A.5 F2 / F3 — severity re-confirmed at MEDIUM

Both re-verified present and unfixed at `HEAD=3ed3eda`; neither is merge-introduced.

**F2 — MEDIUM, held.** `PromptBudgetConfig:19-26` still carries eight `@Bindable` literal
defaults. I re-checked all eight against `PromptBudget.DEFAULT_*` programmatically: **all eight
match** (`false`, 12000, 6000, 50000, 1048576, 12000, 60000, 2000). No live defect; the drift
mechanism is the finding. Held at MEDIUM — promoting a latent duplication with zero current
divergence would be severity inflation.

**F3 — MEDIUM, held.** `ReviewOutputFormatter:26` still reads `reviewer.execution.review-passes`
while `ExecutionConfig`'s nested `ConcurrencySettings` binds
`reviewer.execution.concurrency.review-passes`. Live and user-visible, but the impact is confined
to a banner line; the review itself runs correctly. Not promoted to HIGH because nothing in the
review output or exit status is wrong — only a status line is missing or misleading.

Neither is fixed here: my charter is review/design output only, and each needs a design choice
(F2: delete the literals vs. introduce constants; F3: correct the key vs. route the value through
the inbound port — the latter is the layering-correct answer and the one I recommend).

### 5A.6 Re-issued merge conformance verdict

**CLEAN PASS.**

- Build: exit **0**, `BUILD SUCCESS`, **942** tests, 0 failures / 0 errors / 0 skipped.
- Architecture rules: **15/15 green**. Rule 0 parsed **331/331** classes — unchanged from round 0,
  consistent with t26 having added tests only. Rules 1, 2, 5, 5b: **0 violators, 0 exempt**.
  Rules 3 and 4: violators exactly equal to their exemption sets (2/2 and 9/9). Rules 6a/6b:
  **0 cycles** across 5 layers and all sub-packages.
- Round-0's independent scope proof still holds numerically: **175/175** source files have a
  matching `.class`; the source-file count is unchanged at 175.
- **Conformance findings: 0 CRITICAL, 0 HIGH, 3 MEDIUM** (F2, F3, and F4-as-inherited-defect).
  F1 is closed. No layering violation, no contract violation, no regression against baseline.

The gate's condition — *zero HIGH/CRITICAL* — is met. **t25 is unblocked.** No round 2 required.

---

## 6. Findings

**Round-1 status line.** F1 is **CLOSED** (§5A.1). F2 and F3 are **re-confirmed MEDIUM and still
open** (§5A.5). F4 is added as **MEDIUM, inherited from `origin/main`, excluded from the
conformance count** (§5A.2). The round-0 text below is retained verbatim as the record of how
each was originally raised.

**F1 — HIGH — `enforceAssignedSkillBudget` has no negative control.** *(ADR-0007 D7)*
> **Round-1: CLOSED by t26 (`3ed3eda`). Superseded — see §5A.1.**

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
> **Round-1: re-verified present and unfixed; all 8 literals still match. MEDIUM held — §5A.5.**

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
> **Round-1: re-verified present and unfixed. MEDIUM held — §5A.5.**

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

**Round-1 addition — F5 (raised by backend, t26 §F): not a conformance finding.**
`clarification.md` records the target as "Java 26 (GraalVM 26 EA)" while `pom.xml` declares
`<java.version>28</java.version>`. Provenance verified: merge-base = 27, `origin/main` = 28,
worktree = 28; the bump arrived via repo-owner commit `98b095c`, an ancestor of `origin/main`.
**No worker violated the no-version-upgrade rule**, and the pom is correct — the canonical record
is stale. It is nevertheless hazardous: a future worker "correcting" the pom toward 26 would
silently downgrade the toolchain. Regeneration of `clarification.md` is the coordinator's call,
not an architecture finding; escalated separately.

## 8. Upstream Artifacts Consumed

**Round 1 (new):**

- `.github/modernize/rearchitecture/artifacts/t26-backend.md` — §A F1 remediation claims
  (independently re-verified in source, §5A.1); §B/§B.2 the five-site budget table and §B.5 the
  three escalated decisions (ruled in §5A.3); §C the F4 proposal (ruled in §5A.2); §E provenance
  note (re-verified against `origin/main`); §F the F5 `clarification.md` staleness report.
- `.github/modernize/rearchitecture/team/architect/inbox.md` — the t24 re-dispatch brief
  (2026-08-06T01:40Z), which is the authoritative spec for this round's five deliverables.
- `.github/modernize/rearchitecture/clarification.md` — re-read for `backend.nfr.sla`
  ("no regression") and the config-contract constraint, both of which decided §5A.2 and §5A.3-A.

**Round 0 (retained):**

- `.github/modernize/rearchitecture/artifacts/t23-backend.md` — index; located the three detail files.
- `.github/modernize/rearchitecture/artifacts/t23-backend-validation.md` — build/test baseline, the
  `945 − 26 + 20 = 939` arithmetic, the layer-purity grep audit, and §"Things I inferred rather than
  verified", which directed my independent checks in §3 and §4.
- `.github/modernize/rearchitecture/artifacts/t23-backend-feature-ports.md` — the six ported
  features and the `PromptBudget` split rationale; source of the 3-B #1/#2/#4 claims I re-verified.
- `.github/modernize/rearchitecture/artifacts/t23-backend-conflict-dispositions.md` — the 82
  conflict dispositions; confirmed no disposition placed a type in a layer inconsistent with §3.
- `docs/adr/0006-ports-and-adapters-layering.md` — §2 matrix, D5 mapping table, D6, and line 124
  (which forces the enforcement-rule requirement in §5A.4).
- `docs/adr/0007-agent-definition-trust-model-and-secret-sink-boundary.md` — D2/D4/D7; D7 is the
  standard F1 was closed against, D4 the skip-and-warn shape recommended in §5A.3-C.

## 9. Evidence Mapping

**Round 1:**

| Upstream | → | This task's evidence |
|---|---|---|
| `t26-backend.md` §A.4 (3 tests + kill matrix) | → | §5A.1: verified in `AgentConfigLoaderTest.java` `:320/:386/:404/:426/:452`; `assertPerFileGatesCannotFire` accepted as the D7 negative control |
| `t26-backend.md` §A "pure rename" claim | → | §5A.1: `git diff cd91bb0 3ed3eda` — 1:1 operand substitution, behaviour bit-identical |
| `t26-backend.md` §C F4 proposed HIGH | → | §5A.2: **downgraded to MEDIUM** on 4 verified grounds; excluded from conformance count |
| `t26-backend.md` §C "live corroboration" (12,908 B skill) | → | §5A.2 Ground 2: **premise falsified** — that drop is site 2's byte gate, and the skill has no `metadata.agent`, so it cannot reach site 5 |
| `t26-backend.md` §B.2 five-site table | → | §5A.2/§5A.3: re-derived from source; simulated over 9 agents × 25 assigned skills (61 % headroom) |
| `t26-backend.md` §B.5 escalation #1 (split the knob) | → | §5A.3-A: **DEFER**, and re-framed as additive-with-fallback → non-breaking |
| `t26-backend.md` §B.5 escalation #2 (bytes vs chars) | → | §5A.3-B: **DEFER**, MEDIUM; 27/34 divergent, worst 2.29×, 0 mis-gated |
| `t26-backend.md` §B.5 escalation #3 (site 1 as pre-check) | → | §5A.3-C: **REJECT the framing**; fix site 5 by injection + skip-and-warn |
| `t26-backend.md` §E provenance | → | §5A.2 Ground 1: independently confirmed against `origin/main` `5844456` |
| `clarification.md` `backend.nfr.sla` "no regression" | → | §5A.2 Ground 1: F4 is bit-identical to baseline ⇒ no regression |
| `clarification.md` config-contract constraint | → | §5A.3-A: additive design leaves the constraint unengaged |
| ADR-0007 D7 | → | §5A.1: standard against which F1 was closed |
| ADR-0007 D4 (skip-and-warn) | → | §5A.3-C: prescribed failure mode for site 5 |
| ADR-0006 line 124 (row needs a rule) | → | §5A.4: ADR-0008 must ship with Rule 8; blast radius verified = 1 violator |
| round-0 §3 #1 (`PromptBudget` split, CONFIRMed) | → | §5A.3-C: the established precedent F4's remedy should follow |

**Round 0 (retained):**

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

**Round 1 (authoritative):**

- Command: `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify`
- Exit code: **0** · `BUILD SUCCESS`
- Passed: **942** · Failed: **0** · Errors: **0** · Skipped: **0**
- Delta vs round 0: **+3**, reconciling exactly with t26's three `AssignedSkillBudget` tests.
- Architecture rules: **15/15 green**; Rule 0 parsed **331/331**; every exemption set exact-matched;
  0 cycles at both layer and sub-package granularity.

**Round 0 (superseded):** exit 0, 939 / 0 / 0 / 0, 11/11 architecture rules green.

