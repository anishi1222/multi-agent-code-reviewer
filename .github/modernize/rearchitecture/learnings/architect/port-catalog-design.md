# Port Catalog Design Decisions

Architecture design decisions for the 12-port catalog in the Ports & Adapters rewrite.

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

## History
- 2026-08-05 (rearchitecture/t4): initial — 12 ports designed
