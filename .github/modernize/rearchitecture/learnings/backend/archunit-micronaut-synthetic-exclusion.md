# ⚠️ SUPERSEDED — Do Not Filter Micronaut Synthetics With a Blanket Regex

**This learning's original advice caused a production defect. Both of its recommendations were wrong.** See `archunit-java27-bytecode-ceiling.md` for the replacement approach.

## What The Original Advice Was

To silence rule failures caused by Micronaut-generated `$ClassName$Definition` classes, it recommended:
1. `haveNameNotMatching(".*\\$.*")` to exclude all generated classes from boundary rules, and
2. `archRule.failOnEmptyShould=false` in `archunit.properties`.

## Why It Was Wrong

**(1) The blanket filter destroys real signal.** Generated classes are not uniformly noise. Measured on this codebase:

- The 3 `@Factory` `$...$Definition` classes do **not** violate the infrastructure rule — the factory *method bodies* reference implementations, the DI metadata does not.
- `$ReviewApp$Definition` **genuinely does** violate the presentation-leaf rule, because it records `ReviewApp`'s injection points, faithfully mirroring a real violation in `ReviewApp` itself.

A `.*\$.*` filter erases the true positive along with the false ones. **Generated classes belong in scope; name the specific exemptions instead.**

**(2) `failOnEmptyShould=false` disables the most valuable safety net there is.** That switch suppresses the "this rule matched no classes" error. On this project it hid the fact that the analyzer could only read 107 of 687 classes, so every rule was vacuous while reporting green. The two recommendations interlocked: the filter discarded the only classes the analyzer could see, and the flag suppressed the resulting emptiness error.

## Takeaway

- **Never exclude by name pattern.** Exempt specific, named classes with a written rationale.
- **Assert exclusions match violations *exactly*** — `violationsIgnoringExclusions == exclusionSet`, not `subset-of`. This makes the rule self-cleaning: it fails on an unknown violator *and* on a stale exemption. See `self-cleaning-architecture-exclusions.md`.
- **Never disable emptiness failures.** If a rule legitimately matches nothing during a transitional phase, assert the expected count explicitly so the number is visible and reviewable.
- On Java 24+, the underlying tooling assumption is also invalid — see `archunit-java27-bytecode-ceiling.md`.

## Example

```java
// WRONG - what this file used to recommend
.and().haveNameNotMatching(".*\\$.*")   // discards true positives too

// RIGHT - named exemptions, asserted exactly
private static final Map<String, String> RULE3_EXEMPT = Map.of(
    "dev.example.ReviewApp",
    "composition root; blueprint t4 section 1 relocation tracked in ADR-0006",
    "dev.example.$ReviewApp$Definition",
    "Micronaut DI metadata mirroring ReviewApp's injection points");

assertThat(violationsIgnoringExclusions)
    .containsExactlyInAnyOrderElementsOf(RULE3_EXEMPT.keySet());
```

## History
- 2026-08-05 (multi-agent-code-reviewer/t12): initial
- 2026-08-05 (multi-agent-code-reviewer/t12.1): **superseded** — its advice produced a false green across all 6 boundary rules; rewritten as a cautionary record
