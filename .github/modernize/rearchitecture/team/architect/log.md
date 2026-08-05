## [t2] Analyze current architecture — dependency cycles and framework leakage
- Recon reported 6 cycles; actual count is 10 (4 additional intra-report cycles and the util→agent was actually bidirectional)
- `TemplateService` is the single biggest cycle hub — involved in 5 of 10 cycles
- `AgentConfig` + `ReviewResult` are shared domain models trapped in wrong packages — create 5 cycles
- SLF4J is in 50/120 files — domain purity requires either dropping logging or switching to j.u.l.
- Micronaut `@Nullable` is used as a simple nullability annotation in many domain-like classes — can be replaced with `java.util.Optional` or removed
- `agent` package (30 files) is the hardest decomposition target: mixes 5 different responsibilities across all target layers
- Learnings consumed: (none)

## [t4] Design target layered architecture, full package mapping, and port catalog
- Produced 3 detail files: packages (24 target packages), ports (12 interfaces), classmap (120→138 files)
- Key design: 5 inbound + 7 outbound ports; LoadTemplatePort breaks 5 cycles; domain type moves break 5 more
- Learnings consumed: [architect/shared-domain-types-cycle-roots, architect/template-service-cycle-hub]
- RetryExecutor→SharedCircuitBreaker cycle resolved by parameterizing circuit breaker (pass via method arg, not import)
- Cycle 9 (finding⇄formatter) resolved by making FindingsExtractor produce data only, Formatter consumes — no mutual ref
- domain.report is largest sub-package (~18 files) — may benefit from further sub-splitting during implementation
- DoctorCommand needs new RunDiagnosticsPort to avoid SDK types in presentation

## [t16] Authored ADR 0006 and re-synced all user-facing docs to the implemented layering

- **Docs must be verified against source, not against upstream artifacts.** t4's port catalog
  (§2.1) still showed a superseded single-result `RunReviewPort`, and the README mermaid named
  8 classes that no longer exist. Had I documented from the artifacts alone, ADR-0006 would have
  shipped a wrong contract. Every structural claim in this task was re-derived from HEAD.
- **The port-direction defect was found while writing docs, not while reviewing code.** Trying to
  draw the layer diagram forced the question "who implements this port?" — which immediately
  exposed that `ResolveTokenPort` and `ExecuteSkillPort` are inbound-by-package but
  infrastructure-by-implementer. Diagramming is a cheap defect detector; the arrow has to point
  somewhere and a misclassified port makes it point the wrong way.
- **`ExecuteSkillUseCase` is dead code**: `ApplicationPortFactory` binds the inbound port straight
  to `infrastructure.copilot.SkillExecutor`, so the application-layer use case has zero references
  outside its own Javadoc. A grep for "who calls this" is worth running on every use-case class
  before certifying a layer.
- **Wrong initial assumption: "move `ReviewApp` down into `presentation`."** It looked like the
  obvious fix for the Rule 3 exemption. It is not — it trades a documented exemption for a
  *stricter* `presentation → infrastructure` violation, and breaks `mainClass` in 4 places, 2
  GraalVM `reachability-metadata.json` files, and the `d.l.reviewer.ReviewApp` logger name that
  `docs/runbook.md` asserts verbatim. Moving the 3 `@Factory` classes *up* into the root instead
  reduces net exemptions and left runbook.md needing no edit at all. Check what asserts a class's
  FQN before proposing to move it.
- **Technique worth reusing:** for line-for-line parallel EN/JA docs, splice with a throwaway
  script that (a) asserts its anchor lines before touching anything and (b) replaces regions
  bottom-up so earlier offsets stay valid. First run failed its own assertion because
  `'│   └── util/'.strip()` does not start with `└──` — box-drawing glyphs are not whitespace.
  Assert on `in`, not `startswith`, when tree glyphs are involved.
- **Historical ADRs: cross-link, don't rewrite.** 0001/0002/0003 are still Accepted; only their
  file paths moved. Adding one "still valid, location moved" line to each References section
  preserves the decision history while removing dead paths.
- Learnings consumed: [architect/port-catalog-design, architect/domain-subpackage-organization,
  architect/shared-domain-types-cycle-roots, architect/template-service-cycle-hub,
  teamlead/layer-naming-conventions, teamlead/domain-purity-rules]
- **A concurrent task landed mid-write and invalidated part of the ADR.** t13.1 shipped while t16
  was drafting: it named the logging port `PropagateCorrelationPort` (I had written
  `LogExecutionPort`), numbered the new rule `5b` (I had written `Rule 7`), and closed two of the
  seven Known deviations. Re-running the verification sweep *immediately before* writing `[DONE]`
  caught all of it. **Verify twice on a documentation task: once to write, once to publish.** An
  ADR that names a port which does not exist is worse than no ADR.
- Backend's rule-numbering choice (`5b` inserted in position, not `7` appended) is better than
  mine and is now the recorded convention — prior learnings cite Rules 6a/6b by number, and
  appending would have been fine but inserting keeps the dependency-direction rules contiguous.
- Add a **Status** column to any "Known deviations" table in an ADR. Without it the table reads as
  a permanent indictment; with it, later tasks can close rows in place and the ADR stays live.
