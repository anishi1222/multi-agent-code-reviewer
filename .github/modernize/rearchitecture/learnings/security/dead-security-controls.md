# Dead Security Controls

A security class can declare caps, allowlists and result types that are never referenced — grep-count each symbol instead of trusting the class name.

## What Happened

multi-agent-code-reviewer / t18. `CustomInstructionSafetyValidator` reads as a serious control:
`MAX_INSTRUCTION_SIZE`, `MAX_UNTRUSTED_INSTRUCTION_SIZE`, `MAX_INSTRUCTION_LINES`,
`ALLOWED_CHAR_RANGE`, and a `ValidationResult` type. A code reviewer skimming it would conclude
untrusted instructions are size-capped and charset-restricted.

A repo-wide grep showed **each of those five symbols appears exactly once — at its own
declaration**. Only `containsSuspiciousPattern` (the denylist) is ever invoked. No size limit, no
line limit and no charset allowlist was actually enforced.

This is the third instance of the same failure shape on this project: t12.1 found ArchUnit rules
inspecting 107 of 687 classes (silently partial import), and t13.1 G2 found MDC correlation tests
deleted rather than migrated. In all three, the control *reads* as enforced and enforces nothing.

## Takeaway

Never accept a security control's existence as evidence it runs. For every constant, regex,
allowlist and result type in a validator, count references:

- **count == 1** → declaration only → **dead code**. The control does not exist.
- The class *name* is not evidence. `...SafetyValidator` is a claim, not a guarantee.

Trace from the **call site inward** (who calls this validator, and which method?) rather than
reading the class top-down — top-down reading is exactly what makes dead constants invisible.

When fixing a dead control, add a **negative-control test** that supplies violating input and
asserts rejection. Without it the fix is as unfalsifiable as the original.

## Example

```bash
# For each declared constant/type in a validator, count non-declaration references:
grep -rn "MAX_INSTRUCTION_SIZE" src/main/ | wc -l   # 1 == declaration only == DEAD

# Better: which methods of the validator are actually invoked anywhere?
grep -rn "CustomInstructionSafetyValidator\." src/main/
# -> only containsSuspiciousPattern(). The allowlist half never runs.
```

## History

- 2026-08-05 (multi-agent-code-reviewer/t18): initial — found via SEC-H1; generalises the t12.1 vacuous-ArchUnit and t13.1 deleted-MDC incidents into a repeatable detection technique.
- 2026-08-06 (multi-agent-code-reviewer/t18 re-run): a control can be **live and still
  unguarded**. `ALLOWED_CHAR_RANGE` and `ALLOWED_MODEL_PREFIXES` both have 2 `src/main`
  refs (so they pass the `count == 1` dead-symbol test) but **0** `src/test` refs. The
  project's `AgentPolicyConstantsAreLiveTest` asserts both `main > 0` **and** `tests > 0`,
  which is the right shape — but neither constant is in its enumeration, and they cannot
  simply be appended: the second assertion would go red. Closing that kind of gap means
  writing a test that *names* the constant first. Note also that a purely behavioural pin
  (here `bidiOverrideRejectedFromRepository`) does not satisfy a name-based liveness scan
  and can be deleted without the scan noticing.
