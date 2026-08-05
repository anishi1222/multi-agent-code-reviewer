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
