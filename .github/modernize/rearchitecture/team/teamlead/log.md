## [t1] Establish migration constitution and layer dependency rules
- Defined 5-layer Ports & Adapters model (presentation/application/domain/infrastructure/shared) with application.port as the contract surface
- Key decision: domain purity = no Micronaut, no Jakarta, no SLF4J, no SDK — only java.* and shared
- Port naming convention: VerbNounPort; adapter naming: TechNounAdapter
- ArchUnit is the sole enforcement mechanism (no JPMS, no multi-module)
- SDK isolation to infrastructure only; framework annotations allowed in infrastructure + presentation only
- Learnings consumed: (none)

## [t5] Implementation plan, task breakdown, and test strategy for Ports & Adapters rewrite
- 16 tasks across 6 phases; split by vertical module (agent, report, skill, etc.), not by technical layer
- Phase ordering follows inward-dependency rule: shared → domain → ports → application → infrastructure → presentation
- Cycle-root types (ReviewResult, AgentConfig, SharedCircuitBreaker) must move first in Phase 1 to unblock all downstream moves
- TemplateService is the cycle hub causing 5/10 cycles — LoadTemplatePort breaks all 5 simultaneously
- ArchUnit dependency added as test-scope (archunit-junit5:1.3.0) — not yet in pom.xml
- Parallel task opportunities: T004/T005 (Phase 2), T006/T007/T008 (Phase 3), T009/T010 (Phase 4)
- Tester owns T015 (regression), architect owns T016 (smoke test) — all other tasks are backend
- Learnings consumed: teamlead/domain-purity-rules, teamlead/layer-naming-conventions
