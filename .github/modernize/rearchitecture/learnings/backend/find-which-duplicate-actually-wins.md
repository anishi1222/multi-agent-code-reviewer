# Find Out Which Duplicate Actually Wins Before Deleting One

When a finding says "the default is declared twice", enumerate every source and prove by mutation which one the runtime uses — deleting the inert copy fixes nothing.

## What Happened

`multi-agent-code-reviewer` / t27 (F2). The finding described two sources of truth for eight
budget defaults: constants in `shared/PromptBudget`, and `@Bindable(defaultValue=…)` literals
on the config record. It prescribed deleting the annotations.

Grepping for the key prefix instead of just reading the two named files turned up a **third**
source: `src/main/resources/application.yml` set all eight keys to the same values.

To find out which one won, I set one annotation to `424242` and started a context. The bean
bound `12000` — the yaml value. So the `@Bindable` literals were **unreachable dead code in
every shipped configuration**, and the finding's stated drift mechanism ("the annotation
default silently wins") named the wrong source. Deleting only the annotations would have
removed the harmless duplicate and left the harmful one, while looking like a fix.

## Takeaway

Two habits, both cheap:

1. **Enumerate by searching for the identifier, not by reading the files the finding names.**
   Findings undercount. Config files, defaults in factories, and test fixtures are all sources.
2. **Prove precedence with a discriminating mutant.** Set one candidate to a value no other
   source uses and observe what the runtime produces. If all copies agree, no observation can
   tell them apart — which is exactly the condition that lets drift ship unnoticed.

Corollary for the test: when the duplication is behaviour-neutral, no behavioural assertion can
detect its reintroduction. You need a **structural** guard (reflection over the annotation, a
scan of the config file) or the control is vacuous.

## History

- 2026-08-06 (multi-agent-code-reviewer/t27): initial
