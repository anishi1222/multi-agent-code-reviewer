# t5 — Implementation Plan, Task Breakdown, and Test Strategy

## Summary

Phased implementation plan for the Ports & Adapters rewrite of `dev.logicojp.reviewer` (120 production files → 138 target files across 6 layers). 16 tasks across 6 phases. Split by **vertical business module**, not by technical layer. Every task traces to requirements (REQ from PM behavior IDs or constitution sections).

## Deliverables

- [t5-teamlead-plan.md](./t5-teamlead-plan.md) — Phased implementation plan with requirement traceability
- [t5-teamlead-tasks.md](./t5-teamlead-tasks.md) — Full task breakdown (independently executable)
- [t5-teamlead-teststrategy.md](./t5-teamlead-teststrategy.md) — Test strategy: ArchUnit, regression, and smoke test

## Upstream Artifacts Consumed

- `clarification.md` — scope, backward-compatibility posture, success definition
- `artifacts/project-profile.yaml` — current structure, LOC distribution, cycle inventory
- `artifacts/t1-teamlead.md` — constitution (all sections: layer model, dep direction, domain purity, naming, enforcement, migration invariants)
- `artifacts/t2-architect-cycles.md` — 10 dependency cycles with class-level evidence, cycle hub analysis
- `artifacts/t2-architect-leakage.md` — per-file framework/SDK leakage (20 SDK, 24 Micronaut, 32 Jakarta, 50 SLF4J files)
- `artifacts/t3-pm.md` — 69 behavior IDs, 4 commands, 4 exit codes, 30 templates, 50+ config keys
- `artifacts/t4-architect.md` — architecture design index (12 ports, 24 packages, cycle resolution)
- `artifacts/t4-architect-packages.md` — target package tree, allowed imports per layer
- `artifacts/t4-architect-ports.md` — port catalog (5 inbound, 7 outbound) with signatures and behavior traceability
- `artifacts/t4-architect-classmap.md` — full 120-file class → target package mapping with migration notes

## Evidence Mapping

- `t1-teamlead.md#§1–§2` (layer model + dep direction) → Plan Phase 1 (foundation: shared + domain + ports)
- `t1-teamlead.md#§6` (boundary enforcement) → Plan Phase 5 (ArchUnit tests)
- `t2-architect-cycles.md#Cycle Hub Analysis` → Plan Phase 1 ordering (move cycle roots first)
- `t2-architect-leakage.md` → All implementation tasks: purification notes per file
- `t3-pm.md#§5–§7` → Test strategy: behavior IDs as regression traceability index
- `t4-architect-classmap.md` → Every task's file list derived from class map sections
- `t4-architect-ports.md#§2–§3` → Plan Phase 1 (port interfaces), Phase 2–4 (port implementations)
