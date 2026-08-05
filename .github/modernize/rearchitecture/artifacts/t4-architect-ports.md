# t4 — Port Catalog

## §1 Overview

12 port interfaces in `dev.logicojp.reviewer.application.port`:
- **5 inbound** (in `port.inbound`) — use-case entry points; implemented by `application`; called by `presentation`
- **7 outbound** (in `port.outbound`) — infrastructure contracts; implemented by `infrastructure`; called by `application`

Naming convention: `<Verb><Noun>Port` per constitution §4.

---

## §2 Inbound Ports

Inbound ports define what the application CAN DO. Presentation calls them; application implements them.

### 2.1 `RunReviewPort`

**Package**: `application.port.inbound`
**Implementer**: `application.review.ReviewOrchestrator`
**Callers**: `presentation.command.ReviewCommand` via coordinators

```java
public interface RunReviewPort {
    /** Execute a full review with the given request. */
    ReviewResult execute(ReviewRequest request);
}
```

**Key types**: `ReviewRequest` (domain DTO containing agents, target, options, parallelism)
**Behaviors covered**: ORC-01–ORC-10, RTY-01–RTY-04

### 2.2 `LoadAgentPort`

**Package**: `application.port.inbound`
**Implementer**: `application.agent.LoadAgentUseCase`
**Callers**: `presentation.command.ReviewCommand`, `presentation.command.ListAgentsCommand`

```java
public interface LoadAgentPort {
    /** Load and validate agents from configured directories. */
    List<AgentConfig> loadAll(List<Path> directories);

    /** Load a single agent by name. */
    Optional<AgentConfig> loadByName(String name, List<Path> directories);
}
```

**Behaviors covered**: AGT-01–AGT-13

### 2.3 `ExecuteSkillPort`

**Package**: `application.port.inbound`
**Implementer**: `application.skill.ExecuteSkillUseCase`
**Callers**: `presentation.command.SkillCommand`

```java
public interface ExecuteSkillPort {
    /** Execute a skill with the given parameters. */
    SkillResult execute(String skillId, Map<String, String> parameters);

    /** List available skills. */
    List<SkillDefinition> listSkills();
}
```

**Behaviors covered**: SKL-01–SKL-08

### 2.4 `GenerateReportPort`

**Package**: `application.port.inbound`
**Implementer**: `application.report.GenerateReportUseCase`
**Callers**: `presentation.command.ReviewCommand` (post-review)

```java
public interface GenerateReportPort {
    /** Generate reports for review results. */
    ReportOutput generate(List<ReviewResult> results, ReportOptions options);

    /** Generate executive summary. */
    Optional<String> generateSummary(List<ReviewResult> results, ReportOptions options);
}
```

**Behaviors covered**: OUT-01–OUT-09

### 2.5 `RunDiagnosticsPort`

**Package**: `application.port.inbound`
**Implementer**: `application.review.RunDiagnosticsUseCase` (new, thin)
**Callers**: `presentation.command.DoctorCommand`

```java
public interface RunDiagnosticsPort {
    /** Run all diagnostic checks. */
    List<DiagnosticResult> runAll();
}
```

**Behaviors covered**: AUTH-09

---

## §3 Outbound Ports

Outbound ports define what the application NEEDS FROM the outside world. Infrastructure implements them.

### 3.1 `LoadTemplatePort`

**Package**: `application.port.outbound`
**Implementer**: `infrastructure.template.TemplateRepository`
**Callers**: `application.review.*`, `application.report.*`

```java
public interface LoadTemplatePort {
    /** Load a template by key and render with placeholders. */
    String render(String templateKey, Map<String, String> placeholders);

    /** Load raw template content. */
    String loadRaw(String templateKey);
}
```

**Cycle resolution**: Breaks cycles 2, 5, 7, 8, 10 — all caused by direct `TemplateService` imports.
Previously `TemplateService` was imported by 8+ classes across 4 packages. Now all go through this port.

### 3.2 `RunCopilotSessionPort`

**Package**: `application.port.outbound`
**Implementer**: `infrastructure.copilot.ReviewSessionExecutor`
**Callers**: `application.review.ReviewPassRunner`

```java
public interface RunCopilotSessionPort {
    /** Create and run a Copilot review session, returning the response. */
    String runSession(SessionRequest request);
}
```

**Key types**: `SessionRequest` (domain DTO — no SDK types: agent config, prompt, model, MCP config as domain values)

### 3.3 `RunRubberDuckSessionPort`

**Package**: `application.port.outbound`
**Implementer**: `infrastructure.copilot.RubberDuckDialogueExecutor`
**Callers**: `application.review.RubberDuckDialogueRunner`

```java
public interface RunRubberDuckSessionPort {
    /** Execute a multi-turn rubber-duck dialogue. */
    List<DialogueRound> executeDialogue(RubberDuckRequest request);
}
```

### 3.4 `ManageCopilotClientPort`

**Package**: `application.port.outbound`
**Implementer**: `infrastructure.copilot.CopilotService`
**Callers**: `application.review.ReviewOrchestrator` (to start/stop client)

```java
public interface ManageCopilotClientPort {
    /** Initialize and start the Copilot client. */
    void start(String token);

    /** Stop the client. */
    void stop();

    /** Check health. */
    boolean isHealthy();
}
```

**Behaviors covered**: AUTH-01–AUTH-08, AUTH-10–AUTH-11

