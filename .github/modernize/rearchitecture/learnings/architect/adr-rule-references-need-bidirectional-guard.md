# ADR-Rule References Need a Bidirectional Guard

ADR-to-test traceability must check both directions and must distinguish primary rules from their controls.

## What Happened

In multi-agent-code-reviewer t32.1, an Accepted ADR had previously promised a rule that did not
exist. A first guard model counted every `Rule Nx...` display name; renaming the primary test stayed
green because its `Rule Nx control:` test still supplied the old ID.

## Takeaway

Parse references only from Accepted ADR D-items, inventory only executable primary rule tests, and
check ADR→test plus test→ADR. Exclude control-only display names from primary inventory, pin several
real cross-file anchors, and mutation-test one-sided renames on each side. Comments and reserved
numbers are not executable inventory.

## History

- 2026-08-08 (multi-agent-code-reviewer/t32.1): initial

