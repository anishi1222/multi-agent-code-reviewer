## [t1] Establish migration constitution and layer dependency rules
- Defined 5-layer Ports & Adapters model (presentation/application/domain/infrastructure/shared) with application.port as the contract surface
- Key decision: domain purity = no Micronaut, no Jakarta, no SLF4J, no SDK — only java.* and shared
- Port naming convention: VerbNounPort; adapter naming: TechNounAdapter
- ArchUnit is the sole enforcement mechanism (no JPMS, no multi-module)
- SDK isolation to infrastructure only; framework annotations allowed in infrastructure + presentation only
- Learnings consumed: (none)

## [t5] Implementation plan, task breakdown, and test strategy for Ports & Adapters rewrite
- 16 tasks across 6 phases; split by vertical module (agent, report, skill, etc.), not by technical layer
- Phase ordering follows inward-dependency rule: shared → domain → ports → application → infrastructure → presentation
- Cycle-root types (ReviewResult, AgentConfig, SharedCircuitBreaker) must move first in Phase 1 to unblock all downstream moves
- TemplateService is the cycle hub causing 5/10 cycles — LoadTemplatePort breaks all 5 simultaneously
- ArchUnit dependency added as test-scope (archunit-junit5:1.3.0) — not yet in pom.xml
- Parallel task opportunities: T004/T005 (Phase 2), T006/T007/T008 (Phase 3), T009/T010 (Phase 4)
- Tester owns T015 (regression), architect owns T016 (smoke test) — all other tasks are backend
- Learnings consumed: teamlead/domain-purity-rules, teamlead/layer-naming-conventions

## [t6] Quality gate — validate implementation plan coverage, traceability, and feasibility
- Verified all 69 PM behavior IDs covered across 16 tasks using range expansion
- Verified all 10 constitution sections mapped to tasks (§5, §9 are cross-cutting/conditional — acceptable as implicit)
- Verified all 10 dependency cycles have explicit resolution strategies
- DAG is acyclic with correct parallelism opportunities
- File count arithmetic: 138 target files (classmap) ⊆ 167 task-file references (overlap expected)
- T009 dependency correctness verified — does not need T004 (only T002 domain types)
- Verdict: PASS (0 HIGH, 0 CRITICAL, 2 LOW, 1 INFO)
- Learnings consumed: [teamlead/domain-purity-rules, teamlead/layer-naming-conventions, teamlead/vertical-module-task-split]

## [t22] Final completeness and consistency gate failed cleanly
- Authoritative t20 build evidence and an independent Java 28 clean copy both passed: 1,106 Surefire + 4 Failsafe, zero failures/skips; architecture parsed 365/365 classes with zero prohibited edges/cycles.
- Deep-planning checkpoint chain is not complete: spec-to-plan and plan-to-tasks are absent; tasks-to-impl covers only t32.2 and has no validation block.
- Differential packaged probes exposed a P1 regression: default `list` returns no agents, while `--agents-dir ./agents` lists nine. `LoadAgentUseCase` returns before the configured-directory adapter can merge defaults.
- A second runtime composition gap leaves `skill --list` empty even after 32 global skills are parsed; no production code registers them into the DI `SkillRegistry`.
- The prior smoke accepted either empty or populated inventories, demonstrating that process-exit checks need known-content negative controls.
- Verdict: FAIL (5 CRITICAL, 0 HIGH); t22 must be rerun after backend, tester/PM, and checkpoint remediation.
- Learnings consumed: [teamlead/domain-purity-rules, teamlead/layer-naming-conventions, teamlead/vertical-module-task-split]

