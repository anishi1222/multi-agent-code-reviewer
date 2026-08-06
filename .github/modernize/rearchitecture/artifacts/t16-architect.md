# t16 — ADR 0006 + user-facing documentation update

## Summary

Authored `docs/adr/0006-ports-and-adapters-layering.md`, which resolves the six architecture
tensions routed to the architect during Phases 7–8, plus two defects discovered during source
verification for this task. Updated all user-facing documentation (3 READMEs, ADR index,
3 historical ADR reference sections) so it describes the **implemented** Ports & Adapters
structure instead of the pre-migration flat package tree.

Docs-only task. No source or configuration files were touched (architect charter constraint).

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/clarification.md` — confirmed the rewrite is CLI-only, no web/UI surface, so "user-facing docs" means READMEs + ADRs + runbook, not API docs.
- `.github/modernize/rearchitecture/artifacts/project-profile.yaml` — module/LOC baseline used to sanity-check the new package tree against reality.
- `.github/modernize/rearchitecture/artifacts/t2-architect.md` — current-state analysis; source of the "flat package tree" description the READMEs still carried.
- `.github/modernize/rearchitecture/artifacts/t3-pm.md` — OUT-02 / OUT-03 report filename specs; used to validate the `RunReviewPort` return-type amendment (D7).
- `.github/modernize/rearchitecture/artifacts/t4-architect.md` + `t4-architect-packages.md` — §2 allowed-imports matrix, the authority ADR-0006 reconciles against the implementation.
- `.github/modernize/rearchitecture/artifacts/t4-architect-ports.md` — port catalog; §2.1 still shows the superseded single-result `RunReviewPort`, which D7 corrects.
- `.github/modernize/rearchitecture/artifacts/t5-teamlead.md` — phase/task decomposition; confirmed t16 is the documentation-of-record task, not an implementation task.
- `.github/modernize/rearchitecture/artifacts/t12.1-backend.md` — final package moves; used to derive per-package contents in the new README tree.
- `.github/modernize/rearchitecture/artifacts/t13-backend.md` — `LayerDependencyRulesTest` baseline (Rule 3: 2 violators / 2 exempt, Rule 4: 3 violators / 3 exempt, Rules 6a/6b: 0 cycles); source of the "Known deviations" table rows 3–4 and of the enforcement-rule inventory in D5.
- `.github/modernize/rearchitecture/team/architect/inbox.md` — the six ADR-0006 items themselves (L113–165 port-direction defect, L269–296 ReviewApp/factory placement, L299–345 t13 G1/G2/G3, L348–379 batch 2 items a–d).
- `.github/modernize/rearchitecture/decisions.md`, `board.md` — prior decisions not re-litigated; downstream consumers t17 / t22 identified.

## Evidence Mapping

| Upstream artifact / section | This task's output or evidence |
|---|---|
| `t4-architect-packages.md` §2 allowed-imports matrix (L52–67) | ADR-0006 **D1** reproduces the matrix with a 6th row (composition root) and an explicit "MUST NOT" column; README `## Architecture` layer table is the user-facing projection of the same matrix |
| `t4-architect-packages.md` §1 package tree (L7–50) | README_en/ja `Project Structure` tree rewritten to the 24 actually-existing packages; verified by per-package `.java` counts at HEAD `210e96d` |
| `t4-architect-ports.md` §2.1 single-result `RunReviewPort` | ADR-0006 **D7** supersedes it with `List<ReviewResult> execute(ReviewRequest)`; evidence = `t3-pm.md` OUT-02/OUT-03 require one file per agent per pass, unsatisfiable with a single result |
| `t4-architect-ports.md` port catalog (inbound/outbound split) | Source verification found `infrastructure.auth.GitHubTokenResolver` implements `application.port.inbound.ResolveTokenPort`, and `ApplicationPortFactory` binds inbound `ExecuteSkillPort` to `infrastructure.copilot.SkillExecutor` → ADR-0006 **D2** (direction is decided by *who implements*) + Known deviations rows 1–2 |
| `t13-backend.md` Rule 4 result (3 violators / 3 exempt, scoped to `application.port`) | ADR-0006 **D5** narrows Rule 4 to `application.port.outbound` and requires a rule for `presentation ⊥ infrastructure`; the latter landed in t13.1 as **Rule 5b** (0 exempt) while this task was in flight, so the ADR records it as Resolved. Known deviations rows 3 and 7 |
| `t13-backend.md` Rule 3 result (2 violators / 2 exempt: DI factories) | ADR-0006 **D3** relocates the 3 Micronaut `@Factory` classes into the composition root instead of moving `ReviewApp`; verified the factories are referenced only from Javadoc, so relocation is import-safe; Known deviations row 4 |
| `inbox.md` L113–165 (t9 port defect + "For architect (t16)" directive) | ADR-0006 **D2** + Known deviations rows 1–2; `[notify:coordinator]` HIGH raised because remediation is code work outside the architect charter |
| `inbox.md` L269–296 (ReviewApp / factory placement) | ADR-0006 **D3**, including the counter-evidence that moving `ReviewApp` breaks `mainClass` ×4, two GraalVM `reachability-metadata.json` files, and the `d.l.reviewer.ReviewApp` logger name asserted in `docs/runbook.md` L233/L249 |
| `inbox.md` L348–379 batch 2 items a–d (logging split, `shared` ownership, duplicate class names) | ADR-0006 **D4** (displaced-capability standing rule, first application `PropagateCorrelationPort`) and **D6** (`shared` sole owner + unique-simple-name convention); evidence = `domain` 4 + `application` 10 files on `java.util.logging` vs `presentation` 10 + `infrastructure` 29 on SLF4J, and 2 duplicate simple names (`ConfigDefaults`, `RetryPolicyUtils`) — the duplicates were consolidated into `shared` by t13.1 mid-task, re-verified as 0 remaining |
| `t2-architect.md` current-state package description | README mermaid diagram named 8 classes that no longer exist (`ReviewService`, `ReportService`, `ReportGenerator`, `AgentService`, `SkillService`, `TemplateService`, `GitHubTarget`, `LocalTarget`, `ReportGeneratorFactory`); replaced with verified edges (`ReviewRunExecutor` → `RunReviewPort`/`GenerateReportPort`, `ReviewPassRunner` → `RunCopilotSessionPort`) |

