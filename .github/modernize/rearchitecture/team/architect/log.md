## [t2] Analyze current architecture — dependency cycles and framework leakage
- Recon reported 6 cycles; actual count is 10 (4 additional intra-report cycles and the util→agent was actually bidirectional)
- `TemplateService` is the single biggest cycle hub — involved in 5 of 10 cycles
- `AgentConfig` + `ReviewResult` are shared domain models trapped in wrong packages — create 5 cycles
- SLF4J is in 50/120 files — domain purity requires either dropping logging or switching to j.u.l.
- Micronaut `@Nullable` is used as a simple nullability annotation in many domain-like classes — can be replaced with `java.util.Optional` or removed
- `agent` package (30 files) is the hardest decomposition target: mixes 5 different responsibilities across all target layers
- Learnings consumed: (none)
