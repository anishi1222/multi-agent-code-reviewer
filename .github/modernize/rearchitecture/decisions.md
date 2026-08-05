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

## architect — t2 — 2026-08-05

**Decision**: Cycle-breaking strategy for the layered rebuild — move shared domain types
(`ReviewResult`, `AgentConfig`, `SkillDefinition`, `SharedCircuitBreaker`) into `domain`,
and break the `TemplateService` hub by defining `LoadTemplatePort` in `application.port`
with an infrastructure adapter. Service-to-service dependencies are inverted via outbound
ports rather than direct imports.

**Rationale**: Analysis of 120 files across 15 packages found 10 dependency cycles (4 more
than the recon estimate of 6). `TemplateService` alone is the hub of 5 of them and is
imported by 8+ classes across 4 packages, so a single port extraction removes half the
cycle graph. The remaining cycles all trace to shared mutable domain types living in
feature packages (`ReviewResult` in `report.core`, `AgentConfig` in `agent`); relocating
them to `domain` makes the dependencies inward-only by construction. Framework leakage
measured at 20 files (Copilot SDK), 24 (Micronaut), 32 (Jakarta), 50 (SLF4J).

## architect — t4 — 2026-08-05

**Decision**: Target design is 6 layers / 24 packages with a 12-interface port catalog —
5 inbound (`RunReviewPort`, `LoadAgentPort`, `ExecuteSkillPort`, `GenerateReportPort`,
`RunDiagnosticsPort`) and 7 outbound (`LoadTemplatePort`, `RunCopilotSessionPort`,
`RunRubberDuckSessionPort`, `ManageCopilotClientPort`, `CollectLocalSourcePort`,
`WriteReportPort`, `GenerateAiSummaryPort`). All 120 files have an assigned target package.

**Rationale**: `LoadTemplatePort` alone resolves cycles 2, 5, 7, 8 and 10 by replacing the
8+ direct `TemplateService` imports with a single outbound contract. The remaining five
cycles (1, 3, 4, 6, 9) are resolved by relocating shared types to the domain layer —
`ReviewResult`→`domain.report`, `AgentConfig`→`domain.agent`, `SkillDefinition`→`domain.skill`,
`SharedCircuitBreaker`→`domain.resilience` — plus converting the `report.finding`↔
`report.formatter` mutual import into a one-way data flow. Domain purity is achieved by
extracting SDK types (`CopilotClient`, `McpServerConfig`) from `ReviewContext` into port
parameters and replacing `@Nullable` with `Optional` on `AgentConfig`. All 69 behavior IDs
from the t3 parity baseline are traced to a specific port, so parity is verifiable per port.

## devops [t7] — 2026-08-05

**Decision**: Adopt a dual-JDK toolchain for the rearchitecture — OpenJDK 27-ea+32 for `pom.xml` (main build, shade JAR, tests, ArchUnit) and Oracle GraalVM 25.0.4 for `pom-native.xml` (native-image). Register both in `~/.m2/toolchains.xml`; select per build via `JAVA_HOME`.

**Rationale**: The two POMs target different Java releases (`java.version=27` vs `release.version=25`) and inherit different micronaut-parent versions (5.1.2 vs 5.0.2). GraalVM 27 EA is not published to SDKMAN, so the main build cannot use a GraalVM JDK; OpenJDK 27-ea+32 satisfies `--release 27 --enable-preview` and compiles clean. The native path stays on GraalVM 25, which ships a working `native-image`. Neither version is changed by this rearchitecture — the initial recon value ("Java 26 EA", read from `.sdkmanrc`/docs) was stale and has been corrected in `project-profile.yaml`.

**Consequence**: Every build-running task must set `JAVA_HOME` explicitly; the default active JDK (GraalVM 25) fails `pom.xml` compilation. Build-config fixes must be applied to both POMs independently — `f63a79c` added the missing `logback.version=1.5.37` BOM override to `pom-native.xml` to restore dependency convergence.