## Decisions recorded in ADR 0006

| ID | Decision |
|---|---|
| D1 | Five layers **plus the composition root as "layer 0"** (`dev.logicojp.reviewer`). Composition root does wiring only, contains no business logic, and is never referenced by other layers. |
| D2 | **Port direction is decided by who implements it.** Inbound = implemented by `application`, called by `presentation`. Outbound = implemented by `infrastructure`, called by `application`. An inbound port whose only implementer lives in `infrastructure` is a layer defect. `infrastructure` may import `application.port.outbound` only. |
| D3 | `ReviewApp` **stays** in the root package. Instead the Micronaut `@Factory` classes move from `infrastructure.copilot` into the composition root — this removes 3 Rule 4 exemptions and converts Rule 3's class-name list into one bounded package, so net exemptions decrease. |
| D4 | **Standing rule:** any cross-cutting technical capability displaced by a purity rule MUST be reintroduced as an `application.port.outbound` port with an `infrastructure` adapter — never silently dropped or downgraded. First application: **`PropagateCorrelationPort`** / `MdcCorrelationAdapter` (bind/clear correlation scope, propagate into virtual threads and `StructuredTaskScope`, restore the caller's context on both normal and exceptional return), implemented by t13.1. Pre-binds future metrics/tracing. |
| D5 | **Every row of the allowed-imports matrix must have exactly one enforcement rule; a missing rule is itself a defect.** Requires a rule for `presentation ⊥ infrastructure` (landed as **Rule 5b**, 0 exempt) and narrows Rule 4 from `application.port` to `application.port.outbound`. New rules take a letter suffix at their logical position rather than renumbering, so Rule 6a/6b references in prior learnings stay valid. |
| D6 | `shared` is the **sole owner** of cross-layer defaults and retry policy; `infrastructure.config` holds only Micronaut-bound records. New convention: simple class names are unique under `dev.logicojp.reviewer` (machine-checkable once the 2 known duplicates are removed). |
| D7 | `List<ReviewResult> execute(ReviewRequest)` is the confirmed `RunReviewPort` signature (one element per agent per pass; `passNumber == 0` single-pass, `>= 1` multi-pass). Going forward, port contracts are accepted only if they can satisfy existing output specs. |

## Known deviations recorded

ADR-0006 records these with an explicit **Status** column. `t13.1` landed while this task was in
flight and closed two of them; the statuses below were re-verified against source at the end of
the task, not taken from the t13.1 report.

| # | Deviation | Status | Owner |
|---|---|---|---|
| 1 | `ResolveTokenPort` classified inbound but implemented by `infrastructure.auth.GitHubTokenResolver` | **Open** | backend |
| 2 | `ExecuteSkillPort` implemented by *both* `application.skill.ExecuteSkillUseCase` and `infrastructure.copilot.SkillExecutor`; DI binds the latter, so the use case has zero references outside its own port's Javadoc | **Open** | backend |
| 3 | Rule 4 scoped to `application.port` instead of `application.port.outbound` (`LayerDependencyRulesTest` L196–197) | **Open** | backend |
| 4 | 3 DI factories still in `infrastructure.copilot` and named as Rule 4 class-name exemptions | **Open** | backend |
| 5 | `domain` (4 files) + `application` (10 files) still emit logs via `java.util.logging` | **Partial** — correlation propagation restored by t13.1's `PropagateCorrelationPort`; leveled diagnostic output still undecided | backend |
| 6 | `ConfigDefaults` / `RetryPolicyUtils` duplicated | **Resolved (t13.1)** — consolidated into `shared`; `find … \| uniq -d` now returns 0 duplicate simple names | — |
| 7 | No rule for `presentation ⊥ infrastructure` | **Resolved (t13.1)** — Rule 5b, 0 exempt, negative control captured | — |

## Files changed

| File | Change |
|---|---|
| `docs/adr/0006-ports-and-adapters-layering.md` | **new** — ADR of record for the layering (D1–D7, alternatives, consequences, known deviations, operational notes) |
| `docs/adr/README.md` | sync tag `v2026.06.24-refactor-seams-tests` → `v2026.08.05-ports-and-adapters`; ADR-0006 row appended to `## 一覧` |
| `docs/adr/0001-custom-cli-parser.md` | References updated `cli/` → `presentation/{parser,command}/`; cross-link to ADR-0006 (decision still Accepted, only location moved) |
| `docs/adr/0002-micronaut-di.md` | Cross-link to ADR-0006 noting DI wiring is now confined to the composition root |
| `docs/adr/0003-virtual-thread-orchestration.md` | References updated `orchestrator/` → `application/review/`, `util/` → `shared/`; cross-link to ADR-0006 |
| `README_en.md` / `README_ja.md` | `## Architecture` / `## アーキテクチャ` rewritten: layer responsibility table, new layer-structure mermaid, corrected review-execution-flow mermaid; `Project Structure` tree rebuilt to the 24 real packages; sync sentence dated 2026-08-05 with ADR-0006 link. Files remain line-for-line parallel (1112 each). |
| `README.md` | `## Architecture` rewritten from the old flat `cli/ orchestrator/ agent/ report/summary/ service/ util/` list to the 5 layers + composition root + `application.port.{inbound,outbound}`, plus the `LayerDependencyRulesTest` enforcement note |

`docs/runbook.md` required **no change**: its logger-name examples (`d.l.reviewer.ReviewApp`, L233/L249)
remain accurate precisely because D3 keeps `ReviewApp` in the root package.

## Verification performed

Documentation must describe the implementation, not the plan, so every structural claim was
verified against HEAD `210e96d` rather than taken from upstream reports:

- Per-package `.java` counts for all 24 packages, used to build the tree.
- `presentation → infrastructure` import count = **0** (the rule for it is therefore additive regression-prevention, not a fix — it landed as Rule 5b in t13.1).
- Port implementer resolution for every inbound port (found the 2 misclassifications).
- Reference search on the 3 DI factory classes (Javadoc-only → relocation is import-safe).
- Logging-framework split per layer (4 + 10 on `java.util.logging`; 10 + 29 on SLF4J).
- Duplicate simple class names across the whole tree = exactly 2.
- Real call edges for the flow diagram (`ReviewRunExecutor` → `RunReviewPort` / `GenerateReportPort`; `ReviewPassRunner` → `RunCopilotSessionPort`).
- 8 classes named in the old README diagram confirmed deleted.

Structural checks on the edited docs:

- `README_en.md` / `README_ja.md`: 1112 lines each (parallel preserved), 60 code fences each (even), 2 mermaid blocks each, subgraph/`end` balanced, quotes balanced.
- New mermaid diagrams use only constructs already present in the repo's previously-rendering diagrams (literal newlines inside quoted labels, `direction` in subgraphs, dotted labelled edges).

## Test Results

Not applicable — documentation-only task, no source or build files modified.
No test command was run; `mvn verify` behaviour is unchanged by this task.