### 3.5 `CollectLocalSourcePort`

**Package**: `application.port.outbound`
**Implementer**: `infrastructure.file.LocalFileProvider`
**Callers**: `application.review.LocalSourcePrecomputer`

```java
public interface CollectLocalSourcePort {
    /** Collect source files from a local directory. */
    List<LocalFileCandidate> collect(Path directory, LocalFileSelectionConfig config);

    /** Format collected files into review content. */
    String formatContent(List<LocalFileCandidate> candidates);
}
```

**Behaviors covered**: TGT-01–TGT-09

### 3.6 `WriteReportPort`

**Package**: `application.port.outbound`
**Implementer**: `infrastructure.file.ReportFileWriter` (renamed from `ReportGenerator`)
**Callers**: `application.report.GenerateReportUseCase`

```java
public interface WriteReportPort {
    /** Write a report to the output directory. */
    Path write(String content, String filename, Path outputDir);

    /** Create timestamped output directory. */
    Path createOutputDirectory(Path baseDir);
}
```

**Behaviors covered**: OUT-02, OUT-03, OUT-06

### 3.7 `GenerateAiSummaryPort`

**Package**: `application.port.outbound`
**Implementer**: `infrastructure.copilot.AiSummaryClient`
**Callers**: `application.report.SummaryGenerator`

```java
public interface GenerateAiSummaryPort {
    /** Generate AI-powered executive summary from review results. */
    Optional<String> generate(String prompt);
}
```

**Cycle resolution**: Breaks cycle 10 (report.summary ⇄ service) by removing direct CopilotClient usage from summary generation.
**Behaviors covered**: OUT-04, OUT-05

---

## §4 Cycle Resolution Evidence

All 10 cycles from t2 are resolved by this port catalog + domain type moves:

| Cycle | Resolution |
|---|---|
| 1: agent ⇄ report.core | `ReviewResult` → `domain.report`, `AgentConfig` → `domain.agent`. No cross-layer reference needed. |
| 2: agent ⇄ service | `TemplateService` → `LoadTemplatePort`. `AgentConfig` → `domain.agent`. |
| 3: agent ⇄ skill | `SkillDefinition` → `domain.skill`. `SharedCircuitBreaker` → `domain.resilience`. |
| 4: agent ⇄ util | `SharedCircuitBreaker` → `domain.resilience`. `RetryExecutor` → `shared` (no circuit breaker import). |
| 5: service ⇄ orchestrator | `TemplateService` → `LoadTemplatePort`. Both now in `application` with clean dep direction. |
| 6: report.core ⇄ report.formatter | `ReviewResult` → `domain.report`. Both formatter and generator reference domain, no mutual dep. |
| 7: report.core ⇄ service | `TemplateService` → `LoadTemplatePort`. `ReviewResult` → `domain.report`. |
| 8: report.factory ⇄ service | `TemplateService` → `LoadTemplatePort`. Factory → `infrastructure.copilot`. |
| 9: report.finding ⇄ report.formatter | Both in `domain.report` — reorganize as data flow: Extractor → data → Formatter (no mutual import). `FindingsExtractor` produces `AggregatedFinding`; `FindingsSummaryFormatter` consumes it. Remove direct class reference from Extractor→Formatter. |
| 10: report.summary ⇄ service | `TemplateService` → `LoadTemplatePort`. `AiSummaryClient` → `infrastructure.copilot` behind `GenerateAiSummaryPort`. |

## §5 Port-to-Behavior Traceability

| Port | PM Behavior IDs |
|---|---|
| RunReviewPort | ORC-01–10, RTY-01–04 |
| LoadAgentPort | AGT-01–13 |
| ExecuteSkillPort | SKL-01–08 |
| GenerateReportPort | OUT-01–09 |
| RunDiagnosticsPort | AUTH-09 |
| LoadTemplatePort | (internal — enables template usage across all behaviors) |
| RunCopilotSessionPort | ORC-02, ORC-03, ORC-06 |
| RunRubberDuckSessionPort | ORC-08 |
| ManageCopilotClientPort | AUTH-01–08, AUTH-10–11 |
| CollectLocalSourcePort | TGT-01–09 |
| WriteReportPort | OUT-02, OUT-03, OUT-06 |
| GenerateAiSummaryPort | OUT-04, OUT-05 |

## §6 Domain Purity Verification

After this design, `domain` layer contains:
- **Imports**: Only `java.*`, `java.util.*`, types from `domain.*`, types from `shared.*`
- **No**: `io.micronaut.*`, `jakarta.*`, `com.github.copilot.*`, `org.slf4j.*`, `org.yaml.snakeyaml.*`
- **No DI annotations**: No `@Inject`, `@Singleton`, `@Named`
- **No I/O**: File, network, process operations delegated via outbound ports
- **Logging**: `java.util.logging` if needed, or return results (prefer the latter)

Classes requiring purification when moved to domain:
- `AgentConfig` — remove `@Nullable` (→ `Optional`), remove any Micronaut import
- `ReviewContext` — extract SDK types (`CopilotClient`, `McpServerConfig`) to port parameters
- `ReviewResult` — remove `@Nullable` (→ `Optional`)
- `ReviewTargetInstructionResolver` — remove `@Nullable`
- `SharedCircuitBreaker` — already pure (no framework deps)
- `FallbackSummaryBuilder` — remove SLF4J (use result returns)
- All `report.formatter.*` — remove SLF4J
