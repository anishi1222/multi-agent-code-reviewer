# orchestrator-per-invocation-resources

## Context
Phase 2 (t9) — `ReviewOrchestrator` in `application.review`.

## Pattern: Per-Invocation Executor Resources

In a pure application layer (no DI, no `@Singleton`), the orchestrator creates
`ExecutorResources` (containing `ExecutorService` + `Semaphore`) per call to
`execute(ReviewRequest)` and shuts them down in a `finally` block.

```java
private ExecutorResources buildExecutorResources(int parallelism) {
    var executor = Executors.newFixedThreadPool(parallelism,
        r -> Thread.ofPlatform().name("review-agent-", 0).unstarted(r));
    var semaphore = new Semaphore(parallelism);
    return new ExecutorResources(executor, semaphore);
}
```

Then in `executeStandardReviews`/`executeRubberDuckReviews`:
```java
try {
    return modeRunner.executeStructured(...);
} finally {
    resources.shutdownGracefully();
}
```

**Why**: Avoids `AutoCloseable` and DI lifecycle management. Each review invocation
is self-contained. The old code used `try (var orchestrator = factory.create(...))`.
The new code internalizes this into each `execute()` call.

## Pattern: RunReviewPort Returns Single Aggregated ReviewResult

The port contract `ReviewResult execute(ReviewRequest)` returns ONE result, not
`List<ReviewResult>`. Aggregate multiple agent results by:
- Joining content with `### Review by: <agentName>` headers and `---` dividers
- `success = results.stream().anyMatch(ReviewResult::success)`
- `errorMessage` = semicolon-joined error messages from failed agents

## Pattern: MCP Servers Default to Empty List

Application layer passes `List.of()` for `mcpServers` to port calls.
The infrastructure implementation of `RunCopilotSessionPort` and
`RunRubberDuckSessionPort` is responsible for augmenting with actual MCP specs
based on agent config flags (e.g., `AgentConfig.mcpEnabled()`).
This keeps infrastructure concerns out of the application layer.
