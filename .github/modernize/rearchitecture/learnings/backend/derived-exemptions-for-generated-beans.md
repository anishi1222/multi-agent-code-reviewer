# Deriving Architecture-Rule Exemptions for Generated Bean Definitions

Never hard-code Micronaut `$…$Definition` names in an architecture-rule exemption list — they are named by method declaration index and rot silently; derive them from their declaring class instead.

## What Happened
multi-agent-code-reviewer/t16.1. Narrowing Rule 4 to `application.port.outbound` surfaced 11
violators, 6 of which were generated `$…$Definition` classes mirroring the forbidden dependency of
an already-exempt composition-root class.

The obvious fix — add the 6 FQNs to the exemption set — is a trap. Micronaut names each generated
definition after the factory method's **declaration index**:
`$ApplicationPortFactory$ExecuteSkillPort5$Definition`. Inserting any method above it renumbers
every definition that follows, so the exemption list breaks without anyone touching the rule.

The opposite trap is the blanket "skip anything containing `$`", which silently exempts real
violations in generated code.

## Takeaway
Derive generated exemptions, admitting a generated class only when **both** hold:
1. its declaring source class is **already** exempt, and
2. its forbidden dependencies are a **subset** of that source's.

That is provably non-loosening: a generated class can never introduce a violation its source did
not already have. Verified empirically — during the RED phase `$GitHubTokenResolver$Definition` was
correctly **not** exempted (its source was not exempt), while the `$ApplicationPortFactory$…`
mirrors were.

Two supporting practices:
- **Keep the helper rule-local** if `assertNoViolations` is shared with other rules. Blast radius
  of a shared change is every rule; blast radius of a local one is a single rule.
- **Append new factory methods at the end** and say so in the method's Javadoc, so indices
  0..n-1 stay stable.
- If the assertion is exact-equality (violators == exemptions), guard against exempting a class
  with no forbidden deps — it registers as a stale exemption and fails.

## History
- 2026-08-05 (multi-agent-code-reviewer/t16.1): initial
