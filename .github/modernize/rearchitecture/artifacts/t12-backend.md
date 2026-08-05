# t12 — Phase 5: Presentation Layer + ArchUnit Boundary Tests

## Summary

Completed T011 (presentation layer migration) and T012 (ArchUnit boundary tests).
All 913 tests pass. Build: `BUILD SUCCESS`.

## T011 — Presentation Layer (~28 files)

### New files created

| Package | Files |
|---------|-------|
| `presentation/` | `CliCommand`, `CliOutput`, `CliParsing`, `CliUsage`, `ExitCodes`, `ReviewOptions` (+ `ModelOptions`, `Builder`), `ReviewAgentSelection`, `ReviewTargetSelection`, `CliValidationException`, `ReviewOutputFormatter`, `ReviewPreparationService`, `ReviewRunRequestFactory`, `ReviewExecutionCoordinator`, `ReviewRunExecutor`, `ReviewModelConfigResolver`, `ReviewAgentConfigResolver`, `ReviewTargetResolver` |
| `presentation/command/` | `ReviewCommand`, `DoctorCommand`, `ListAgentsCommand`, `SkillCommand` |
| `presentation/parser/` | `ReviewOptionsParser`, `DoctorParser`, `ListAgentsParser`, `SkillParser` |
| `presentation/formatter/` | `SkillOutputFormatter` |
| `infrastructure/copilot/` | `ApplicationPortFactory` (@Factory for 7 port beans) |

### ApplicationPortFactory — port bindings

| Port interface | Implementation |
|----------------|---------------|
| `LoadAgentPort` | `LoadAgentUseCase` |
| `RunDiagnosticsPort` | `RunDiagnosticsUseCase` |
| `WriteReportPort` | `ReportFileWriter` |
| `GenerateAiSummaryPort` | `AiSummaryClient` |
| `RunCopilotSessionPort` | `ReviewSessionExecutor` |
| `ExecuteSkillPort` | `SkillExecutor` |
| `GenerateReportPort` | `GenerateReportUseCase` |

### `ReviewApp` wiring

`ReviewApp` updated to import `presentation.CliCommand` and `presentation.CliOutput` (replacing old `cli.*`).

## T012 — ArchUnit Boundary Tests

File: `src/test/java/dev/logicojp/reviewer/architecture/LayerDependencyRulesTest.java`

| Rule | Description |
|------|-------------|
| Rule 1 | `domain.*` must not import Micronaut, Jakarta, Copilot SDK, SLF4J, SnakeYAML |
| Rule 2 | `shared.*` must only use `java.*` |
| Rule 3 | No package outside `presentation.*` may import `presentation.*` (synthetic `$` classes excluded) |
| Rule 4 | `infrastructure.*` must not import `application.review.*` use-case classes |
| Rule 5 | `application.*` (excluding `application.port.*`) must not import `infrastructure.*` or `presentation.*` |
| Rule 6 | No cyclic dependencies between top-level packages |

### Configuration

- `pom.xml`: added `com.tngtech.archunit:archunit-junit5:1.3.0` test dependency
- `src/test/resources/archunit.properties`: `archRule.failOnEmptyShould=false`

## Bug Fixes Discovered During Integration

1. **`CopilotClientStarter`** — missing `@Singleton` (never registered as bean before; presentation layer wiring exposed it)
2. **`LocalFileProvider`** — missing `@Singleton` (same root cause)
3. **`ReviewSessionConfigFactory`** — missing `@Singleton`
4. **`SummaryGenerator` template key constants** — used slash-based keys (`"summary/user-prompt"`) without `.md` extension, not matching actual files (`"summary-prompt.md"`). Fixed all 9 constants.
5. **`ReviewOptions`** — added `reasoningEffort` to `ModelOptions` (required by `ReviewModelConfigResolver`)
6. **ArchUnit Rule 3** — `$ReviewApp$Definition` (Micronaut-generated synthetic) violated rule; fixed with `haveNameNotMatching(".*\\$.*")` to exclude all synthetic classes

## Test Results

- Command: `JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open ./mvnw -B clean verify`
- Passed: **913**
- Failed: **0**
- Skipped: 0
- Build: **SUCCESS**

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t1-teamlead.md` — hexagonal architecture constitution, package naming rules
- `.github/modernize/rearchitecture/artifacts/t4-architect-packages.md` — package design and layer boundaries
- `.github/modernize/rearchitecture/artifacts/t4-architect-ports.md` — port catalog (inbound/outbound)
- `.github/modernize/rearchitecture/artifacts/t4-architect-classmap.md` — class migration map
- `.github/modernize/rearchitecture/artifacts/t5-teamlead-tasks.md` — T011/T012 task definitions
- `.github/modernize/rearchitecture/artifacts/t11-backend.md` — Phase 4 infrastructure baseline (907 tests)

## Evidence Mapping

- `t4-architect-ports.md#inbound-ports` → `ApplicationPortFactory` provides all 7 inbound port beans
- `t4-architect-packages.md#package-structure` → `presentation.*` sub-packages match spec
- `t5-teamlead-tasks.md#T011` → 28 presentation files created with correct imports
- `t5-teamlead-tasks.md#T012` → `LayerDependencyRulesTest` with 6 rules, all passing
- `t11-backend.md#test-results` (907 tests) → 913 tests after T012 additions (6 new ArchUnit tests)
