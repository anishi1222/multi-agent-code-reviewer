# t4 — Target Layered Architecture, Full Package Mapping, and Port Catalog

## Summary

Complete Ports & Adapters architecture design for the `dev.logicojp.reviewer` CLI application. Maps all 120 production Java files to 6 layers across 24 target packages. Defines 12 port interfaces (5 inbound, 7 outbound) that break all 10 dependency cycles and confine external SDK/framework dependencies per constitution §2–§7.

## Deliverables

- [t4-architect-packages.md](./t4-architect-packages.md) — Target package tree with sub-package rationale
- [t4-architect-ports.md](./t4-architect-ports.md) — Port catalog: all 12 port interfaces with signatures, direction, implementer, and caller
- [t4-architect-classmap.md](./t4-architect-classmap.md) — Full 120-file class → target package mapping with migration notes

## Upstream Artifacts Consumed

- `clarification.md` — target architecture (Ports & Adapters), domain purity, scope
- `artifacts/project-profile.yaml` — current structure, cycles, SDK leakage
- `artifacts/t1-teamlead.md` — constitution: §1 layer model, §2 dep direction, §3 domain purity, §4 port convention, §5 naming, §8 file placement
- `artifacts/t2-architect.md` — cycle inventory (10), leakage inventory (SDK 20 files, Micronaut 24, Jakarta 32, SLF4J 50)
- `artifacts/t2-architect-cycles.md` — class-level cycle evidence
- `artifacts/t2-architect-leakage.md` — per-file framework/SDK leakage
- `artifacts/t2-architect-class-map.md` — preliminary per-file layer targets
- `artifacts/t3-pm.md` — 69 behavior IDs (parity baseline), 30 templates, 50+ config keys

## Evidence Mapping

- `t1-teamlead.md#§1` (layer model) → Package tree in t4-architect-packages.md
- `t1-teamlead.md#§2` (dep direction) → Import rules per layer in t4-architect-packages.md §2
- `t1-teamlead.md#§4` (port convention) → Port catalog in t4-architect-ports.md
- `t2-architect-cycles.md` (10 cycles) → Cycle resolution evidence in t4-architect-ports.md §3
- `t2-architect-class-map.md` (120 files) → Refined mapping in t4-architect-classmap.md
- `t3-pm.md#§5` (behavior IDs) → Port responsibilities traced to behavior IDs in t4-architect-ports.md §2

## Architecture Decision Summary

1. **12 ports, not more** — one port per cohesive capability per §4; avoids port explosion
2. **`LoadTemplatePort` breaks 5 cycles** — per learning `architect/template-service-cycle-hub`
3. **Domain types moved first** — per learning `architect/shared-domain-types-cycle-roots`
4. **`domain` has 6 sub-packages** — `agent`, `report`, `skill`, `instruction`, `review`, `resilience` for cohesion
5. **`infrastructure` has 7 sub-packages** — `copilot`, `config`, `template`, `file`, `auth`, `logging`, `parsing`
6. **`presentation` preserves CLI structure** — `command`, `formatter`, `parser` sub-packages
7. **`application` has 4 sub-packages** — `review`, `report`, `skill`, `agent` matching use-case domains
8. **`shared` is flat** — pure utilities, no sub-packages needed at 8 files
