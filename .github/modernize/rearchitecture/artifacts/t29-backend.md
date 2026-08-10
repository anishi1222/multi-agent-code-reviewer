# t29 — F4 remediation: inject the skill budget into `AgentPromptBuilder` as a pure value

**Role:** backend · **Classification:** brownfield-rewrite · **Phase:** Upstream Merge · **Status:** COMPLETE

Closes finding **F4** (t24-architect §5A.2, severity MEDIUM). `AgentPromptBuilder` — a `domain`
class — gated its rendered "Assigned Review Skills" section against the hardcoded constant
`ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH` and aborted the whole prompt build with
`IllegalStateException` on breach. Its sibling gates in `AgentConfigLoader` honour the *configured*
value and skip-and-warn. Both halves of that divergence are now removed.

---

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t24-architect.md` — §5A.2 (F4 statement + four
  verified grounds), §5A.3-C (the mandated remedy, and the explicit REJECT of the "pre-check in the
  loader" alternative), §5A.4 (proposed Rule 8 + blast radius), §6 (sequencing).
- `.github/modernize/rearchitecture/artifacts/t26-backend.md` — the `shared/PromptBudget` precedent
  this change is modelled on, and the named-unit-bearing-field discipline applied to the loader.
- `.github/modernize/rearchitecture/clarification.md` — target stack. **Note the coordinator's
  inline correction: the target is Java 28, not the stale "Java 26" in the body text.** Build was
  run on Java 28 accordingly; nothing was downgraded.
- `.github/modernize/rearchitecture/team/backend/inbox.md` (lines 1001–1054) — the t29 brief.

## Evidence Mapping

| Upstream contract | This task's output / evidence |
|---|---|
| t24 §5A.2 ground 1 — *`domain` reads a `ConfigDefaults` limit constant* | `grep -rn ConfigDefaults src/main/java/.../domain/` → **no matches**. Import removed from `AgentPromptBuilder`. |
| t24 §5A.2 ground 2 — *the configured value is ignored* | `AgentConfigLoader` field `maxRenderedSkillSectionChars` → `new SkillBudget(…)` → `AgentConfig.skillBudget()` → read in `appendAssignedSkills`. Pinned by `raisingConfiguredBudgetAdmitsPreviouslyDroppedSkill` + mutant **M2**/**M5**. |
| t24 §5A.2 ground 3 — *aborts where siblings skip-and-warn* | `IllegalStateException` for budget breach deleted; replaced by `logger.warning(...)` + `continue`. Pinned by `dropsOversizedExpandedSkillInsteadOfThrowing` + mutant **M1**. |
| t24 §5A.2 ground 4 — *one constant governs five quantities in two units* | New field is named for what it measures and its unit (`maxRenderedSkillSectionChars` / `renderedSkillSectionMaxChars`), per `learnings/backend/one-knob-many-budgets-erases-provenance`. `ConfigDefaults` consumer table updated. |
| t24 §5A.3-C — *inject as a pure value following the `PromptBudget` precedent* | `shared/SkillBudget.java`, a `record` with compact-ctor normalisation and an all-defaults no-arg ctor — structurally identical to `PromptBudget`. |
| t24 §5A.3-C — *REJECT the loader pre-check alternative* | Not implemented. The loader deliberately does **not** enforce this budget; its field javadoc says so explicitly, so a future reader does not "helpfully" add the rejected pre-check. |
| t24 §5A.4 — *Rule 8: no `domain` class may reference a limit constant on `shared.ConfigDefaults`* | Rule 8's single verified violator was F4. **Now zero violators → t30 may add the rule green.** |
| ADR-0007 D7 — *negative control with a mutant kill matrix* | §"Negative control" below: 10 mutants, 0 survivors, disjointness demonstrated. |

---

## What changed

### New — `shared/SkillBudget.java`
Pure value record `SkillBudget(int renderedSkillSectionMaxChars)`. Default sourced from
`ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH`; non-positive input normalises via
`ConfigDefaults.defaultIfNonPositive`. `shared` is the one layer both `domain` and `infrastructure`
may import, which is precisely why the value can cross the boundary here and not elsewhere.

### `domain/agent/AgentConfig.java` — the injection seam
Added `skillBudget` as a 13th record component.

**Seam chosen by measurement, not preference.** An arity scan over `new AgentConfig(` found
**70 of 71 call sites use the 8-arg convenience constructor**; the sole 12-arg call is inside
`Builder.build()`. So a 13th component + null-normalisation in the compact constructor breaks
**zero** existing call sites. Rejected alternatives:

- *Thread a parameter through `AgentPromptBuilder`'s static methods* — breaks 3 production and
  ~15 test call sites, and forces `ReviewTargetInstructionResolver` and `ReviewPassRunner` to carry
  a budget neither one uses.
- *Fold it into `PromptBudget`* — re-merges two unrelated knobs and undoes t26's provenance work.

### `domain/agent/AgentPromptBuilder.java` — the defect site
`appendAssignedSkills` rewritten from "render everything, then measure, then throw" to per-skill
fragment rendering with a cumulative budget check, `logger.warning(...)` + `continue` on overflow,
and `return instruction` unchanged when nothing fits. `renderSkill(...)` extracted;
`ASSIGNED_SKILLS_HEADER` extracted as a package-private constant.

**Byte-identity argument.** The old code computed `skillSectionLength = prompt.length() -
instruction.length()`, which equals `HEADER.length() + Σ fragmentᵢ.length()` exactly, where
`fragmentᵢ = "\n### " + name + "\n\n" + (desc.isBlank() ? "" : desc + "\n\n") + expandedPrompt + "\n"`.
The new incremental accumulation reproduces the old output byte-for-byte whenever nothing is
dropped. This is pinned by a golden-string test written out **in full** rather than rebuilt from
production constants, so header drift fails the test instead of silently tracking it.

**Reachability proven algebraically, not assumed** (per `learnings/backend/architecture-rule-negative-control`):
`ASSIGNED_SKILLS_HEADER` measures **71** chars; the original oversize input `"x".repeat(11_000)`
against instruction `"Inspect ${repository}."` yields a fragment of **11,029** chars → section
**11,100 > 10,000**. The drop branch is provably entered.

`java.util.logging.Logger` is used because it is the established in-tree idiom for `domain`
(matching `AgentDefinitionPolicy`, `AgentSectionParser`, `AgentConfigValidator`) after SLF4J was
displaced by the purity rule. **No outbound logging port was invented here — that is t16's scope.**

### `infrastructure/parsing/AgentConfigLoader.java` — where config becomes a value
Fourth named budget field `maxRenderedSkillSectionChars`; the "three budgets" provenance comment
updated to "four"; `parseAgent` now attaches `.withSkillBudget(...)` to the result of `applySkills`.

**Attach point matters.** `applySkills` early-returns when an agent has no skills
(`if (agentSkills.isEmpty()) return config;`), so attaching *inside* it would silently leave
skill-less agents with a default budget. Attaching in `parseAgent` covers both paths — and mutants
**M9**/**M10** below prove each path is independently guarded.

### `shared/ConfigDefaults.java`
Consumer table updated: the `AgentPromptBuilder` row now reads `(via SkillBudget)` / `skip + warn`,
and the stale "reads this constant directly … rejects by throwing" paragraph is gone.

---

## Negative control (ADR-0007 D7)

Ten mutants applied to restored-from-backup production sources, each run against the full suite.
**0 survivors.** `.` = mutant survives this test, `X` = this test kills it.

| test | M1 never-drop | M2 ignore-injected-budget | M3 break-not-continue | M4 emit-empty-section | M5 loader-ignores-config | M7 drop-description | M8 wrong-null-default | M9 attach-only-with-skills | M10 attach-only-when-skilless |
|---|---|---|---|---|---|---|---|---|---|
| `rendersByteIdenticalSectionWhenWithinBudget` | . | . | . | . | . | **X** | . | . | . |
| `dropsOversizedExpandedSkillInsteadOfThrowing` | X | . | . | X | . | . | . | . | . |
| `raisingConfiguredBudgetAdmitsPreviouslyDroppedSkill` | X | **X** | . | X | . | . | . | . | . |
| `budgetIsCumulativeNotPerSkill` | X | X | . | . | . | . | . | . | . |
| `continuesPastDroppedSkillSoLaterSmallerOnesStillFit` | X | X | **X** | . | . | . | . | . | . |
| `omitsSectionEntirelyWhenNoSkillFits` | X | X | . | X | . | . | . | . | . |
| `defaultsBudgetWhenConfigCarriesNone` | . | . | . | . | . | . | **X** | . | . |
| `appendsExplicitlyAssignedSkills` (pre-existing) | . | . | . | . | . | . | X | . | . |
| `configuredBudgetReachesLoadedAgentConfig` | . | . | . | . | X | . | . | . | **X** |
| `budgetTracksTheConfiguredValue` | . | . | . | . | X | . | . | . | **X** |
| `skilllessAgentsStillCarryTheBudget` | . | . | . | . | X | . | . | **X** | . |

### The result that justifies the exercise

**M2 reintroduces F4 exactly** — ignore the injected value, hardcode `10_000` — **and is NOT killed
by `dropsOversizedExpandedSkillInsteadOfThrowing`.** Predicted algebraically beforehand
(11,100 > 10,000 holds under both the fixed and the mutated code) and confirmed empirically. So the
intuitive "it drops instead of throwing" test verifies the *graceful-degradation* half of the remedy
and says **nothing** about the *configurability* half. Only
`raisingConfiguredBudgetAdmitsPreviouslyDroppedSkill` and the cumulative/order tests close that gap.
M3 and M7 likewise each have exactly one unique killer.

**M9/M10 are complementary**, killed by disjoint test sets. Together they prove the budget reaches
agents on *both* the with-skills and the skill-less loader paths — neither is accidentally covered
by the other.

### A mutant that killed too much revealed a fixture defect
M9's first run killed **all three** loader tests. That looked like a strong result; it was actually
a defect signal. All three fixtures were taking the same (skill-less) path, so **no test covered the
with-skills path at all**. The fixture was rewritten to parameterise skill presence, after which M9
kills exactly one test and M10 kills the other two. *A mutant killing more tests than its blast
radius should reach means the fixtures lack diversity, not that coverage is strong.*

---

## Test Results

- **Command:** `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify`
- **Passed:** 962
- **Failed:** 0
- **Errors:** 0
- **Skipped:** 0
- **Result:** `BUILD SUCCESS`

Architecture suite `LayerDependencyRulesTest`: **10/10 pass**, 0 cycles — including the `domain`
purity rule, which the new `domain → shared.SkillBudget` import satisfies.

Net new tests attributable to t29: **+15** (7 in `AgentPromptBuilderTest`, 6 in `SkillBudgetTest`,
3 in `AgentConfigLoaderTest`, minus the 1 deleted defect-asserting test). Targeted re-run of the
four affected suites: **58/58 pass**.

**Test removed:** `AgentPromptBuilderTest.rejectsOversizedExpandedSkillGuidance`. It asserted the
defect (that an oversized skill throws). Keeping it would have contradicted the mandated remedy.

---

## Findings

None new. F4 is closed.

## Issues / handoff

- **t30 is unblocked.** Rule 8 ("no `domain` class may reference a limit constant on
  `shared.ConfigDefaults`") had exactly one verified violator, F4. `grep -rn ConfigDefaults
  src/main/java/dev/logicojp/reviewer/domain/` now returns **no matches**, so t30 can add the rule
  and see it pass on the first run. If it fails, something landed after this task.
- **`AgentConfig` is now a 13-component record.** Anything constructing it positionally with 12 args
  must add a trailing `skillBudget` (or pass `null` to accept the default). The 8-arg convenience
  constructor — used by 70 of 71 call sites — is unaffected.
- **The loader deliberately does not enforce this budget.** t24 §5A.3-C explicitly rejected that
  design. The field javadoc records the rejection; please do not "fix" it.
- **Concurrent worktree modification observed** — see the note raised to the coordinator. Two test
  files unrelated to t29 (`RubberDuckPromptBuilderTest`, `ReviewOverallSummaryAppenderTest`) were
  modified inside this task's window by another agent. They are green and I left them untouched, but
  the 962 total includes roughly 5 tests that are not mine.