## [t22.4] Reconstructed global deep-planning checkpoints
- Canonical requirement identifiers are part of the traceability contract: descriptive ARCH/BUILD aliases initially looked valid but had to be replaced by the exact 84 IDs from the t22 matrix.
- The global evolved plan contains 37 items: 6 governance/environment, 16 original plan items, and 15 remediation/regate items; all map inversely to the 55-task board ledger.
- Source annotations apply to conversion tasks T001-T013; build, test, and validation tasks T014-T016 must be explicit N/A rather than falsely marked present.
- A Ruby-only deterministic checker avoided unavailable PyYAML and older-Ruby `filter_map`/`tally`; it now proves 29 structural invariants and 330 repository paths.
- Producer/auditor separation is intentional: t22.4 leaves all `validation.passed` values false so only t22.5 can attest semantic sufficiency.
- Learnings consumed: [teamlead/domain-purity-rules, teamlead/layer-naming-conventions, teamlead/vertical-module-task-split, teamlead/smoke-gate-must-prove-work]

## [t22] Final completeness and consistency re-gate passed
- Re-ran the full deterministic gate after C-001–C-005 remediation: 84/84 requirements, 69/69 PM behaviors, and all three independently validated checkpoints are complete.
- Fresh Java 28 `./mvnw -B clean verify` passed 1,107 Surefire + 5 Failsafe tests; the architecture analyzer parsed 366/366 production classes with zero prohibited edges/cycles.
- Fresh packaged probes independently reproduced C-004 and C-005 closure: default discovery listed nine agents and the canonical catalog listed 32 discovered skills.
- Producer-time checkpoint snapshots remain historically immutable; t22.5 validation metadata and the final matrix represent the later closure state.
- One LOW auxiliary-list omission remains non-blocking because the authoritative 55-row ledger and canonical mappings include all five IDs.
- Learnings consumed: [teamlead/checkpoint-contract-integrity, teamlead/domain-purity-rules, teamlead/layer-naming-conventions, teamlead/smoke-gate-must-prove-work, teamlead/vertical-module-task-split]

## [t36] Independent documentation quality gate failed on source accuracy
- Current architecture, runtime, security, packaging, EN/JA structure, and release chronology were independently reproduced.
- Both detailed READMEs prescribe `{{placeholder}}`, but the implementation and shipped resources use only `${key}`; mirrored translation errors can pass parity checks.
- The broader reference audit found one missing operational checklist and 17 unique retired-repository GitHub targets returning 404.
- Fixing the historical URLs conflicts with the loaded byte-immutable-history policy; coordinator arbitration is required rather than silently weakening either gate.
- Release notes also overstate GraalVM 25.0.4 release-workflow alignment and use an imprecise latest-tag claim; template trees omit one of 28 files.
- Verdict: FAIL (1 HIGH, 3 MEDIUM, 2 LOW); code/runtime reports remain green at 1,107 Surefire + 5 Failsafe + 1,107 native tests.
- Learnings consumed: [teamlead/checkpoint-contract-integrity, architect/unreleased-state-does-not-rewrite-release-history]

## [t36] Fresh documentation re-gate passed after F-01 through F-06 remediation
- Independently reproduced every closure: `${placeholder}`, canonical runbook anchor, explicit unavailable historical identifiers, accurate JVM/native workflow split, release-tag wording, and exact 28-file template inventories.
- Fresh deterministic evidence passed: 25/25 source assertions, 23/23 local references, 39/39 factual external targets, 6/6 parity checks, 4/4 chronology checks, and 5/5 fence checks.
- `PlaceholderUtilsTest` passed 2/2 under Java 28; this read-only gate did not rerun the unaffected full JVM/native build.
- Chronology validation must count changed lines and preserved identifiers rather than raw diff opcodes; 14 substitutions can be grouped into nine operations.
- Whole-document token equality is not a valid bilingual oracle for localized history; exact token parity is useful within matched current-state sections and must be combined with structural and curated semantic checks.
- The paired pre-history prefix has 8 headings / 33 inline-code tokens; the `Unreleased` subtree has 6 / 30. A hand-reconstructed final checker initially used stale path and section assumptions, so its false negatives were corrected against source rather than treated as document regressions.
- Learnings consumed: [architect/unreleased-state-does-not-rewrite-release-history, teamlead/checkpoint-contract-integrity, teamlead/parity-is-not-source-accuracy]
