# Choose The Injection Seam By Measuring Call Sites, Not By Taste

When a pure `domain` class needs a configured value, pick where to thread it by counting existing call-site arities first — the cheapest seam is usually not the most obvious one.

## What Happened

`multi-agent-code-reviewer` / t29. `AgentPromptBuilder` (in `domain`) read a limit from
`ConfigDefaults` as a compile-time constant, because ADR-0006 Rule 1 forbids `domain` from importing
`infrastructure.config`. Three seams were available:

1. **Add a parameter to the static builder methods.** The obvious choice — and the worst. It breaks
   3 production and ~15 test call sites, and forces two unrelated collaborators
   (`ReviewTargetInstructionResolver`, `ReviewPassRunner`) to accept and forward a budget neither
   one uses. Threading a value through classes that don't care about it is how "just one parameter"
   becomes a cross-cutting refactor.
2. **Fold it into the existing `PromptBudget`.** Cheap, but re-merges two budgets that an earlier
   task had deliberately split apart to restore provenance.
3. **Add a component to the `AgentConfig` record the builder already receives.** Chosen.

The decision was made by running an arity scan over `new AgentConfig(`: **70 of 71 call sites use
the 8-arg convenience constructor**; the only wide call is inside `Builder.build()`. Adding a 13th
component with null-normalisation in the compact constructor therefore broke **zero** call sites.
That number turned an argument about taste into a measurement.

## Takeaway

1. **Before choosing where to thread a value, `grep` the constructor/factory and count arities.**
   A convenience constructor absorbing most call sites means widening the full one is nearly free.
2. **Prefer the seam that already flows to the consumer.** If the class receives an aggregate, put
   the value on the aggregate. Adding a parameter forces every intermediary to know about it.
3. **Carry it as a named record in `shared`, not a bare `int`.** A `SkillBudget(int
   renderedSkillSectionMaxChars)` names what is measured *and its unit*; an `int` argument silently
   swaps with the next `int` at the call site.
4. **Normalise `null` in the compact constructor to a defaults instance,** so the existing narrow
   constructor can pass `null` and every old call site keeps working unchanged.
5. **State in the producer where the value is *not* enforced.** The loader that reads the config
   deliberately does not apply this budget; without a javadoc note saying so, a later reader
   "helpfully" adds a duplicate check.

## Example

```java
// shared — the unit is in the component name, so it can't be swapped with another int
public record SkillBudget(int renderedSkillSectionMaxChars) {
    public SkillBudget {
        renderedSkillSectionMaxChars = ConfigDefaults.defaultIfNonPositive(
            renderedSkillSectionMaxChars, DEFAULT_RENDERED_SKILL_SECTION_MAX_CHARS);
    }
    public SkillBudget() { this(DEFAULT_RENDERED_SKILL_SECTION_MAX_CHARS); }
}

// domain — 13th component; compact ctor normalises null so the 8-arg ctor (70 call sites) is intact
public record AgentConfig(..., SkillBudget skillBudget) {
    public AgentConfig { skillBudget = skillBudget == null ? new SkillBudget() : skillBudget; }
    public AgentConfig withSkillBudget(SkillBudget b) { return Builder.from(this).skillBudget(b).build(); }
}

// infrastructure — config becomes a value exactly once, at the boundary
applySkills(config, globalSkills).withSkillBudget(new SkillBudget(maxRenderedSkillSectionChars));
```

## History
- 2026-08-06 (multi-agent-code-reviewer/t29): initial — from the F4 remediation.
