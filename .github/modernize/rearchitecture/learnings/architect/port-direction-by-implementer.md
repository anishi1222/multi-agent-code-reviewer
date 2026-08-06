# Port Direction Is Decided By Who Implements It

A port's inbound/outbound classification comes from its implementer's layer, not from the package it was filed in.

## What Happened

During `rearchitecture/t16` (authoring ADR-0006) two ports in `application.port.inbound` turned out
to be implemented by `infrastructure`:

- `ResolveTokenPort` — only implementer is `infrastructure.auth.GitHubTokenResolver`.
- `ExecuteSkillPort` — the DI factory binds it to `infrastructure.copilot.SkillExecutor`, which
  left `application.skill.ExecuteSkillUseCase` as dead code with zero references outside its own
  Javadoc.

Both compiled and both passed the layer-dependency tests, because the tests checked *package*
direction (`infrastructure` may import `application.port`) rather than *implementation* direction.
The defect only surfaced when drawing the layer diagram, where the arrow has to point somewhere.

## Takeaway

- Classify by implementer: **inbound** = implemented by `application`, called by `presentation`;
  **outbound** = implemented by `infrastructure`, called by `application`.
- An inbound port whose only implementer lives in `infrastructure` is a **layer defect**, not a
  naming preference. It means the use case either doesn't exist or has been bypassed.
- Enforce it structurally: allow `infrastructure` to import `application.port.outbound` **only**.
  Scoping the rule to `application.port` is too wide and lets this class of defect through.
- Cheap detection: for every inbound port, grep for its implementers; for every use-case class,
  grep for its callers. A use case with no callers means a port was bound past it.
- Drawing the layer diagram is itself a defect detector — do it before certifying a layer.

## History
- 2026-08-05 (rearchitecture/t16): initial — found via ADR-0006 authoring, recorded as D2
