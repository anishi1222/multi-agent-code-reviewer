# An Inherited Defect Is Not a Merge-Conformance Finding — But You Must Still Rule On It

A defect that is bit-identical to the pre-merge baseline does not belong in a merge gate's
finding count. Excluding it silently, however, is what makes gates untrustworthy.

## The situation

A merge-conformance gate received a proposed HIGH finding: a domain class gated a rendered
prompt section against a hardcoded compile-time constant, so raising the corresponding
`application.yml` knob could not move the ceiling, and breach threw rather than degraded.

If accepted as HIGH, the gate could not clean-PASS and the downstream task stayed blocked.

## What settled it

Comparing the same expression on both sides of the merge:

| | gate expression |
|---|---|
| upstream `main` | `len > SkillConfig.DEFAULT_MAX_PARAMETER_VALUE_LENGTH` |
| post-merge | `len > ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH` |

and one more line: `SkillConfig.DEFAULT_… = ConfigDefaults.SKILL_…`. Both are compile-time
constants holding the same number. The restructure moved *which class declares the constant*.
Behaviour is unchanged, so against a "no regression vs. baseline" NFR there is nothing to report.

## The rule

**Provenance decides membership in a merge gate's finding count; severity decides its place on
the product backlog. They are different questions and must be answered separately.**

A merge gate asks: *did this merge break something?* A defect present, identically, before the
merge cannot answer yes — regardless of how bad it is. Report it, attribute it upstream, assign
it a product severity, and state explicitly that it is excluded from the conformance count and
why. Never let it fall through the gap between "not my gate's problem" and "someone else's".

## Two guards against motivated reasoning

The downgrade was convenient — it was the only thing standing between the gate and a PASS.

1. **Ask the counterfactual out loud.** "Would I rule this MEDIUM if the verdict did not depend
   on it?" If the grounds stand alone — here: identical to baseline, cited trigger provably
   cannot chain, 61 % headroom in every shipped configuration — the downgrade is sound. If the
   only ground is the gate outcome, it is not.
2. **Record what the change made worse.** The restructure imposed a layer-purity rule that
   forbids the domain class from importing the config type, so the naive one-line fix is no
   longer available and the defect became a design task. Naming that cost is what separates a
   ruling from a whitewash.

## Also

Verify the proposer's trigger scenario, don't inherit it. The "live corroboration" offered here
(an oversized file dropped on every run) was dropped by a *different* gate, emitting a *different*
warning, on a file that a downstream filter would have excluded anyway. It could not reach the
crash site. A severity argument resting on an unverified reachability claim is not a severity
argument.
