# Shared Domain Types as Cycle Roots

AgentConfig and ReviewResult are domain models placed in wrong packages, causing 5 dependency cycles.

## What Happened
During t2 analysis, `AgentConfig` (in `agent` package) and `ReviewResult` (in `report.core` package) were found to be imported across 6+ packages each. Since their home packages also import from the consuming packages, this creates mutual dependency cycles.

## Takeaway
When restructuring to Ports & Adapters, move cross-cutting domain models (AgentConfig, ReviewResult, SkillDefinition, SharedCircuitBreaker) to the `domain` layer **first**, before any other restructuring. This is a foundational step that breaks 5 cycles and unblocks all other package moves. These types must have zero framework dependencies to satisfy domain purity (§3).

## History
- 2026-08-05 (rearchitecture/t2): initial — AgentConfig/ReviewResult identified as cycle roots
