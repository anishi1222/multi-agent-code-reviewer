# Empty Violator Set Needs a Permanent Control

A rule with 0 violators and 0 exemptions observes nothing, and passes identically when its predicate is broken — ship it with a fixture-based negative control or it is decoration.

## What Happened

`rearchitecture/t30`. `LayerDependencyRulesTest` proves a rule works by asserting
*violations-ignoring-exemptions* **equals** the declared exemption set. That is a strong check — it
fails when a rule goes stale — but it only has force when at least one of the two sets is non-empty.

Rule 8 was added *after* backend had already removed its single violator, so it arrived at 0
violators and 0 exemptions. The assertion compared `[]` to `[]`. It would have passed just as
happily if the constant name were misspelled, the predicate inverted, or the bytecode reference it
depends on absent. The rule was green and blind.

Worse, no naturally-occurring probe existed: every in-tree consumer of the target type also called
one of its helper *methods*, so nothing exercised the constant-read-only path the rule was written
to catch.

The mechanism intended to prevent "a control whose scope of application is invisible" had itself
become a control whose scope of application was invisible.

## Takeaway

- When adding a rule whose blast radius is **already zero**, treat that as a warning, not a win. Ask
  what observable difference exists between this rule working and this rule being broken. If the
  answer is "none", the rule is not yet finished.
- Ship a **permanent negative control**: a fixture in the *test* tree (so it can never become a
  subject of the rule) that the detector must positively identify. Assert the detection primitive,
  not the rule's conclusion.
- Then **prove it once, live**: reintroduce the real violation in the real source tree, watch the
  rule go red and name the right violator and edge, and revert. A control you reasoned about is not
  a control you measured.
- This matters most when detection rests on **compiler behaviour rather than a spec guarantee** — a
  toolchain change should turn the control red (reporting that the rule went blind), instead of
  leaving the rule green forever while enforcing nothing.

## Example

```java
// Fixture lives under src/test — never a subject of the rule itself.
static final class InlinedConstantReadProbe {
    static boolean over(int n) { return n > ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH; }
}

@Test
void rule8DetectsAnInlinedConstantRead() {
    // Assert the *detection primitive*, not the rule's verdict.
    assertTrue(referencedTypes(load(InlinedConstantReadProbe.class)).contains(CONFIG_DEFAULTS));
}
```

## History
- 2026-08-06 (rearchitecture/t30): initial — Rule 8 shipped at 0 violators / 0 exemptions; control
  added, then verified by reintroducing F4's original form and observing Rule 8 fail correctly.
