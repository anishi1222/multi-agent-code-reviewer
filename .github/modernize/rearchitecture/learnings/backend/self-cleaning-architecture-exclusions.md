# Architecture Rule Exclusions Must Be Exact-Match, Not Subset

**Assert that violations-ignoring-exclusions *equals* the exclusion set exactly.** The usual `violations ⊆ exclusions` form lets unknown violators hide behind broad exemptions and lets stale exemptions rot silently after the code is fixed.

## What Happened

A boundary rule written as "fail if any violation is not in the allowlist" passed for weeks while the analyzer was silently broken. Because the assertion only checked a *subset* relation, an empty violation set trivially satisfied it. The rule could not distinguish "clean" from "inspected nothing."

Rewriting the assertion to require exact equality caught a genuine violator on the very first run — `$ReviewApp$Definition`, Micronaut's DI metadata for a class that itself violated the rule. Nobody had anticipated it. A subset assertion would never have surfaced it.

## Takeaway

One equality assertion delivers three distinct properties that would otherwise need separate tests:

1. **The rule fires.** If exemptions are non-empty, the rule *must* find them. This is a permanent built-in negative control — a rule that silently stops working now fails.
2. **No unknown violator.** Anything new that violates is reported, not absorbed by a broad exemption.
3. **No stale exemption.** When someone fixes an exempted class, the test fails and forces the now-obsolete entry to be deleted. Exclusion lists stop being write-only.

Pair it with two habits:

- **Every exemption carries a written rationale**, stored as a `Map<String, String>` rather than a `Set`. An exemption you cannot justify in one sentence is a violation you have not fixed.
- **Mutation-test the rules once.** Empty each exemption list and confirm the rule fails. A boundary rule nobody has ever seen fail is indistinguishable from one that *cannot* fail.

Also assert an analyzer-completeness gate (parsed count == on-disk class count). Exact-match exclusions verify the rule's *logic*; only a completeness gate verifies its *input*.

## Example

```java
private void assertNoViolations(String rule, Map<String, String> exempt, Set<String> found) {
    // NOT: assertThat(found).isSubsetOf(exempt.keySet())
    assertThat(found)
        .as("%s: exclusions must match violations exactly — an extra entry means an "
          + "unknown violator, a missing one means a stale exemption", rule)
        .containsExactlyInAnyOrderElementsOf(exempt.keySet());
}

private static final Map<String, String> RULE4_EXEMPT = Map.of(
    "dev.example.infrastructure.copilot.ApplicationPortFactory",
    "composition root: wiring impls to ports is a factory's job (t4 section 3)",
    /* ... each entry justified in one sentence ... */);
```

For transitional scaffolding, invert the same idea so it cleans itself up:

```java
@Test
void legacyPackagesAreExplicitlyOutOfCycleScope() {
    // Fails once t13 deletes the legacy tree, forcing this test and the
    // LEGACY_PACKAGES constant to be removed instead of rotting as dead scaffolding.
    assertThat(LEGACY_PACKAGES).isNotEmpty();
}
```

## History
- 2026-08-05 (multi-agent-code-reviewer/t12.1): initial
