# Mutation Verify Regression Tests

A test written to lock a bug fix is worthless until you reintroduce the bug and watch it fail — assert against a second derivation, never a literal.

## What Happened

`multi-agent-code-reviewer` / t28 (finding F3). A config value was read from **two different keys** by
two components that were supposed to agree — a banner said "3 passes" while 1 pass actually ran.

The natural regression test is:

```java
// set reviewer.…review-passes = 3
assertThat(banner).contains("Review passes: 3");   // ✗ passes against the BROKEN code too
```

It is vacuous. The broken code also printed whatever *its own* key said; the bug was never that a
component mis-rendered its input, but that two components had **different inputs**. The literal `3` in
the assertion is supplied by the test author, so the test only proves the component can echo.

What has power is comparing **two independently-derived values**: what the banner is told, versus what
`ReviewOrchestratorFactory#buildConfig(...)` — the exact call the executor makes — resolves. Then the
keys were set to *contradictory* values (real key = 3, retired key = 7) so any drift is forced into
the open.

Proof it works: the fix was reverted in an isolated copy and the test re-run —

```
legacyBannerKeyNoLongerReachesTheBanner:112
[port and executor must resolve the same key]  expected: 3  but was: 7
```

Restoring the fix returned green. ~2 minutes of work; the only actual evidence the test regresses.

## Takeaway

1. **Never assert a value against a literal you chose** when the bug class is "two sources disagree".
   Assert source A == source B, and reach B through the production call path (a `public` builder method,
   the container-resolved bean) rather than recomputing it in the test.
2. **Force the contradiction.** If two inputs could diverge, set them to *different* values in the test.
   Equal values make broken and fixed code indistinguishable.
3. **Mutation-verify before claiming done.** Reintroduce the defect in a scratch copy (`rsync` to
   `/tmp`, never the shared worktree), run only the new class, confirm red, restore, confirm green.
   Paste the failure text into the artifact — it is the evidence.
4. **Expect only the targeted test to go red.** In t28 the parameterized mapping test stayed green under
   the wiring mutation, correctly: different tests pin different things. Know which of yours is the control.

## History
- 2026-08-06 (multi-agent-code-reviewer/t28): initial — from F3's banner/executor key split.
