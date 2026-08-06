# t25 — Restore rubber-duck compaction test coverage dropped by the origin/main merge

**Role:** tester · **Phase:** 10/11 (Upstream Merge) · **Classification:** brownfield-rewrite
**Status:** COMPLETE — full `clean verify` green, all restored assertions proven non-vacuous by mutation.

## Summary

The t23 merge of `origin/main` resolved 10 test-file conflicts as "ours", silently dropping two
assertions. Both are restored here **against the post-merge layered API** (not by reverting to
main's flat-tree signatures), upgraded from bare assertions into **matched-pair negative controls**
per ADR-0007 D7, and each is demonstrated to kill a targeted mutant.

Net: **+5 tests, 942 → 962** (the other +15 belong to a concurrent backend task — see Reconciliation).

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t23-backend.md` — merge strategy; the 10 "ours"-resolved test files.
- `.github/modernize/rearchitecture/artifacts/t23-backend-validation.md` — the disclosure of the two dropped assertions and the 945→939 reconciliation method I reused.
- `.github/modernize/rearchitecture/artifacts/t24-architect.md` — round-1 clean pass, the **942** baseline, the `shared/PromptContentCompactor` placement ruling, and open MEDIUMs F2/F3/F4 (confirmed out of scope for t25).
- `.github/modernize/rearchitecture/clarification.md` — target is **Java 28** (`pom.xml:22`); used to pin `JAVA_HOME`.
- `.github/modernize/rearchitecture/team/tester/inbox.md` — authoritative t25 task brief.

## Evidence Mapping

| Upstream artifact § | This task's output / evidence |
|---|---|
| `t23-backend-validation.md` → "dropped: `RubberDuckPromptBuilderTest.compactsPeerContentWhenEnabled`" | `RubberDuckPromptBuilderTest.PeerContentCompaction` (4 tests) + mutants **M1/M2** killed |
| `t23-backend-validation.md` → "dropped: `### Good Points` assertions" | `ReviewOverallSummaryAppenderTest.preservesNonNumberedSectionsAndExcludesThemFromFindingCount` + mutant **M3** killed |
| `t24-architect.md` → baseline **942 / 0 / 0 / 0**, exit 0 | Reproduced locally byte-for-byte before editing (`/tmp/t25-baseline.log`) |
| `t24-architect.md` → compactor lives in `shared/`, consumed by `domain` | Tests assert the `domain → shared` call path via the builder; no new imports added, arch gate still 10/10 |
| `t24-architect.md` → F4 `AgentPromptBuilder:145` ignores configured budget | Deliberately **not** touched (backend-owned); my `usesConfiguredPeerBudgetInsteadOfDefault` covers the analogous `RubberDuckPromptBuilder` path only |
| `clarification.md` → Java 28 | `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open` on every run |

## What was restored

### Gap 1 — compaction *invocation wiring* (`domain/agent/RubberDuckPromptBuilderTest`)

`PromptContentCompactorTest` (6 tests, survived the merge) proves the compactor works. It proves
nothing about whether `RubberDuckPromptBuilder` ever **calls** it. Restored as a `@Nested` group of
four cases, every one half of a pair differing in exactly one dimension:

| Test | Isolates | Expectation |
|---|---|---|
| `keepsPeerContentIntactWhenCompactionDisabled` | `compactPrompts` = false (**control**) | `PEER:` + full 26-char content |
| `compactsPeerContentWhenEnabled` | `compactPrompts` = true | `PEER:` + **tail** slice, strictly shorter |
| `compactsCounterContentWhenEnabled` | second public entry point | same pair via `buildCounterPrompt` |
| `usesConfiguredPeerBudgetInsteadOfDefault` | `peerContentMaxChars` (flag held true) | large budget ⇒ no compaction |

Two design points worth recording:

- The assertion is `isEqualTo("PEER:" + EXPECTED_TAIL)`, not merely "shorter". Head-keeping
  `compact()` would yield `PEER:abcdefghijkl`; tail-keeping `compactKeepingTail()` yields
  `PEER:opqrstuvwxyz`. The equality pins **which** compactor entry point the builder uses.
- With `peerContentMaxChars = 12`, `compactKeepingTail` emits **no omission marker** — the marker
  (`"\n\n... (N chars omitted for token budget)\n\n"`) is ~43 chars, so `available = 12 - 43 <= 0`
  and the method returns a bare tail slice. The test documents this rather than asserting a marker
  that would never appear.

### Gap 2 — `### Good Points` preservation (`domain/report/ReviewOverallSummaryAppenderTest`)

`ReviewFindingParser.FINDING_HEADER` only matches **numbered** `### N.` headers. The dropped
assertions protected two behaviours at once: non-numbered `###` sections must be (a) preserved in
the finalized report and (b) excluded from the recomputed 総評 counts.

Restored as `preservesNonNumberedSectionsAndExcludesThemFromFindingCount`. The pre-existing
`appendsSummaryFromMergedContent` (2 numbered findings, no other sections ⇒ 2件) is the **control
arm**; the new test adds two non-numbered sections to the same shape and still demands 2件.

Beyond restoring main's `contains("### Good Points")` / `contains("Prepared statements")`, it also
asserts the exact `主な指摘: SQL Injection、Secret exposure。` line — a loosened regex leaks section
titles into that list, which a `contains` count check alone would miss.

**Fixture hardening:** I gave `### 改善点` a body line. `extractFindingBlocks` discards blocks with
an empty body, so an empty `### 改善点` would have been dropped *even under a loosened regex*,
silently weakening the guard to 3件 instead of 4件. With the body, mutant M3 produces a clean 4件.

## Mutant kill demonstration

The tester charter forbids modifying production source. Mutants were therefore applied
**transiently** and restored from a **byte-exact backup copy** (`cp -p`), never `git checkout --`,
because a concurrent agent held uncommitted production work in this same worktree and `git checkout`
would have destroyed it. Each restore is verified by `shasum -c` **and** `git diff --quiet`.

| ID | Mutation | File | Result | Tests killed |
|---|---|---|---|---|
| **M1** | drop the call: `return compactKeepingTail(...)` → `return safeContent;` | `RubberDuckPromptBuilder.java` | exit 1 — **killed** | `compactsPeerContentWhenEnabled`, `compactsCounterContentWhenEnabled` (2 of 8; both controls survived ✅) |
| **M2** | invert the guard: `if (!compactPrompts)` → `if (compactPrompts)` | `RubberDuckPromptBuilder.java` | exit 1 — **killed** | the above **plus** `keepsPeerContentIntactWhenCompactionDisabled` (3 of 8) |
| **M3** | loosen finding regex: `(\d+)\]?\.\s+` → `(\d*)\]?\.?\s*` | `ReviewFindingParser.java` | exit 1 — **killed** | `preservesNonNumberedSectionsAndExcludesThemFromFindingCount` (1 of 4) |

The M1/M2 split is the ADR-0007 D7 proof: **M1 kills only the "enabled" arms** (so those tests
really do assert the call), while **M2 additionally kills the control arm** (so the control really
is a control, not a test that passes for free). M3's failure output is the exact regression guarded:

```
レビュー結果として、4件の指摘事項を確認しました。 ... 主な指摘: Good Points、改善点、SQL Injection。
   expected to contain: "2件の指摘事項"
```

**Post-mutation state:** `git diff --name-only -- src/main/java` contains neither mutated file.
Zero production lines changed by t25.

## Test Results

- Command: `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify`
- **Passed: 962 · Failed: 0 · Errors: 0 · Skipped: 0 · `MAVEN_EXIT_CODE=0` · BUILD SUCCESS**
- Architecture gate: `Architecture: layer boundary enforcement` **10/10 green**, `[arch] Rule 0: parsed 332/332 classes`, 0 cycles.
- Log: `/tmp/t25-verify2.log` (exit code captured unpiped to `/tmp/t25-verify2-rc.txt`).

### Reconciliation 942 → 962

Baseline **942** was re-measured locally before any edit (`/tmp/t25-baseline.log`, exit 0), matching
t24 exactly. The +20 delta decomposes with no remainder:

| Owner | Source | Declared | Executed |
|---|---|---|---|
| **t25 (mine)** | `RubberDuckPromptBuilderTest` | +4 `@Test` | +4 |
| **t25 (mine)** | `ReviewOverallSummaryAppenderTest` | +1 `@Test` | +1 |
| backend (in-flight) | `AgentPromptBuilderTest` + `AgentConfigLoaderTest` | +9 `@Test` | +9 |
| backend (in-flight) | `SkillBudgetTest` (new) | 3 `@Test` + 1 `@ParameterizedTest` | +6 |
| | **total** | | **+20 ⇒ 962** ✅ |

`git diff | grep -c '^-\s*@Test'` = **0** for my files: nothing was deleted or renamed. The
`SkillBudgetTest` row is the declared-vs-actual gap (`@ValueSource(ints = {0, -1, MIN_VALUE})`
expands 1 annotation into 3 executions) — counted from Maven's console `Tests run:` line, never from
summing `<testsuite tests=…>`.

## Runtime Validation Verdict

```
environment:  PASS  exit_code=0   JDK 28 (28.ea.9-open) resolved via JAVA_HOME; Maven wrapper 3.x
startup:      N/A   exit_code=n/a  library/CLI change only; no service started by t25
integration:  PASS  exit_code=0   mvn -B clean verify → 962/962, arch gate 10/10, Rule 0 332/332
e2e:          N/A   exit_code=n/a  no e2e tier in this build
overall:      PASS  exit_code=0
```

## Issues found (for downstream)

1. **CRITICAL (process, escalated):** another agent edited **production source in this same
   worktree** throughout t25 — `AgentConfig`, `AgentPromptBuilder`, `AgentConfigLoader`,
   `ConfigDefaults`, new `SkillBudget`/`SkillBudgetTest`. Two concrete harms:
   - The tree stopped test-compiling mid-run (`AgentConfigLoaderTest.java:[578,30] cannot find
     symbol: variable ConfigDefaults`), aborting a verification run that was not mine.
   - A `clean verify` overlapping their Maven run produced **924 run / 23 failures / 184 errors**,
     *all* `NoClassDefFoundError` (e.g. `ReviewOverallSummaryAppender` — a class whose `.java` and
     `.class` both existed on disk). This was a **build race on the shared `target/`**, not a
     regression. Re-running once no Maven process was live gave a clean 962/0/0/0.
   - Consequence: my final number necessarily includes backend's in-flight tests. The table above
     separates them, but t25's figure is only reproducible against that same working tree.
2. **No production defects found.** Every restored assertion passes against unmodified source; all
   three mutants were introduced by me and reverted. t24's F2/F3/F4 MEDIUMs remain open and
   backend-owned — untouched here.

## Files changed

```
src/test/java/dev/logicojp/reviewer/domain/agent/RubberDuckPromptBuilderTest.java   | +81
src/test/java/dev/logicojp/reviewer/domain/report/ReviewOverallSummaryAppenderTest.java | +53
2 files changed, 134 insertions(+)   — test files only, zero production changes
```
