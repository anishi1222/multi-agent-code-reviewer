# Checkpoint Contract Integrity

Traceability checkpoints must preserve canonical IDs and separate producer self-checks from independent approval.

## What Happened

In multi-agent-code-reviewer/t22.4, descriptive architecture and build aliases initially preserved
meaning but broke the canonical 84-ID denominator. The producer also needed reproducible structural
and path checks without certifying its own semantic evidence.

In t22's final re-gate, the producer-time execution snapshot correctly continued to show t22.5 as
pending even after t22.5 independently approved the checkpoint chain. Rewriting that snapshot would
erase chronology; the validation block and final matrix carry the later state instead.

## Takeaway

- Copy requirement IDs exactly from the authoritative denominator; never rename them in a checkpoint.
- Mark source annotations only where conversion scope makes them applicable.
- Producers may verify counts, DAGs, inverse mappings, ledger parity, and path existence.
- Producers must leave `validation.passed: false`; only the named independent validator may set it.
- Make producer checks deterministic and executable so the validator can independently reproduce them.
- Preserve producer-time counts after validation; record later approval in validation metadata and
  the final gate matrix rather than retroactively changing historical snapshot arithmetic.
- A final report should preserve the original finding IDs and closure chain while clearly superseding
  the historical verdict with the current independently evidenced verdict.

## History

- 2026-08-09 (multi-agent-code-reviewer/t22.4): initial
- 2026-08-09 (multi-agent-code-reviewer/t22): added producer-snapshot and final-verdict guidance
