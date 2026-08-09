# Liveness Enumerations Can Self-Satisfy

A source-scan test can count its own parameter list as evidence and turn a name-only citation into a false behavioral guarantee.

## What Happened

In `multi-agent-code-reviewer` task t34, `AgentPolicyConstantsAreLiveTest` counted every source
line naming a constant. Its own `@ValueSource("ALLOWED_MODEL_PREFIXES")` therefore satisfied
`testUses > 0`; merely adding the name would have gone green without testing policy behavior.

The closure used a separate suite: reflect the private configured set, compare it with an
independent expected set, and call the public validator with matched accepted/rejected boundary
inputs for every prefix.

## Takeaway

Exclude a liveness test's own inventory from its evidence, or require a separate behavioral
test. For finite private allowlists, pin both the exact set and public behavior: exact prefix,
extension, case-folded form, truncated form, and non-leading lookalikes.

## History

- 2026-08-08 (multi-agent-code-reviewer/t34): initial
