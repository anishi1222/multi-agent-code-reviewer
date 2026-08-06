# A Purity Rule Is Not a Membership Rule

`shared` needs a criterion for what may *live* there, not just a constraint on what it may *import* — purity is the weakest possible gate.

## What Happened

`multi-agent-code-reviewer` rearchitecture, t24. Ruling on whether `PromptContentCompactor`,
`PromptBudget` and `ConfigDefaults` belonged in `shared` after the upstream merge.

ADR-0006 enforces `shared` purity mechanically (Rule 2: `shared` may import only `java.*`;
34 classes, 0 violators, 0 exempt). That rule is sound and it passed. But it constrains only what
`shared` *imports*. Nothing constrains what may be *placed* there. Any logic at all — including
core business rules — can be parked in `shared` provided it imports nothing but the JDK, and every
architecture rule stays green.

That makes `shared` the layer with the strictest purity requirement and the weakest natural
resistance to accretion. It is the path of least resistance for anything awkward: a type needed by
two layers, a helper that would otherwise force a dependency edge, a constant nobody wants to own.
Left alone it becomes the junk drawer the rearchitecture existed to eliminate.

Ruling #4 I found myself applying a criterion that was nowhere written down: *referenced by ≥2
layers, and carrying no business vocabulary*. `PromptContentCompactor` passed it — consumed by
`domain` and `application`, zero imports, signatures are `(String, int)` with no domain types.
I explicitly rejected the alternative of moving it to `domain`: since `application → domain` is
legal, `domain` would have satisfied both consumers too, so "two layers use it" alone does not
justify `shared`. The deciding factor was the absence of business vocabulary — it is a
character-budget string mechanism, not a review rule.

## Takeaway

For any layer defined by a *negative* constraint (may not import X), write a matching **positive
membership criterion**, or the layer will accrete whatever satisfies the negative constraint.

For a `shared`/`common`/`util` layer the criterion that works:

> Admit only mechanisms and constants that are **referenced by two or more layers** *and* **carry
> no business vocabulary** in their names or signatures. A type referenced from a single layer
> belongs in that layer.

Note the two clauses are both required. "Used by 2+ layers" alone is insufficient whenever the
layers are already permitted to depend on one another — the inner layer is then the correct home.

Corollary, from the same ADR: a convention stated in prose with no enforcement rule behind it is
the same defect class as a matrix row with no rule. ADR-0006 D6 requires simple class names to be
unique and even notes this is mechanically checkable — yet nothing checked it. It happened to hold
(0 duplicates across 175 files), but that was diligence, not enforcement.

## History

- 2026-08-06 (multi-agent-code-reviewer/t24): initial, from decision items 3-B #4 and 3-D.
