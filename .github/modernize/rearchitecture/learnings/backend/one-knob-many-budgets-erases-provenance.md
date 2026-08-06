# One Knob Governing Many Budgets Erases Provenance

When a single config value is aliased into one field and reused by several gates, each gate stops declaring which budget it enforces — name the budgets separately even when they share a value.

## What Happened

`multi-agent-code-reviewer` / **t26**. `skillConfig.maxParameterValueLength()` was assigned to a single
`AgentConfigLoader.maxSkillPromptLength` field and compared against **five** different quantities across
the codebase (the brief said three — counting them myself found two more).

Two problems only became visible once the sites were listed side by side:

- **Unit conflation.** One gate compared `Files.size()` in *bytes* against the same integer other gates
  compared *UTF-16 chars* against. Measuring the real corpus showed 27 files where `bytes != chars`
  (up to 2×), though none currently mis-gated — a **latent**, locale-dependent limit, not an active bug.
- **Divergent failure modes.** Three sites skipped with a warning; two threw. One of the throwing sites
  read the hardcoded default instead of the configured value, so *raising* the knob to silence a warning
  converted a graceful skip into a hard crash.

The fix that fit inside the backend charter was not new config keys (a user-facing contract change
needing an ADR) but **restoring provenance**: three named, unit-bearing fields
(`maxSkillFileBytes`, `maxSkillContentChars`, `maxAssignedSkillTotalChars`), all still initialised from
the one knob, with a comment that they are not interchangeable. Behaviour is bit-identical.

## Takeaway

- Before defending or changing a shared limit, **enumerate every comparison site yourself**. Briefs
  undercount, and the extra sites are where the interesting defects live.
- **Name each budget after what it measures and in what unit**, even when the values are currently equal.
  A field named for one meaning (`maxParameterValueLength`) reused for four others is how the drift hides.
- Splitting names is behaviour-preserving and in-charter; adding **config keys** is a contract change —
  escalate that half rather than doing both or neither.
- Check units before asserting impact: `Files.size()` is bytes, `String.length()` is UTF-16 chars.
  Measure the actual corpus before calling a unit bug "active" — ours turned out latent.

## Example

```java
// Before — one alias, three gates, no provenance at the call site
private final int maxSkillPromptLength;

// After — same value, but each gate now declares its own budget and unit
/// Maximum size of one skill file **on disk, in bytes**, checked before parsing.
private final int maxSkillFileBytes;
/// Maximum **character** length of one skill's injected content.
private final int maxSkillContentChars;
/// Maximum **cumulative character** length of all skills assigned to one agent.
private final int maxAssignedSkillTotalChars;
```

## History

- 2026-08-06 (multi-agent-code-reviewer/t26): initial
