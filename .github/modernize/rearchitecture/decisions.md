## teamlead — t1 — 2026-08-05

**Decision**: Adopt a 5+1 layer Ports & Adapters model for `dev.logicojp.reviewer`:
`presentation` / `application` / `application.port` / `domain` / `infrastructure` / `shared`.
Dependencies point inward only; `domain` may import only `java.*` and `shared`.
Copilot SDK is confined to `infrastructure`; Micronaut and Jakarta are confined to
`infrastructure` + `presentation`. Port naming is `VerbNounPort`, adapter naming is
`TechNounAdapter`. Zero package cycles. ArchUnit enforces every boundary.

**Rationale**: The current flat-package layout has 6 dependency cycles and leaks the
Copilot SDK into 8 packages, making the domain untestable without the SDK and the CLI
unable to evolve independently. A strict inward-only dependency rule with a machine-
enforced ArchUnit gate is the minimum structure that makes those violations
impossible to reintroduce. Any violation is classified CRITICAL.
