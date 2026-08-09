# Event Cardinality Needs a Duplicate Mutant

Selecting one matching event proves existence, not an exactly-once observability contract.

## What Happened

In `multi-agent-reviewer` t32.1, ADR-0007 D4 required exactly one summary event per load. The
implementation emitted one, and all positive tests passed, but their helper returned the first
matching event. A mutant that emitted the same summary twice therefore survived all 5 tests.

## Takeaway

For an exact-cardinality contract, collect the complete filtered event set and assert its size
before checking content or level. Exercise every semantic branch, then add one extra emission as a
negative control; the targeted test must go RED.

## History

- 2026-08-08 (`multi-agent-reviewer`/t32.1): initial
