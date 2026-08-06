# t2 — Current Architecture Analysis: Dependency Cycles and Framework Leakage

## Summary

Analysis of 120 Java files across 15 packages under `dev.logicojp.reviewer`. Identified **10 dependency cycles** (4 more than recon's 6), **SDK leakage in 20 files**, **framework leakage (Micronaut/Jakarta) in 56 files**, and **SLF4J in 50 files**. Every package except `report.formatter`, `report.sanitize`, and `report.util` has at least one external framework dependency. The `agent` package is the worst offender: 30 files mixing domain logic, SDK I/O, and cross-cutting concerns.

## Deliverables

- [t2-architect-cycles.md](./t2-architect-cycles.md) — Complete dependency cycle inventory with class-level evidence
- [t2-architect-leakage.md](./t2-architect-leakage.md) — Framework/SDK leakage per file, migration risk per package
- [t2-architect-class-map.md](./t2-architect-class-map.md) — Full class inventory with responsibility, I/O, and purity classification

## Upstream Artifacts Consumed

- `clarification.md` — target architecture (Ports & Adapters), scope, domain purity rules
- `artifacts/project-profile.yaml` — current structure, known cycles, SDK leakage counts
- `artifacts/t1-teamlead.md` — constitution: layer model §1, dependency direction §2, domain purity §3, port convention §4

## Evidence Mapping

- `project-profile.yaml#structure.notes` (6 cycles) → `t2-architect-cycles.md` (verified 6, found 4 additional = 10 total)
- `project-profile.yaml#structure.notes` (SDK 53 locations) → `t2-architect-leakage.md` (verified: 20 files with Copilot SDK imports)
- `t1-teamlead.md#§2` (dependency direction) → cycle analysis validates every violation against inward-only rule
- `t1-teamlead.md#§3` (domain purity) → leakage analysis identifies every file that would violate domain purity if placed in domain layer
- `t1-teamlead.md#§7.6` (SDK isolation) → 20 files with SDK imports cataloged for infrastructure confinement
- `t1-teamlead.md#§7.7` (framework isolation) → 56 files with Micronaut/Jakarta imports cataloged

## Key Findings

### 1. Dependency Cycles (10 total — 4 more than recon)

| # | Cycle | Root cause |
|---|---|---|
| 1 | agent ⇄ report.core | `ReviewResult` lives in report.core but is constructed in agent |
| 2 | agent ⇄ service | `TemplateService` used by agent; `AgentService` depends on agent types |
| 3 | agent ⇄ skill | `AgentConfig` holds `SkillDefinition`; `SkillExecutor` uses `SharedCircuitBreaker` |
| 4 | agent ⇄ util | `RetryExecutor` imports `SharedCircuitBreaker` from agent |
| 5 | service ⇄ orchestrator | `ReviewService` creates orchestrators; orchestrators use services |
| 6 | report.core ⇄ report.formatter | `ReportGenerator` ↔ `ReportContentFormatter` via `ReviewResult` |
| 7 | report.core ⇄ service | `ReportGenerator` uses `TemplateService`; `ReportService` uses report types |
| 8 | report.factory ⇄ service | `ReportGeneratorFactory` uses `TemplateService`; `ReportService` uses factory |
| 9 | report.finding ⇄ report.formatter | `FindingsExtractor` ↔ `FindingsSummaryFormatter` mutual import |
| 10 | report.summary ⇄ service | `AiSummaryClient` uses `TemplateService`; `ReportService` uses `SummaryGenerator` |

**Cycle resolution strategy for t4**: Move shared domain types (`ReviewResult`, `AgentConfig`, `SkillDefinition`, `SharedCircuitBreaker`) into `domain` layer. Inject service dependencies via outbound ports. Break TemplateService dependency by defining a `LoadTemplatePort`.

### 2. Framework Leakage Summary

| Framework | Files affected | Constitution target layer |
|---|---|---|
| Copilot SDK (`com.github.copilot.*`) | 20 | infrastructure only (§7.6) |
| Micronaut (`io.micronaut.*`) | 24 | infrastructure + presentation only (§7.7) |
| Jakarta (`jakarta.*`) | 32 | infrastructure + presentation only (§7.7) |
| SLF4J (`org.slf4j.*`) | 50 | infrastructure + presentation only (§3) |
| SnakeYAML (`org.yaml.snakeyaml.*`) | 1 | infrastructure only |

### 3. Packages with Mixed Concerns (architectural smells)

| Package | Smell | Detail |
|---|---|---|
| `agent` (30 files) | God-package | Mixes domain models, SDK I/O adapters, prompt builders, session executors, circuit breakers |
| `cli` (28 files) | God-package | Mixes argument parsing, command dispatch, use-case coordination, output formatting |
| `service` (13 files) | Facade overload | SDK client management + template loading + report orchestration + review orchestration |
| `util` (14+) | Kitchen-sink | Concurrency, text parsing, token handling, process execution, audit logging |
| `report.*` (22 files) | Sub-package cycles | 4 internal cycles among core/formatter/finding/summary |

### 4. Migration Risk Assessment

| Risk | Severity | Mitigation |
|---|---|---|
| `ReviewResult` is imported by 6+ packages — moving it will touch many files | HIGH | Move to `domain` first as a foundational step; update all imports in one pass |
| `TemplateService` is imported by 8+ classes across 4 packages — cycle hub | HIGH | Define `LoadTemplatePort` in application.port; inject in use cases |
| `AgentConfig` and `SharedCircuitBreaker` are shared across agent/skill/service | HIGH | Move pure domain parts to `domain`; keep DI-wired factory in infrastructure |
| SLF4J in 50 files — domain §3 forbids it | MEDIUM | Domain classes must use `java.util.logging` or return results; SLF4J stays in infra/presentation |
| `config` package has Copilot SDK type in `GithubMcpConfig` | MEDIUM | Move to `infrastructure.config` |
| `util/RetryExecutor` → `agent/SharedCircuitBreaker` cycle | LOW | Move `SharedCircuitBreaker` to `domain` or `shared` (it's pure logic) |
