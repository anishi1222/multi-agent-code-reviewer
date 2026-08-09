# Validate Checkpoints from Authoritative Denominators

An independent checkpoint validator must reconstruct denominators from source artifacts instead of accepting the checkpoint producer's own counts.

## What Happened

In multi-agent-code-reviewer t22.5, the producer checker passed 29/29, but that established only
internal structure and path existence. The architect separately extracted 69 PM IDs from t3, 15
non-PM IDs from the historical traceability gate, T001–T016 from the task source, and 55 task IDs
from the board before comparing the three checkpoints. A separate post-validation checker was
needed because the producer checker correctly expected validation flags to remain false.

## Takeaway

Keep producer-time ledger state immutable, place the later audit result in a distinct validation
block, and use a validator-owned executable check. Reconstruct both sides of every mapping from
their authoritative sources, then verify current files and runtime rather than trusting summaries.

## History

- 2026-08-09 (multi-agent-code-reviewer/t22.5): initial
