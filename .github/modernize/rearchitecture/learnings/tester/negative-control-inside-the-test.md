# Negative Control Inside the Test, Not the Source

How to prove a guard clause is load-bearing when your charter forbids mutating production code.

## What Happened

Project: `anishi1222/multi-agent-code-reviewer` — Ports & Adapters rewrite, task `t14` (tester).

The coordinator asked me to prove that the `InterruptedException` guard in
`RetryPolicyUtils.isTransientException` still short-circuits after a utility consolidation widened
the transient-marker set from 5 to 11 markers.

The obvious proof is mutation testing: delete the guard, watch a test go red. But the tester charter
forbids touching production source, and mutation tooling wasn't in the build. A plain assertion —
`isTransientException(new InterruptedException("boom"))` is `false` — proves nothing, because
`"boom"` contains no transient marker. That test passes identically with the guard deleted. It is
a **vacuous** test that looks like coverage.

The fix was to put the control *inside* the test by pairing two inputs that differ in exactly one
dimension:

```java
assertThat(RetryPolicyUtils.isTransientException(new RuntimeException("connection reset"))).isTrue();
assertThat(RetryPolicyUtils.isTransientException(new InterruptedException("connection reset"))).isFalse();
```

Identical message; only the type differs. The first assertion proves the message *does* classify as
transient, so the second can only be `false` because of the `instanceof InterruptedException`
guard. Delete the guard and the second assertion necessarily flips. The test cannot pass vacuously.

## Takeaway

When asserting that a guard/branch is responsible for an outcome, don't assert the outcome alone —
assert a **matched pair** that isolates the branch:

1. An input that takes the *other* path and produces the opposite result (the control).
2. The input under test, differing in exactly the one dimension the guard keys on.

Ask of every guard test: *"if someone deleted this guard, would this test fail?"* If the honest
answer is "probably not", the test is decorative. This works for null guards, type guards,
feature-flag branches, and deny-lists, and it needs no mutation framework and no source edit.

Same principle as the architecture-rule negative control in
`backend/architecture-rule-negative-control` — a rule that has never been observed to fail is
indistinguishable from a rule that cannot fail.

## Corollary: test the *wiring*, not just the logic (t25)

A green unit test for a helper proves the helper works. It proves **nothing** about whether any
caller invokes it. `PromptContentCompactorTest` had 6 passing tests while
`RubberDuckPromptBuilder`'s call into the compactor was entirely uncovered — a merge could have
deleted the call and the suite would have stayed green.

Apply the same matched-pair shape one level up, at the caller:

```java
// control: flag off  -> full content survives
assertThat(peerPrompt(budget(false, 12))).isEqualTo("PEER:" + FULL);
// under test: flag on -> compacted
assertThat(peerPrompt(budget(true,  12))).isEqualTo("PEER:" + TAIL);
```

Two refinements that made this sharper:

- **Assert the exact expected string, not "it got shorter".** `"PEER:" + TAIL` vs `"PEER:" + HEAD`
  distinguishes `compactKeepingTail()` from `compact()`. "Shorter" would accept either, so a
  caller swapping one for the other would go unnoticed.
- **Add a second pair on the configuration dimension.** Holding the flag `true` and varying only
  the budget (small vs default) proves the value is *read from config*, not hardcoded. One pair
  guards the branch; the other guards its provenance.

## History
- 2026-08-05 (multi-agent-code-reviewer/t14): initial
- 2026-08-06 (multi-agent-code-reviewer/t25): added the caller-side/wiring corollary — helper tests
  don't cover the call site; assert exact strings and add a config-provenance pair.
