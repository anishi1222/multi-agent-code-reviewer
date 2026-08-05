# Port Catalog Design Decisions

Architecture design decisions for the port catalog in the Ports & Adapters rewrite (12 designed in t4, 13 as implemented).

## What Happened
During t4, designed 12 port interfaces (5 inbound, 7 outbound) for the layered architecture. Key decisions:
1. LoadTemplatePort as first/highest-impact port (breaks 5 cycles)
2. Separate RunCopilotSessionPort vs RunRubberDuckSessionPort (different session semantics)
3. ManageCopilotClientPort for SDK lifecycle (start/stop/health) isolated from session execution
4. DoctorCommand requires RunDiagnosticsPort to avoid SDK type leakage into presentation

## Takeaway
- Port granularity: one port per cohesive capability. Don't create a god-port like `CopilotPort` that covers sessions + lifecycle + health + summaries — split by concern.
- Inbound ports map 1:1 to CLI commands (review, list, skill, doctor) plus report generation.
- Outbound ports map to external system boundaries: SDK sessions, SDK lifecycle, file I/O (source collection, report writing), template loading, AI summary generation.
- Port interfaces use domain types only in signatures — no SDK types leak through ports.
- **Validate a port contract against the output specs it has to satisfy before accepting it.** The
  t4 catalog gave `RunReviewPort` a single-result return; the report specs require one file per
  agent per pass, which that signature cannot produce. Corrected in t16 to
  `List<ReviewResult> execute(ReviewRequest)`.
- Filing a port under `port.inbound` does not make it inbound — see
  [port-direction-by-implementer](./port-direction-by-implementer.md).

## History
- 2026-08-05 (rearchitecture/t4): initial — 12 ports designed
- 2026-08-05 (rearchitecture/t16): implemented catalog is **13 ports** (6 inbound + 7 outbound);
  the extra inbound `ResolveTokenPort` was added during implementation without design review and
  is misclassified. `RunReviewPort` return type corrected to `List<ReviewResult>` (ADR-0006 D7).
