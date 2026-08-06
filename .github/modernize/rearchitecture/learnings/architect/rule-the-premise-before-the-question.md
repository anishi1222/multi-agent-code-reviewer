# Rule the Premise Before Ruling the Question

When a decision item arrives with a factual premise baked into it, verify the premise first — twice now the premise was false.

## What Happened

`multi-agent-code-reviewer` rearchitecture, t24 (and earlier, ADR-0006 D3).

Decision item 3-A was handed to me as: *"`reviewPasses`/`sharedSessionEnabled` — a multi-pass
capability with **no YAML surface** and **no test exercising `reviewPasses > 1`**. KEEP or REMOVE?"*
Phrased that way it reads as an obvious REMOVE: unsurfaced, untested code.

Both halves were false. `sharedSessionEnabled` had a live CLI flag (`--no-shared-session`,
parsed at `ReviewOptionsParser:211`, documented at `CliUsage:48`) and was a field on an **inbound
port DTO**. `reviewPasses` had a bindable Micronaut key
(`reviewer.execution.concurrency.review-passes`) and **three** tests exercising values > 1.
The premise had confused "absent from the shipped `application.yml`" with "has no configuration
surface" — but `@Bindable` binds from user YAML, env vars and system properties regardless.

Had I answered the question as posed, I would have deleted a documented CLI flag and a port field.

This is the second instance. ADR-0006 D3 was written on the premise that three classes named
`*Factory` were all Micronaut `@Factory` beans; only one was. D3's prescribed action would have
moved business logic and an inbound-port implementation into the composition root, contradicting D1.

## Takeaway

A decision item's premise is an *input to verify*, not a *given*. Before ruling:

1. Restate the premise as a checkable claim ("no test exercises X", "no config surface for Y").
2. Check it with a command, not by reading the upstream artifact that asserted it.
3. If false, **say so explicitly and rule on the corrected facts** — do not quietly answer the
   question as asked, and do not bounce it back unanswered.

Watch for these premise smells specifically:

- *"no configuration surface"* — check for annotation-declared keys (`@Bindable`, `@Value`,
  `@ConfigurationProperties`), not just the shipped config file. Absence from a shipped default
  file is not absence of a surface.
- *"no test covers it"* — check for a test that *looks* like it covers the control but trips a
  different, earlier control. A per-item cap and a cumulative budget are different controls.
- *"these are all X"* — check each one (the D3 `*Factory` naming trap).

The framing usually arrives from someone acting in good faith who checked one artifact rather than
the code. The cost of verifying is one grep; the cost of not verifying is deleting live capability.

## Example

```bash
# premise: "no YAML surface"
grep -rn "review-passes" src/main/resources/     # absent from shipped yml — premise looks true
grep -rn "reviewPasses" src/main/java/**/config/ # @Bindable(defaultValue = "1") — premise false
```

## History

- 2026-08-06 (multi-agent-code-reviewer/t24): initial, from decision item 3-A; cross-referenced
  the earlier ADR-0006 D3 instance.
