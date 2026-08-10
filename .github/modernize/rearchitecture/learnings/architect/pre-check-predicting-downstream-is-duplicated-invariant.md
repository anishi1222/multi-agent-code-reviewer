# A Pre-Check That Must Predict a Downstream Computation Is a Duplicated Invariant

When an admission gate and a later hard limit disagree, the fix is almost never "make the
admission gate predict the limit."

## The situation

Five budget checks guarded one resource. An early admission gate (infrastructure) counted
cumulative admitted content; a late gate (domain) counted the *rendered* output and threw. The
rendered form is always larger — fixed header and per-item markup, plus unbounded placeholder
expansion — so a narrow window exists where admission passes and rendering aborts.

The proposal was to make the admission gate a true pre-check for the render gate.

## Why that is the wrong direction

To predict the render, the infrastructure gate must track the domain class's header text, its
per-item markup, and its placeholder substitution — **forever**. That is:

- a **duplicated invariant**: two places must agree about one formula, with nothing enforcing it;
- an **inward knowledge leak**: infrastructure now depends on domain's *rendering format*. No
  import-level layering rule catches this, because no import is added — which makes it worse,
  not better, than an ordinary violation.

Adopting it would re-create "the control's scope is invisible at its call site" in a new place
while claiming to fix that exact pattern.

## The actual defect

Four gates skipped-and-warned; one threw. **Two controls over one resource with opposite failure
modes** is the inconsistency worth removing — not the arithmetic gap between them.

## The remedy

Fix the *late* gate, not the early one:

1. Give the domain class the effective budget as an **injected pure value**, not a static
   constant read. (If layer purity forbids the domain from importing the config type, that is a
   signal the value must be carried inward — the same move that produced the project's existing
   budget value object.)
2. Make its breach **graceful** — drop the overflowing item and warn — matching the shape every
   other gate already uses.

One change; it resolves the hardcoded-ceiling defect, the early/late divergence, and the
throw-vs-skip inconsistency, and it needs no new configuration key.

## Generalisation

**Prefer removing a redundant control over synchronising two controls.** If a late check is
genuine defence-in-depth, it should degrade like the primary control and read the same configured
value. If it cannot be made to do either, it is not defence-in-depth — it is a second, divergent
policy wearing the first one's name.

## Corollary on splitting a shared knob

When one configuration key is found to govern several distinct quantities, the instinct is to
split it — a breaking contract change. Prefer **additive with fallback**: introduce specific keys
that each default to the existing key. Nothing is removed, every existing deployment behaves
identically, and the cost collapses from "breaking change + migration notes" to "one ADR".
