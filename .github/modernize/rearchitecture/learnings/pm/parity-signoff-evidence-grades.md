# Parity Sign-off Evidence Grades

Keep the product verdict separate from test-evidence strength when signing off a behavior inventory.

## What Happened

In `multi-agent-code-reviewer` task t21, all 69 PM behaviors met the approved t5 threshold of at
least one passing test, except one explicitly accepted interactive manual-tier flow. The earlier
t14 audit still graded 19 covered behaviors PARTIAL because their complete observable guarantee was
not asserted in isolation.

## Takeaway

Report both dimensions: use PASS/FAIL for parity and DIRECT/COVERED-PARTIAL/MANUAL-TIER for evidence
strength. Do not promote partial evidence to direct merely because the full suite is green. A
missing behavior or observed divergence blocks sign-off; weaker-but-policy-compliant coverage is
retained transparently as test-hardening information.

When later runtime evidence contradicts a prior sign-off, preserve the failed historical state.
Promote the affected row to DIRECT-CLOSURE only after a discriminating test proves both sides:
the corrected positive outcome and rejection of the former false-positive outcome. A generic green
suite or process-start assertion is not sufficient.

## History

- 2026-08-09 (`multi-agent-code-reviewer`/t21): initial
- 2026-08-09 (`multi-agent-code-reviewer`/t22.3): added contradiction and direct-closure rule from
  the AGT-01/SKL-01 re-gate
