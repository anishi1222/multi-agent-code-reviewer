# t23 — Feature Ports, Caught Regressions, and §5 Acceptance

## 6 `main` features ported into the layered tree

Each of these lived in a file our branch had deleted (`DU`) or in a conflict region that `main` lost. **Keeping the
deletion or taking `ours` would have compiled cleanly and silently dropped the feature** — which is precisely why
every `DU` file was diffstat-audited rather than trusted to its commit message.

| # | Feature | Origin | Landed in |
|---|---|---|---|
| 1 | **Prompt compaction** (`--compact-prompts`) — full budget-driven compaction path | `38dcbc8` | `shared/PromptBudget`, `shared/PromptContentCompactor`, `SummaryPromptBuilder`, `RubberDuckPromptBuilder`, `LocalSourcePrecomputer` |
| 2 | **`--no-rubber-duck` resolver support** — flag was parsed but inert | `DU` audit | `presentation/ReviewAgentConfigResolver`, `ReviewOptions.RubberDuckOptions.disabled` |
| 3 | **Dynamic code fence** — <code>"\`".repeat(max(3, longestBacktickRun+1))</code>, so fenced content containing backticks cannot break out | `DU` audit | `infrastructure/file/LocalFileContentFormatter.codeFenceFor()` |
| 4 | **`ReviewFinding.summary` / `.location`** + structured extraction with `priorityOrUnknown` → `"Unknown"` | `DU` audit | `domain/report/ReviewFinding`, `FindingsParser`, `ReviewFindingParser` |
| 5 | **`enforceAssignedSkillBudget`** | `38dcbc8` | `infrastructure/parsing/AgentConfigLoader` (+3 tests preserved) |
| 6 | **`ConsolidatedFinding` cross-agent consolidation** — normalised-title matching with priority escalation; supersedes our exact-match dedup | `38dcbc8` | `domain/report/FindingsSummaryFormatter` (+2 tests preserved) |

Items 2–4 were **not** discoverable from commit messages; they surfaced only from the per-file diffstat audit of all
45 `DU` files. Had I trusted the commit log, all three would have been lost silently.

## 2 silent regressions caught and reversed

1. **`--no-shared-session` was dropped by the merge.** `main`'s side won the conflict regions in
   `ReviewOptionsParser` and took its case label and builder call with it — while `CliUsage:48` still advertised the
   flag. The CLI would have accepted an advertised flag as a no-op. **Restored both.**
2. **`ReviewResultPipelineTest` lost a test.** Git *auto-merged* (no conflict, so nothing flagged it) `main`'s version
   of this file, which drops `finalizeResultsReturnsRawPassResultsWhenMultiPass` — coverage of the multi-pass
   capability we are deliberately retaining. Found only by diffing per-file test-annotation counts against `HEAD`.
   **Restored from `HEAD`; both tests pass.** This is the strongest argument for the count reconciliation in the
   validation doc: an auto-merge is *not* evidence of a safe merge.

## ADR-0006 violations introduced by auto-merge (both fixed)

Auto-merge twice inserted an illegal import into `domain`. Both **compiled fine** and would only have failed at the
architecture gate — the generalisable trap is: *when `main` adds a framework-bound config type consumed by `domain`,
git will happily wire it in across a layer boundary.*

1. **`PromptBudgetConfig` (Micronaut `@ConfigurationProperties`) consumed by domain prompt builders.**
   Split into a pure `shared/PromptBudget` record + a thin `infrastructure/config/PromptBudgetConfig` binder exposing
   `toPromptBudget()`. Names kept deliberately distinct to avoid the known
   `duplicate-utility-consolidation-semantic-drift` trap.
2. **`AgentPromptBuilder` referencing `infrastructure.config.SkillConfig.DEFAULT_MAX_PARAMETER_VALUE_LENGTH`.**
   Hoisted to `shared/ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH`; `SkillConfig` now delegates.

`domain` may import only `java.*`, `domain`, `shared` — `LayerDependencyRulesTest` Rule 1 is an allowlist with
**zero** exemptions (`Set.of()`).

## Wiring chain — the ported feature is live, not inert

A port that compiles but is never invoked is indistinguishable from a dropped feature at runtime, so `PromptBudget`
was wired end-to-end:

```
application.yml (prompt-budget, L41-49)
  └─> infrastructure/config/PromptBudgetConfig   (Micronaut binding)
        └─> .toPromptBudget()                    (pure shared type crosses the boundary)
              ├─> OrchestratorConfig.promptBudget
              │     └─> ReviewOrchestrator.buildReviewContext -> ReviewContext.promptBudget
              │           ├─> LocalSourcePrecomputer
              │           └─> RubberDuckPromptBuilder
              └─> SummaryGenerator.SummaryGenerationConfig.promptBudget
                    └─> SummaryPromptBuilder (8-arg ctor)
```

Route chosen because **`application` may not import `infrastructure`**. `OrchestratorConfig` and
`SummaryGenerationConfig` already flow infrastructure→application and both use builders/records with defaults, so
adding a component broke only 5 test call-sites.

## §5 acceptance checklist

| § | Item | Status | Evidence |
|---|---|---|---|
| 5.1 | Prompt-compaction feature present and reachable | ✅ | Wiring chain above; `PromptContentCompactorTest` 6 tests green |
| 5.2 | Rubber-duck model/template updates (`05757a4`) | ✅ | `git diff MERGE_HEAD -- templates/` is **empty** — all 13 `main`-edited templates landed byte-identical |
| 5.3 | Native-image reachability metadata (`0b802d1`), `native.maven.plugin.version` 1.1.3 | ✅ | `git diff MERGE_HEAD -- src/main/resources/` empty; POM property present |
| 5.4 | `jackson.version` = 3.1.5 in **both** POMs (CVE-2026-59889) | ✅ | `pom.xml:39`, `pom-native.xml:41` |
| 5.5 | No flat package resurrected | ✅ | Top level = `ReviewApp.java` + 5 layer dirs; flat-dir probe returns `NONE` |
| 5.6 | `LayerDependencyRulesTest` green **and non-vacuous** | ✅ | 10/10 pass; Rule 0 (`parsed == classFilesOnDisk`) holds — not weakened, not `@Disabled` |
| 5.7 | Copilot SDK confined to `infrastructure` | ✅ | Independent grep: `NONE - clean` |
| 5.8 | Templates verified explicitly (load by path — a dropped edit throws no compile error) | ✅ | Byte-identical to `MERGE_HEAD` |

**Toolchain note:** `main` upgraded Java 27 → 28 (`java.version=28`, micronaut-parent 5.1.0, micronaut 5.1.2,
copilot-sdk 1.0.8). The machine default JDK is GraalVM 25 and **cannot** compile this tree. Every build must pin:

```bash
JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify
```

`--enable-preview` is active (surefire `argLine`), so a JDK mismatch produces confusing, misleading errors rather
than a clear version complaint.
