# RunReviewPort Returns List Not Single Result

Architecture decision: `RunReviewPort.execute()` must return `List<ReviewResult>`, not a single aggregated `ReviewResult`, to preserve per-agent identity for OUT-02/OUT-03 filename routing.

## What Happened

In t9, `RunReviewPort.execute()` was typed as returning a single `ReviewResult`. The orchestrator
then called `aggregateResults()` which joined all per-agent content with `---` dividers into one
blob. This destroyed per-agent identity before the result ever left the application layer.
`GenerateReportUseCase.writePerAgentReports()` received a `List.of(singleBlob)` and could only
write one file — OUT-02 and OUT-03 multi-file output was structurally impossible.

In t9.1, the port was changed to `List<ReviewResult> execute(ReviewRequest)` and
`aggregateResults()` was deleted. The list flows intact to `GenerateReportPort.generate()`.

Source project: anishi1222/multi-agent-code-reviewer / t9.1

## Takeaway

`RunReviewPort` MUST return `List<ReviewResult>`. Any aggregation for display or summary belongs
in the **presentation layer** (CLI/UI), not the application layer. The application layer must
preserve per-agent grain so port adapters can decide how to present results.

Do NOT add back aggregation to `ReviewOrchestrator` — it violates the layering contract.

## Example

```java
// ✅ Correct — application layer port
List<ReviewResult> execute(ReviewRequest request);

// ❌ Wrong — collapses per-agent identity
ReviewResult execute(ReviewRequest request);
```

## History
- 2026-08-05 (anishi1222/t9.1): initial — documented structural defect fix
