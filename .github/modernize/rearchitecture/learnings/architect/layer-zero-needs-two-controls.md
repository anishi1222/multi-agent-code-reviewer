# Layer Zero Needs Two Independent Controls

A composition root is conformant only when both its incoming dependency direction and its internal responsibilities are constrained.

## What Happened

During `multi-agent-code-reviewer` t17, the current tree had no source reference from a named layer
to either root-package class. That looked like ADR-0006 D1's "the root is never a component"
constraint was satisfied.

Two independent checks disproved certification:

1. An isolated application class with a field typed as root `ReviewPortFactory` passed all 15
   architecture tests. The suite checked root-to-presentation exemptions, package membership, and
   cycles, but never the forbidden layer-to-root direction.
2. Source responsibility inspection found that root `ReviewApp` still parsed global CLI options,
   printed usage/errors, dispatched commands, created/chmodded a directory, and switched concrete
   logging configuration. Even a perfect incoming-edge rule would not detect this because all of
   those dependencies are legal for layer zero; the responsibilities are not.

The inverse failure also existed: `ApplicationPortFactory` was treated as composition wiring while
remaining in infrastructure, but contained trust classification, I/O, SDK construction, and
configuration mapping. Moving it into the root without first splitting those responsibilities would
make the dependency graph greener while making layer zero less conformant.

## Takeaway

- Enforce **no named layer depends on a direct root-package type** with a bytecode rule and a real
  one-way mutant.
- Separately inspect or constrain **what root code does**: constructor/bean binding and process start
  only; no parsing, formatting, policy, configuration mapping, or external I/O.
- A root package's permission to name every layer is not permission to own every responsibility.
- Move only proven wiring into layer zero. Split policy and adapter behavior before relocation.
- Certification needs both controls; either one alone leaves a different false-green path.

## History

- 2026-08-07 (`multi-agent-code-reviewer`/t17): initial; discovered a passing layer-to-root mutant
  and two non-wiring layer-zero candidates during final conformance review.
