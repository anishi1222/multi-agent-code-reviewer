# t26 — F1 Remediation (HIGH): Negative Control for the Cumulative Assigned-Skill Budget + Ruling on One-Knob-Three-Budgets

**Role:** backend · **Classification:** brownfield-rewrite · **Phase:** Upstream Merge
**Verdict:** ✅ A delivered (control now verified) · ⚖️ B ruled — **one knob may NOT govern these budgets**; partial fix landed, remainder escalated.

---

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t24-architect.md` — source of finding **F1 (HIGH)**; §6 defines F1/F2/F3, §3 #3 confirms `enforceAssignedSkillBudget` is correctly placed in `infrastructure/parsing` (a resource-limit control, not a trust control, so ADR-0007 D2 does not claim it).
- `.github/modernize/rearchitecture/artifacts/t23-backend.md` — established that the assigned-skill budget arrived via the upstream merge, not via new authorship.
- `.github/modernize/rearchitecture/artifacts/t23-backend-validation.md` — prior validation baseline (939 tests) used as the regression reference.
- `.github/modernize/rearchitecture/team/backend/inbox.md` — coordinator brief defining deliverables A and B and the completion criteria.
- `.github/modernize/rearchitecture/clarification.md` (clarification/v1, canonical scenario record) — §Backend "API contract preservation" and §Generic "Out of scope" independently govern the ruling in §B; §Generic "Existing test posture: must pass" and the `backend.nfr.sla` default ("no regression") govern the acceptance bar. **Added to this task's dependencies after initial delivery; consumed and reconciled in full — see §F.**
- `docs/adr/0007-agent-definition-trust-model-and-secret-sink-boundary.md` §D7 — the governing rule: *「否定的対照のない制御は、制御ではない」*.

## Evidence Mapping

| Upstream item | This task's output / evidence |
|---|---|
| `t24-architect.md` §6 **F1 — unverified control at `AgentConfigLoader.java:189`** | `AgentConfigLoaderTest.AssignedSkillBudget` (3 tests) + **two-mutation kill matrix** below → control is now verified, F1 closable |
| `t24-architect.md` §3 #3 (placement is correct) | Accepted; no code was moved. Fix is confined to naming/provenance inside the existing class |
| ADR-0007 **D7** (negative control required) | Mutation testing proves each test fails when its target control is removed — passing tests alone would not have satisfied D7 |
| Coordinator brief **task B** (one knob, three budgets) | §B ruling below, with the correction that it is **five** sites, not three |
| `t23-backend.md` (upstream provenance) | Re-confirmed: `enforceAssignedSkillBudget` exists in `origin/main` and is absent at merge-base `fb2e795c` — inherited gap, **not introduced by t23** |
| `clarification.md` §Backend — *"`application.yml` keys may break only when justified by architecture quality, with ADR and migration notes"* | **Corroborates §B.5**: adding new configured budget keys is a contract change requiring an ADR → escalation to architect was mandated by the canonical record, not merely my judgement |
| `clarification.md` §Generic — *"Out of scope: gratuitous CLI/config breaking changes"* | The landed fix (§B.4) adds **zero** config keys — a pure internal field rename → compliant |
| `clarification.md` §Generic — *"Out of scope: dependency/runtime version upgrades"* | No version changed by t26. Independently verified that the in-tree Java bump came from **upstream**, not from any worker — see §F |
| `clarification.md` §Generic — *"Existing test posture: must pass"* | 942/942 pass, 0 failures/errors/skipped, exit 0 |
| `clarification.md` gap `backend.nfr.sla` — *"match current production baseline; no regression"* | Landed change is **bit-identical** in behaviour (all three fields hold the same value; `git diff` is rename-only) → no regression. F4 is a **pre-existing** inherited risk, not one introduced here |
| `clarification.md` §Generic success definition — *deps inward only, zero cycles, ArchUnit-enforced* | Changes confined to `infrastructure/parsing` + `shared`; no dependency direction altered; **15/15 arch rules pass, 0 cycles** |

---

## A. Negative Control for the Cumulative Assigned-Skill Budget

### A.1 Why the control was previously unreachable by any test

`enforceAssignedSkillBudget` (L204) caps the **cumulative** `name + description + prompt` length of the skills whose `metadata.agent` matches the agent. Before this task, no test could reach that branch. This is provable, not assumed:

- `isSafeSkill` rejects a skill when `injectedContent.length() > budget`, where
  `injectedContent = String.join("\n", name, description, prompt)` → length `= skillLength + 2`.
- So every skill that survives to the budget loop satisfies `skillLength ≤ budget − 2`.
- At the loop's first iteration `assignedPromptLength == 0`, so `skillLength > budget` is **impossible**.

⇒ **The cumulative branch is reachable only with ≥ 2 assigned skills.** The pre-existing `rejectsOversizedSkillFile` test uses a single skill with no `metadata.agent` and no frontmatter, so it trips the per-file gate and never enters the branch at all.

### A.2 What was added

`AgentConfigLoaderTest` → new `@Nested class AssignedSkillBudget` (3 tests). `BUDGET = 1_000`, three skills of exactly `SKILL_LENGTH = 400` (`400 + 400 ≤ 1000 < 1200`). Content is **ASCII-only by design**, so `bytes == chars` and the test is not itself contaminated by the unit conflation described in §B.

| Test | Asserts |
|---|---|
| `dropsAssignedSkillOnceCumulativeBudgetIsExceeded` | survivors are exactly `[skill-a, skill-b]` — the third is dropped although each is individually well within budget |
| `identicalSkillIsAcceptedAloneButDroppedAfterOthers` | a **byte-identical** `skill-c` at the **same budget** survives alone but is dropped when preceded by a/b |
| `skillsWithoutAgentMetadataAreNotSubjectToTheCumulativeBudget` | matched pair with test 1 — identical sizes and budget, **only `metadata.agent` removed** → all three survive |

### A.3 Attributing the failure to L207 and nothing else

Two independent mechanisms pin the drop to the cumulative branch:

1. **In-test negative control on the sibling gates.** `assertPerFileGatesCannotFire` re-parses every generated file with the production `SkillMarkdownParser` and asserts `Files.size(f) ≤ BUDGET` **and** `String.join("\n", name, description, prompt).length() ≤ BUDGET` — i.e. it evaluates the *actual predicates* of the per-file byte gate and the per-skill content gate and shows both are false. No other branch can drop the skill.
2. **Order sensitivity.** Test 2 is the airtight form: the per-file gates are pure functions of `(file, budget)`, so a byte-identical file under an identical budget *cannot* produce different outcomes. A differing outcome is only possible if the decision depends on **previously accepted skills** — which is the definition of the cumulative branch.

### A.4 D7 compliance — mutation kill matrix

Passing tests do not satisfy D7. Each control was disabled in turn and the suite re-run:

| Mutation applied to `AgentConfigLoader` | test 1 | test 2 | test 3 |
|---|---|---|---|
| **M1** — cumulative branch disabled (`if (false && …)` at L207) | **FAIL** ✅ | **FAIL** ✅ | pass *(expected)* |
| **M2** — `metadata.agent` guard disabled (L199) | pass *(expected)* | pass *(expected)* | **FAIL** ✅ |

Every test kills at least one mutant, and the two mutants are killed by **disjoint** tests — so no test is vacuous and each control has its own dedicated negative control. Both mutations were reverted; `git diff` confirms no `false &&` marker remains.

---

## B. Ruling — may one knob govern these budgets?

### B.1 Ruling

> **No.** `skillConfig.maxParameterValueLength()` must not remain the single governing value for these budgets. However, the **full** remediation is a user-facing configuration contract change and is escalated rather than performed here; what landed in t26 is the provenance-restoring subset that is behaviour-identical.

### B.2 Correction to the brief: it is **five** sites, not three

| # | Site | Quantity measured | Unit | Budget source | On breach |
|---|---|---|---|---|---|
| 1 | `SkillDefinition:54` | one substituted MCP parameter value | chars | method arg | **throws** `IllegalArgumentException` |
| 2 | `AgentConfigLoader` file gate | one skill file on disk | **bytes** | configured | skip + warn |
| 3 | `AgentConfigLoader` content gate | one skill's injected content | chars | configured | skip + warn |
| 4 | `AgentConfigLoader` assigned total | cumulative content per agent | chars | configured | skip + warn |
| 5 | `AgentPromptBuilder:145` | **rendered** "Assigned Review Skills" section | chars | **hardcoded constant** | **throws** `IllegalStateException` |

### B.3 Evidence

1. **Five quantities, one number.** Two of them abort the run; three degrade gracefully. A single knob cannot express both risk appetites.
2. **Unit conflation (latent, not active — verified).** Site 2 compares `Files.size()` in **bytes** against the same integer sites 3/4 compare **UTF-16 chars** against. I measured all shipped skills rather than assuming: **27 of them have `bytes != chars`**, with divergence up to **2×** (`vuejs3-review`: 2 921 bytes / 1 457 chars). But **zero** files currently fall in the mis-gated window (`bytes > 10 000 ≥ chars`), so today this is a **latent** defect. A Japanese-heavy skill of ~5 000 chars would be silently dropped at roughly half the nominal budget.
3. **Site 4 systematically under-counts site 5 over the identical skill set.** Both filter on exactly `metadata.agent == agentName` (`AgentPromptBuilder:127` ≡ `AgentConfigLoader:199`). Site 4 sums raw `name+description+prompt`; site 5 measures the rendered section, which adds **71 chars of fixed header + 10 chars per skill** (both figures computed from the literals, not estimated), plus an unbounded placeholder-expansion delta. So a skill set whose raw sum lands within `[budget − 71 − 10n − Δ, budget]` **passes the loader and then throws in the builder**.
4. **Site 5 ignores configuration entirely** — see F4 below.
5. **Name/doc contradiction.** The field is `maxParameterValueLength` (site 1's meaning) while `ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH`'s javadoc documented site 5's meaning. Neither described sites 2–4.
6. **The alias erased provenance.** `AgentConfigLoader` collapsed the knob into one field `maxSkillPromptLength` used by three different gates, so no call site declared which budget it applied. This is the **8th** instance of this run's systemic pattern — *a control's scope of application is invisible at the call site* — matching `ApplicationPortFactory:54-60` from t18.1.

### B.4 What landed in t26 (in-charter, zero behaviour change)

- `AgentConfigLoader`: the single `maxSkillPromptLength` alias is replaced by three explicitly-named, unit-bearing, documented fields — `maxSkillFileBytes`, `maxSkillContentChars`, `maxAssignedSkillTotalChars` — all still initialised from the one knob, with a comment stating they measure different quantities in different units and are **not interchangeable**. Each gate now names the budget it enforces. Behaviour is bit-identical (all three hold the same value).
- `ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH`: javadoc corrected from a single claimed meaning to the full five-consumer table, explicitly flagging the bytes-vs-chars split and the site-5 override gap.

### B.5 Deliberately **not** done (with reasons)

- **New YAML keys / separate configured budgets** — a user-facing configuration contract change requiring an ADR + migration notes. Escalated to architect. **Independently mandated** by `clarification.md` §Backend, which permits `application.yml` keys to break *"only when justified by architecture quality, with ADR and migration notes"*, and by §Generic, which puts *"gratuitous CLI/config breaking changes"* out of scope.
- **Adding fields to the `SkillConfig` record** — breaks 7 constructor call sites and introduces new config surface that would itself require D7 negative controls. Scope creep at phase 10/11.
- **A test asserting the site-5 `IllegalStateException`** — that would lock a defect in as expected behaviour.

---

## C. Proposed new finding — F4 (HIGH): raising the knob converts a graceful skip into a hard crash

`AgentPromptBuilder:145` compares against `ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH` **directly**, ignoring the configured `reviewer.skills.max-parameter-value-length`. The loader gates honour the configured value. Therefore:

> An operator who sees `Assigned review skill budget exceeded … skipping skill` and does the natural thing — **raise the configured limit** — moves the loader's ceiling above the builder's fixed one. Skills that were previously *skipped with a warning* are now *admitted*, and `AgentPromptBuilder` **throws `IllegalStateException`**, aborting that agent's review entirely.

The remedy makes the failure mode strictly worse. The crash window is not the ~71-char markup gap — it is *everything above 10 000 chars*, unbounded. Severity **HIGH** (availability; triggered by the documented remediation path for a warning users will actually see).

Live corroboration from this build: `.github/skills/java-add-graalvm-native-image-support/SKILL.md` (12 908 bytes) is **already being dropped** at the default budget during every test run, so operators have a real, present incentive to raise the knob.

## D. Correctly-characterised non-finding (LOW / informational)

Skills **without** `metadata.agent` bypass the cumulative budget (added unconditionally at L200). This is **not** a security bypass and should not be reported as one: `config.skills()` has exactly **one** consumer, `AgentPromptBuilder:126`, which filters to `metadata.agent == config.name()`. Unassigned skills are loaded, carried in memory, then discarded at the only point of use. Classify as **dead weight (LOW)**, not a bypass. Test 3 pins this behaviour so a future "fix" cannot silently change it.

## E. Provenance note

`enforceAssignedSkillBudget` is present in `origin/main` and **absent at merge-base `fb2e795c`**. It is an **inherited upstream gap** faithfully ported by t23 — t23 did not introduce it, and F1 is not a t23 regression.

## F. Reconciliation against `clarification.md` — plus one cross-cutting finding

`clarification.md` was added to this task's dependency list after initial delivery. It was consumed in full and every constraint reconciled against the delivered work (see the Evidence Mapping rows above). **No deliverable required revision** — the record *strengthens* the §B ruling rather than altering it, because it independently mandates the ADR route that §B.5 had already chosen on charter grounds.

One discrepancy did surface, and it is **not** a t26 issue:

> **F5 (MEDIUM, process) — `clarification.md` is stale on the target runtime and should be regenerated.**
> The record states the target framework is **"Java 26 (GraalVM 26 EA)"**. The actual `pom.xml` declares `<java.version>28</java.version>` with `<release>${java.version}</release>`.

Provenance was verified rather than assumed:

| Ref | `<java.version>` |
|---|---|
| merge-base `fb2e795c` | **27** |
| `origin/main` | **28** |
| current worktree | **28** |
| `clarification.md` (generated 2026-08-05) | **26** |

The 27 → 28 bump is commit `98b095c` *"fix: update Java version from 27 to 28 in pom.xml"*, authored by the **repository owner on 2026-07-21**, confirmed an **ancestor of `origin/main`**, touching `pom.xml` only. Therefore:

- ✅ **No worker violated** the *"Out of scope: dependency/runtime version upgrades"* constraint. The drift entered legitimately through the Upstream Merge phase.
- ⚠️ **The canonical record is stale.** Its own header says *"DO NOT EDIT manually. Re-run the Clarification Gate if inputs change."* The inputs **have** changed. I did **not** edit it — regeneration is the coordinator's call.
- ⚠️ **This is actively hazardous.** A downstream worker that trusts the "Java 26" line and "corrects" the pom toward it would **downgrade** the runtime — a real regression, and itself a violation of the out-of-scope rule. Verifying against the pom is what prevented that here: t26's own verification build ran under JDK 28, matching `<release>28</release>`. Building to the stated "Java 26" would have failed.

---

## Test Results

- **Command:** `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify` (unpiped; exit code captured on the same line)
- **Passed:** 942 · **Failed:** 0 · **Errors:** 0 · **Skipped:** 0
- **Exit code:** 0 — `BUILD SUCCESS`
- **Architecture rules:** 15/15 pass, 0 cycles across 5 layers
- **Delta vs t23 baseline:** 939 → 942 = **+3**, reconciled against Maven's console `Tests run:` line and matching exactly the 3 `@Test` methods added (per the `surefire-declared-vs-actual-test-counts` learning, surefire XML root attributes are not used for this reconciliation).
- **Mutation verification:** 2 mutants injected, both killed, both reverted (see §A.4).

## Files Changed

| File | Change |
|---|---|
| `src/test/java/.../parsing/AgentConfigLoaderTest.java` | **+3 tests** (`@Nested AssignedSkillBudget`) + in-test negative control helper |
| `src/main/java/.../parsing/AgentConfigLoader.java` | alias split into 3 named unit-bearing fields; gates renamed; no behaviour change |
| `src/main/java/.../shared/ConfigDefaults.java` | javadoc corrected to the five-consumer reality |

## Handoff

- **architect** — please rule on F4 and on the escalated items in §B.5 (separate configured budgets, bytes-vs-chars semantics, making site 4 a true pre-check for site 5). F4 needs a severity assignment and an ADR if the config surface changes.
- **coordinator** — F1 is closable. §B is ruled, not deferred; the escalated remainder is scoped and justified above.
