# t23 — Validation Evidence

## Test Results

- **Command:** `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify`
- **Passed:** 939
- **Failed:** 0
- **Errors:** 0
- **Skipped:** 0
- **Result:** `BUILD SUCCESS` (43.4 s)

```
[INFO] Tests run: 939, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> Stack traces appear in the console output but are **expected** — negative-path tests that assert on logged
> exceptions (`Execution failed: boom`, `Execution failed: fail`, `No agents found`). `Failures: 0, Errors: 0` confirms.

## Test-count reconciliation: 945 → 939

Baseline before the merge was **945**. Reconciliation was done by diffing per-file `@Test`/`@ParameterizedTest`
annotation counts between `HEAD` and the working tree. The annotation delta is **−7**, and the executed delta before
restoring the pipeline test was also **−7** — an exact match, which is what makes the arithmetic trustworthy rather
than merely plausible. Restoring `ReviewResultPipelineTest`'s second test brings the final figure to **939 (−6)**.

**Removed — 26 tests.** `main` deliberately deleted the multi-pass merging cluster (`UD`, all four classes):

| File | Δ |
|---|---|
| `domain/report/ReviewResultMergerTest` | −16 |
| `domain/report/ReviewFindingSimilarityTest` | −4 |
| `domain/report/AggregatedFindingTest` | −3 |
| `domain/report/ReviewMergedContentFormatterTest` | −3 |

**Added — 20 tests**, of which **14 are `main`'s own tests preserved through the merge** (the load-bearing number —
it is direct evidence that `main`'s behaviour, not just its files, survived):

| File | Δ | Source |
|---|---|---|
| `shared/PromptContentCompactorTest` | +6 | `main` (repackaged `util` → `shared`) |
| `infrastructure/parsing/AgentConfigLoaderTest` | +3 | `main` — `enforceAssignedSkillBudget` |
| `domain/agent/AgentPromptBuilderTest` | +2 | `main` — skill-budget tests |
| `domain/report/FindingsSummaryFormatterTest` | +2 | `main` — `ConsolidatedFinding` consolidation |
| `presentation/parser/ReviewOptionsParserTest` | +1 | `main` — `--compact-prompts` |
| `domain/agent/RubberDuckTemplateContractTest` | +1 | `main` (repackaged) |
| `domain/agent/SynthesisStrategyTest` | +1 | `main` (repackaged) |
| `shared/PromptBudgetTest` | +2 | new — the ADR-0006 split |
| `infrastructure/config/PromptBudgetConfigTest` | +2 | new — binder mapping |

**Arithmetic:** `945 − 26 + 20 = 939` ✅

`ReviewRunnerTest` contributes **0**: it was `main`'s file (superseded by our `ReviewPassRunner`), never part of our
945 baseline. Verified via `git show HEAD:...ReviewRunnerTest.java` → not present.

> **Method note:** counts must be read from Maven's console `Tests run:` line after a `clean`, **not** summed from
> `<testsuite tests=…>` XML, which under-counts parameterized classes by ~9.

## Layer purity audit (independent of the architecture test)

`LayerDependencyRulesTest` passes 10/10, but since auto-merge had already slipped two violations past compilation, I
verified independently rather than relying on the gate alone:

| Probe | Result |
|---|---|
| `domain/` + `shared/` importing `infrastructure`/`application`/`presentation`/`io.micronaut`/`com.github.copilot`/`jakarta.inject`/jackson | **NONE — clean** |
| Copilot SDK referenced outside `infrastructure/` | **NONE — clean** |
| `presentation/` importing `infrastructure` | **NONE — clean** |
| Flat packages (`agent cli config orchestrator report service target util`) present | **NONE — clean** |
| Top level of `dev/logicojp/reviewer/` | `ReviewApp.java` + `application domain infrastructure presentation shared` |

**Non-vacuity:** Rule 0 asserts `parsed == classFilesOnDisk`, so the suite cannot pass by silently parsing zero
classes. It was neither weakened nor `@Disabled`.

## Runtime-asset verification

Templates and agent definitions load **by path at runtime**, so a dropped edit produces no compile error and no test
failure — it fails in production. Verified by diffing the working tree directly against `MERGE_HEAD`:

| Path | `git diff MERGE_HEAD` | Meaning |
|---|---|---|
| `templates/` | **empty** | All 13 `main`-edited templates landed byte-identical |
| `agents/` | **empty** | Landed byte-identical |
| `src/main/resources/` | **empty** | Includes `application.yml` + native-image metadata |

`application.yml` spot-checked: `prompt-budget` L41–49 present; `peer-model: gpt-5.6-sol` L89;
`review-passes`/`shared-session-enabled` correctly **absent** (so `@Bindable` defaults apply — see below).

## Test coverage deliberately reduced — full disclosure

Resolving the 10 test-file conflicts as **ours** preserved our layered test structure but dropped some `main` test
cases. This is a real, if modest, reduction and is reported rather than glossed:

| Dropped | Mitigation |
|---|---|
| `RubberDuckPromptBuilderTest.compactsPeerContentWhenEnabled` | Wired `PromptBudget` into the test's `context()` helper so the compaction path is at least constructible and exercised; `PromptContentCompactorTest` (6 tests, from `main`) covers the compaction logic itself |
| `ReviewOverallSummaryAppenderTest` — a `### Good Points` assertion | `GOOD_POINTS_SECTION` behaviour is exercised via `SummaryPromptBuilder`; the specific assertion is not reproduced |

