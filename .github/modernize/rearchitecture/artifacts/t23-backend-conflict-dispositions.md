# t23 — Conflict Dispositions (all 82)

Merge topology: merge-base `fb2e795c` · ours `d3a499c` · theirs `5844456` (`origin/main`, +36 commits).

Governing rule: **layered architecture wins on structure; `main` wins on behaviour.**

## Category summary

| Git status | Meaning here | Count | Policy applied |
|---|---|---|---|
| `DU` | Deleted by us, modified by them — `main` edited a file our branch had already deleted during the flat→layered rewrite | 45 | Keep the deletion, but **first diff every file** and port any behaviour change into its layered successor |
| `UU` | Both modified — file exists in both trees | 19 | Hunk-by-hunk; behaviour from `main`, structure from ours |
| `UD` | Modified by us, deleted by them — `main` deliberately removed the feature | 8 | Accept `main`'s deletion (it is a real product decision, not a merge artifact) |
| `UA`/`AU` | Added on one side only, path-conflicting | 10 | Repackage to the layered path |
| **Total** | | **82** | **all resolved → 0** |

---

## `DU` — 45 files (flat-tree files `main` edited, we had deleted)

**This category is where silent regressions hide.** A `DU` conflict resolves "cleanly" by keeping the deletion —
and that silently discards whatever `main` changed. Every one of the 45 was therefore diffstat-audited against
merge-base *before* removal, specifically hunting for behaviour that had no counterpart in the layered tree.

That audit found **3 genuine features I had not catalogued from the commit messages alone** (`--no-rubber-duck`
resolver support, the dynamic code fence, and `ReviewFinding.summary`/`location` + structured extraction) — all
ported. See `t23-backend-feature-ports.md`.

Deleted flat packages (all 45 files): `agent/`, `cli/`, `config/`, `orchestrator/`, `report/`, `service/`, `target/`.

> These do not appear as deletions in `git diff --cached HEAD` because they did not exist on our side — our branch
> had already deleted them. They appear only as resolved `DU` conflicts. Verification that none were resurrected:
> `ls src/main/java/dev/logicojp/reviewer/` → `ReviewApp.java` + `application domain infrastructure presentation shared`.

All 45 traced to commit **`38dcbc8`** — *not* `25c4b49` as the brief stated (see index, "Correction to the t23 brief").

---

## `UD` — 8 files (`main` deleted; deletion accepted)

`main` removed the multi-pass result-merging cluster outright. Verified this was a deliberate product decision, not
a merge artifact: `git grep -E 'reviewPasses|passNumber' 5844456 -- src/main` returns **nothing**.

| File | Test file |
|---|---|
| `domain/report/AggregatedFinding.java` | `AggregatedFindingTest.java` (−3 tests) |
| `domain/report/ReviewFindingSimilarity.java` | `ReviewFindingSimilarityTest.java` (−4) |
| `domain/report/ReviewMergedContentFormatter.java` | `ReviewMergedContentFormatterTest.java` (−3) |
| `domain/report/ReviewResultMerger.java` | `ReviewResultMergerTest.java` (−16) |

Accounts for **−26 of the −6 net** test delta (see validation doc).

---

## `UU` — 19 files (both modified)

Resolved hunk-by-hunk. Non-mechanical calls, with rationale:

| File | Call | Rationale |
|---|---|---|
| `domain/agent/SynthesisStrategy.java` | **theirs** | Took `main`'s `default buildSynthesisPrompt(..., PromptBudget)` + `templateContent()`. Pure behaviour addition; no structural conflict. |
| `domain/report/FindingsSummaryFormatter.java` | **theirs** (all 4 hunks) | `main`'s `ConsolidatedFinding` algorithm (cross-agent consolidation by normalised title + compatible summary/location, with priority escalation) strictly supersedes our exact-match `MergedFinding` dedup. Retyped `FindingsExtractor.Finding` → `ReviewFinding`. |
| `presentation/formatter/ReviewOutputFormatter.java` | **ours** (all 3 hunks) | `main`'s `ModelConfig` refactor is not expressible against our `printModelSection` signature, and HEAD preserves the review-passes line we are deliberately retaining. |
| `domain/report/SummaryPromptBuilder.java` | **theirs**, ported | Adopted `main`'s 242-line version wholesale: `GOOD_POINTS_SECTION`, `PromptBudget` field, 8-arg ctor, compact/legacy branch, 6 compaction helpers. |
| `presentation/parser/ReviewOptionsParser.java` | **union** | Field union of both sides. **Caught a silent regression here** — see feature-ports doc. |
| `domain/agent/AgentPromptBuilder.java` | ours + hoist | `main`'s change pulled `infrastructure.config.SkillConfig` into `domain` (ADR-0006 violation). Constant hoisted to `shared/ConfigDefaults`. |
| `infrastructure/parsing/AgentConfigLoader.java` | **theirs** | Gains `enforceAssignedSkillBudget`; correct layer per ADR-0007. |
| `domain/report/ReviewFindingParser.java`, `ReviewOverallSummaryAppender.java`, `ReviewFinding.java`, `FindingsParser.java` | mixed | Behaviour from `main`, layered types retained. |
| `README.md` | **union of prose** | Both sides documented real features. |
| `README_en.md`, `README_ja.md` | **ours** | Layered-architecture narrative is the point of the branch. |
| remaining test files (10) | **ours** | See "coverage reduced" disclosure in validation doc. |

---

## `UA`/`AU` — 10 files (repackaged to layered paths)

Files `main` added under flat paths, relocated to their layered homes — e.g. `util/PromptContentCompactor.java` →
`shared/PromptContentCompactor.java` (+ its 6-test suite, which survives intact).

---

## Two resolution techniques that proved unsafe (recorded for reuse)

1. **Line-based "take ours" resolvers mis-interleave restructured code.** A script that drops everything between
   `=======` and `>>>>>>>` produced *syntactically broken* Java in 3 files where `main` had hoisted a `@Test` out of
   a `@Nested` class — the surviving braces did not balance, surfacing as a cascade of `';' expected`.
   **Correct primitive when the intent is "take ours entirely": `git show :2:<path> > <path>`.**
   Line-based resolution is only safe when both sides share the same block structure.
2. **BSD `sed` does not support `\b`** — it silently succeeds while changing nothing, so a rename appears to work and
   the breakage surfaces much later. **Always audit with a tree-wide `grep` after any `sed`-driven rename.**
   A greedy `sed` on a method call also matched across a chained `.getFirst()`, producing
   `...(List.of(merged)).getFirst(, 1);`. Anchor the capture, then re-read the file to verify.
