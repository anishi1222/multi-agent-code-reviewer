# t23 — Resolve the origin/main Merge into the Layered Tree

## Summary

Merged `origin/main` (`5844456`, 36 commits ahead) into `anishi1222-layered-architecture-rebuild` (`d3a499c`).
**82 conflicts → 0.** All resolved and **staged**; the merge commit has **deliberately not been created** — left for coordinator review.

The two sides did structurally opposed things: our branch deleted the flat technical-concern tree
(`agent/ cli/ config/ orchestrator/ report/ service/ target/ util/`) and rewrote it as Ports & Adapters layers;
`origin/main` kept the flat tree and added features to it. The governing rule applied throughout:

> **The layered architecture wins on structure; `main` wins on behaviour.**

A merge is only correct if main's *behaviour* survives inside the layered tree. Deleting a `main`-side change to
silence a conflict is a silent feature regression — this was treated as the primary failure mode and audited for
explicitly (see `-feature-ports.md`, which records 6 features that would otherwise have been lost, and 2 silent
regressions that were caught and reversed).

**Result: `BUILD SUCCESS`, 939 tests, 0 failures, 0 errors, 0 skipped.** Architecture gate green and non-vacuous.

## Deliverables

- [t23-backend-conflict-dispositions.md](./t23-backend-conflict-dispositions.md) — all 82 conflicts by category, with per-category policy and rationale for every non-mechanical call
- [t23-backend-feature-ports.md](./t23-backend-feature-ports.md) — the 6 ported `main` features, 2 caught silent regressions, and the §5 acceptance checklist
- [t23-backend-validation.md](./t23-backend-validation.md) — build/test evidence, exact test-count arithmetic, layer-purity audit, and the disclosure of reduced test coverage

## Status

| Gate | Result |
|---|---|
| Conflicts resolved | **82 → 0** |
| `mvn clean verify` | **BUILD SUCCESS** |
| Tests | **939 run, 0 failures, 0 errors, 0 skipped** |
| `LayerDependencyRulesTest` | **10/10 green, non-vacuous** (Rule 0 completeness holds) |
| Flat packages resurrected | **none** — top level is `ReviewApp.java` + 5 layer dirs only |
| Copilot SDK outside `infrastructure` | **none** |
| `jackson.version` | **3.1.5** in both POMs (CVE-2026-59889 avoided) |
| Merge commit created | **No** — staged only, by design |

## Upstream Artifacts Consumed

- `docs/adr/0006-ports-and-adapters-layering.md` — the layer import matrix. Sole authority for every placement
  decision; drove the `PromptBudget` config split and the `ConfigDefaults` constant hoist.
- `docs/adr/0007-agent-definition-trust-model-and-secret-sink-boundary.md` — trust-boundary rules; confirmed
  `AgentConfigLoader.enforceAssignedSkillBudget` (a `main` feature) belongs in `infrastructure/parsing`, not `domain`.
- `.github/modernize/rearchitecture/board.md` — run state; confirmed t23 scope and phase. Read-only, not edited.
- `.github/modernize/rearchitecture/team/backend/inbox.md` (lines 648–816) — the coordinator's authoritative t23
  brief: 5 non-negotiables, conflict taxonomy §4-A…§4-D, §5 acceptance checklist, §6 definition of done, §7 report format.

## Evidence Mapping

| Upstream artifact § | This task's output / evidence |
|---|---|
| ADR-0006 § layer import matrix (`domain` may import only `java.*`/`domain`/`shared`) | Forced the split of `PromptBudgetConfig` into pure `shared/PromptBudget` + `infrastructure/config/PromptBudgetConfig` binder. Evidence: `t23-backend-feature-ports.md` §"ADR-0006 violations"; verified by `LayerDependencyRulesTest` Rule 1 (green) **and** an independent grep (`t23-backend-validation.md` §"Layer purity audit") |
| ADR-0006 § `domain` must not import framework types | Second violation caught in `AgentPromptBuilder` → constant hoisted to `shared/ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH`. Evidence: same grep, `NONE - clean` |
| ADR-0006 § `application` must not import `infrastructure` | Determined the `PromptBudget` wiring route (`OrchestratorConfig`/`SummaryGenerationConfig` carry the pure type). Evidence: `t23-backend-feature-ports.md` §"Wiring chain" |
| ADR-0007 § secret-sink / agent-definition trust boundary | `enforceAssignedSkillBudget` placed in `infrastructure/parsing/AgentConfigLoader`; 3 `main` tests preserved (`AgentConfigLoaderTest` 11→14). Evidence: `t23-backend-validation.md` per-file delta table |
| inbox.md §4-A…§4-D conflict taxonomy | Full 82-conflict disposition table: `t23-backend-conflict-dispositions.md` |
| inbox.md §5 acceptance checklist | Point-by-point verification: `t23-backend-feature-ports.md` §"§5 acceptance checklist" |
| inbox.md §6 definition of done | Status table above + `t23-backend-validation.md` |
| board.md § t23 scope/phase | Scope confirmed as single-unit merge; escalation rationale in §"Notes for the coordinator" below |

## Notes for the Coordinator

1. **Correction to the t23 brief (attribution only).** The brief attributes the UD deletions and several touches to
   `25c4b49`. That is **wrong**: all of them came from `38dcbc8`. `25c4b49` touched only `SummaryPromptBuilder.java`
   (+27) and `AgentConfigValidator.java` (+1) in main source. **The brief's ruling stands** — only the commit
   attribution was incorrect.
2. **Open escalation, unanswered.** `main` deleted `reviewPasses` / `sharedSessionEnabled` outright. I escalated to
   architect and proceeded on **keep-our-capability** (widening, not narrowing): with the YAML keys absent,
   `@Bindable` defaults yield `reviewPasses=1`, `sharedSessionEnabled=true`, i.e. behaviour identical to `main`
   while our capability survives. If architect rules for removal, that is a **follow-up task, not t23**.
3. **Reduced test coverage — disclosed, not hidden.** Taking `ours` on 10 test conflicts dropped some `main` test
   cases. Fully itemised with mitigations in `t23-backend-validation.md` §"Test coverage deliberately reduced".
4. **Not committed.** Merge state is intact (`MERGE_HEAD = 5844456`); everything is staged. Undo point:
   tag `pre-merge-origin-main-backup` → `d3a499c`.
5. **Pre-existing, out of scope:** `pom-native.xml` does not compile at HEAD, and the config-only `default-shade`
   block in `pom.xml` (L242–265) produces a non-executable jar. Both confirmed pre-existing against merge-base `fb2e795c`.
