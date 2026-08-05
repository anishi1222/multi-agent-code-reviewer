# Composition Root Is Layer Zero

Treat the DI/entry-point root package as an explicit layer with its own rules, rather than as an exemption from the layer rules.

## What Happened

In `rearchitecture/t16`, `ReviewApp` (root package, `mainClass`, DI assembly) violated the layer
matrix by construction: a composition root must see every layer. The obvious fix — move it down
into `presentation` — was wrong on inspection:

- it trades a documented exemption for a **stricter** `presentation → infrastructure` violation,
  since wiring necessarily touches adapters;
- it breaks `mainClass` in 4 places and 2 GraalVM `reachability-metadata.json` files;
- it invalidates the logger name `d.l.reviewer.ReviewApp` that `docs/runbook.md` asserts verbatim
  in sample log output.

The inversion worked better: keep `ReviewApp` in the root and move the 3 Micronaut `@Factory`
classes **up** from `infrastructure.copilot` into the root. That removed 3 rule exemptions and
converted a rule's ad-hoc class-name allowlist into one bounded package. Net exemptions decreased,
and the runbook needed no edit.

## Takeaway

- Model the composition root as **layer 0** in the allowed-imports matrix, with explicit rules:
  wiring only, no business logic, and **never referenced by any other layer**.
- Prefer moving *wiring* up into the root over moving the *entry point* down into a layer.
- Before proposing to relocate a class, grep for what asserts its fully-qualified name:
  build config `mainClass`, native-image metadata, logger names in docs, reflection config.
  An FQN asserted outside the source tree is a hidden coupling.
- Judge a placement decision by **net exemption count**, not by whether one specific rule goes
  green.

## History
- 2026-08-05 (rearchitecture/t16): initial — recorded as ADR-0006 D3
