# t2 — Dependency Cycle Inventory

Complete class-level evidence for all 10 inter-package dependency cycles.

## Cycle 1: agent ⇄ report.core

**Direction agent→report.core (5 classes):**
- `ReviewAgent.java` → `ReviewResult`
- `ReviewPassRunner.java` → `ReviewResult`
- `ReviewResultFactory.java` → `ReviewResult`
- `ReviewRetryExecutor.java` → `ReviewResult`
- `ReviewSessionExecutor.java` → `ReviewResult`
- `RubberDuckDialogueExecutor.java` → `ReviewResult`

**Direction report.core→agent (2 classes):**
- `ReportGenerator.java` → `AgentConfig`
- `ReviewResult.java` → `AgentConfig`

**Root cause**: `ReviewResult` is a domain model that belongs in neither package. `AgentConfig` is similarly cross-cutting.

## Cycle 2: agent ⇄ service

**Direction agent→service (2 classes):**
- `RubberDuckDialogueExecutor.java` → `TemplateService`
- `RubberDuckPromptBuilder.java` → `TemplateService`

**Direction service→agent (3 classes):**
- `AgentService.java` → `AgentConfig`, `AgentConfigLoader`
- `ReviewService.java` → `AgentConfig`
- `SkillService.java` → `AgentConfig`, `CircuitBreakerFactory`, `SharedCircuitBreaker`

**Root cause**: `TemplateService` is used directly instead of via a port. `AgentConfig` leaks upward.

## Cycle 3: agent ⇄ skill

**Direction agent→skill (2 classes):**
- `AgentConfig.java` → `SkillDefinition`
- `AgentConfigLoader.java` → `SkillDefinition`, `SkillMarkdownParser`

**Direction skill→agent (1 class):**
- `SkillExecutor.java` → `SharedCircuitBreaker`

**Root cause**: `SkillDefinition` and `SharedCircuitBreaker` are shared domain concepts placed in wrong packages.

## Cycle 4: agent ⇄ util

**Direction agent→util (7 classes):**
- `AgentFrontmatterMapper.java` → `FrontmatterParser`
- `AgentMarkdownParser.java` → `FrontmatterParser`
- `AgentPromptBuilder.java` → `PlaceholderUtils`
- `ReviewPassRunner.java` → `StructuredConcurrencyUtils`
- `ReviewRetryExecutor.java` → `RetryExecutor`, `RetryPolicyUtils`
- `ReviewSessionConfigFactory.java` → `CopilotPermissionHandlers`
- `RubberDuckPromptBuilder.java` → `PlaceholderUtils`

**Direction util→agent (1 class):**
- `RetryExecutor.java` → `SharedCircuitBreaker`

**Root cause**: `SharedCircuitBreaker` is in `agent` but is a shared utility concept.

## Cycle 5: service ⇄ orchestrator

**Direction orchestrator→service (5 classes):**
- `AgentReviewExecutor.java` → `TemplateService`
- `AgentReviewer.java` → `TemplateService`
- `OrchestratorConfig.java` → `TemplateService`
- `ReviewOrchestrator.java` → `TemplateService`
- `ReviewOrchestratorFactory.java` → `CopilotService`, `TemplateService`

**Direction service→orchestrator (1 class):**
- `ReviewService.java` → `ReviewOrchestrator`, `ReviewOrchestratorFactory`

**Root cause**: `ReviewService` is both the entry facade and the orchestrator consumer — conflated responsibilities.

## Cycle 6: report.core ⇄ report.formatter

**Direction report.core→report.formatter (1 class):**
- `ReportGenerator.java` → `ReportContentFormatter`

**Direction report.formatter→report.core (2 classes):**
- `ReportContentFormatter.java` → `ReviewResult`
- `SummaryFinalReportFormatter.java` → `ReviewResult`

**Root cause**: `ReviewResult` is a domain type trapped in `report.core`.

## Cycle 7: report.core ⇄ service

**Direction report.core→service (1 class):**
- `ReportGenerator.java` → `TemplateService`

**Direction service→report.core (2 classes):**
- `ReportService.java` → `ReportGenerator`, `ReviewResult`
- `ReviewService.java` → `ReviewResult`

**Root cause**: `TemplateService` is accessed directly instead of via port.

## Cycle 8: report.factory ⇄ service

**Direction report.factory→service (1 class):**
- `ReportGeneratorFactory.java` → `TemplateService`

**Direction service→report.factory (1 class):**
- `ReportService.java` → `ReportGeneratorFactory`

**Root cause**: Same `TemplateService` direct access pattern.

## Cycle 9: report.finding ⇄ report.formatter

**Direction report.finding→report.formatter (1 class):**
- `FindingsExtractor.java` → `FindingsSummaryFormatter`

**Direction report.formatter→report.finding (3 classes):**
- `FindingsSummaryFormatter.java` → `FindingsExtractor`
- `ReviewMergedContentFormatter.java` → `AggregatedFinding`
- `SummaryFinalReportFormatter.java` → `FindingsExtractor`

**Root cause**: Extraction and formatting are interleaved — need clear data flow direction.

## Cycle 10: report.summary ⇄ service

**Direction report.summary→service (4 classes):**
- `AiSummaryClient.java` → `CopilotCliException`, `TemplateService`
- `FallbackSummaryBuilder.java` → `TemplateService`
- `SummaryGenerator.java` → `TemplateService`
- `SummaryPromptBuilder.java` → `TemplateService`

**Direction service→report.summary (1 class):**
- `ReportService.java` → `SummaryGenerator`

**Root cause**: `TemplateService` dependency again — hub of 5 cycles.

---

## Cycle Hub Analysis

**`TemplateService`** is the single biggest cycle hub, creating cycles 2, 5, 7, 8, 10 (5 of 10 cycles). Defining a `LoadTemplatePort` will break all 5 simultaneously.

**`AgentConfig` / `ReviewResult`** shared domain types create cycles 1, 2, 3, 6, 7. Moving them to `domain` breaks all 5.

**`SharedCircuitBreaker`** creates cycles 3, 4. Moving to `shared` or `domain` breaks both.