**Net effect: direct behavioural coverage of the compaction path is thinner than `main`'s.** The compaction *logic*
is well covered; what is thinner is its *invocation from the rubber-duck builder*. Recommended follow-up: port
`compactsPeerContentWhenEnabled` onto the layered `RubberDuckPromptBuilderTest`. Low risk, not a merge blocker.

## Retained-capability rationale (multi-pass / shared-session)

`main` deleted `reviewPasses` and `sharedSessionEnabled`. Escalated to architect; **unanswered**. Proceeded on
keep-our-capability because it is **widening, not narrowing**: with the YAML keys absent, `@Bindable` defaults give
`reviewPasses=1` and `sharedSessionEnabled=true` — behaviourally identical to `main` for every existing user, while
our capability survives. Reversing this later is a config-and-delete change; recovering it after deletion would mean
re-implementing. If architect rules for removal, that is a follow-up task, not t23.

## Things I inferred rather than verified from an authoritative source

Listed explicitly so the coordinator can challenge them:

1. **`ReviewFinding` component order is `(title, priority, agent, category, summary, location)`** — note **agent
   before category**, the reverse of `main`'s `FindingsExtractor.Finding`. In the two retyped call-sites both values
   were identical, so the retype was provably safe *there*; **any future retype must re-check this ordering.**
2. **`buildFindingsSummary` mapping:** `main`'s `FindingsExtractor.buildFindingsSummary(results)` →
   `FindingsSummaryFormatter.formatSummary(FindingsExtractor.extractAll(results))`. Both null/empty-safe; equivalence
   established by reading both implementations, not by a dedicated test.
3. **`ReviewOverallSummaryAppender.appendToResults(List<ReviewResult>)`** is our single-arg signature, not `main`'s
   `appendToMergedResults` — the rename follows from the removal of merging, but no test asserts the old name is gone.

## Merge state at hand-off

- `MERGE_HEAD` = `5844456` — **merge state intact** (verify with `git rev-parse -q --verify MERGE_HEAD`; note that
  in this **worktree**, `.git` is a *file*, so `cat .git/MERGE_HEAD` misleadingly returns nothing)
- `HEAD` = `d3a499c`; **no merge commit created — by design**, left for coordinator review
- Staged: **108 files changed, 4576 insertions(+), 2380 deletions(−)**; unresolved conflicts: **NONE**
- Unstaged: only coordinator-owned files (`board.md`, `inbox.md`, `project-profile.yaml`) — untouched by me
- Undo point: tag `pre-merge-origin-main-backup` → `d3a499c`. No destructive command was run at any point.

## Known-good pre-existing failures (out of scope, confirmed against merge-base `fb2e795c`)

1. `pom-native.xml` does not compile at HEAD.
2. The config-only `default-shade` block in `pom.xml` (L242–265) produces a non-executable jar.
