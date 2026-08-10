# An ADR's own ordering constraint outranks the task framing

Applies to: any task that says "implement <ADR>'s D-item N".
Discovered: t31 (architect).

## The situation

t31 asked for two things: add ADR-0007's missing Rule 4b, and resolve the violation it exposes.
Both true, both scoped. The obvious execution: write the rule, watch it go red on `McpServerSpec`,
delete the offending call, watch it go green. Done.

ADR-0007 also contained, in a section the task never referenced, a **HIGH migration risk**:

> **D5 must not be performed before D6.** Removing the wrapper without the replacement in place
> is a net loss of defence.

The obvious execution *is* the forbidden sequence. D6 was unimplemented.

## Rule

When a task cites an ADR decision, read that ADR's **risk, ordering, and consequences sections**
before executing — not just the cited decision. A task description is a pointer, not a
specification. The ADR is the specification, and it can constrain the order in which its own
items may be discharged.

Execute in the ADR's order and say so in the artifact, so the divergence from the task's implied
order is visible rather than looking like scope creep.

## The subtler trap

I measured the old control and found it protected **nothing** (the SDK overrode no `toString()`,
and nothing logged the object). That reads like a licence to skip the ordering constraint — "there
is no defence to lose."

It is not. The constraint held for a *different* reason than the ADR gave: the old control and the
existing sink masked by different criteria (header **name** vs value **shape**), and the old one
uniquely covered opaque custom header values. That single delta is what the replacement had to
close. **A null result on the stated rationale does not discharge a constraint — re-derive whether
it holds for another reason before reordering.**

## Generalisation

Ordering constraints are usually written down once, far from where the work is assigned. Grep the
ADR for 順序 / order / "must not" / risk before starting, not after.
