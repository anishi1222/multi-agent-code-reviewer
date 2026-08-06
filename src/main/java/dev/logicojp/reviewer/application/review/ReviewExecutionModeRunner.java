package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.outbound.PropagateCorrelationPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.review.ReviewContext;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import dev.logicojp.reviewer.shared.StructuredConcurrencyUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/// Orchestrates parallel execution of review agents using structured concurrency.
///
/// Purified from {@code orchestrator.ReviewExecutionModeRunner}:
/// - Replaced old {@code agent.ReviewContext} → {@code domain.review.ReviewContext}.
/// - Replaced old {@code report.core.ReviewResult} → {@code domain.report.ReviewResult}.
/// - Replaced old {@code config.ExecutionConfig} → config values from {@code OrchestratorConfig}.
/// - Replaced SLF4J with {@code java.util.logging} for this class's own diagnostics.
/// - MDC context propagation is restored through {@link PropagateCorrelationPort}: forked
///   subtasks run on fresh virtual threads whose correlation context starts empty, so the
///   parent context is captured and re-installed inside each fork.
public final class ReviewExecutionModeRunner {

    private record ExecutionParams(int reviewPasses, int agentCount, long timeoutMinutes, long perAgentTimeoutMinutes) {}

    private record SubtaskWithConfig(StructuredTaskScope.Subtask<List<ReviewResult>> subtask, AgentConfig config) {}

    @FunctionalInterface
    public interface AgentPassExecutor {
        List<ReviewResult> execute(AgentConfig config,
                                   ReviewTarget target,
                                   ReviewContext context,
                                   int reviewPasses,
                                   long perAgentTimeoutMinutes);
    }

    private static final Logger logger = Logger.getLogger(ReviewExecutionModeRunner.class.getName());

    private final OrchestratorConfig config;
    private final ReviewResultPipeline reviewResultPipeline;
    private final OrchestratorMetrics metrics;
    private final PropagateCorrelationPort propagateCorrelation;

    public ReviewExecutionModeRunner(OrchestratorConfig config,
                                     ReviewResultPipeline reviewResultPipeline,
                                     OrchestratorMetrics metrics,
                                     PropagateCorrelationPort propagateCorrelation) {
        this.config = config;
        this.reviewResultPipeline = reviewResultPipeline;
        this.metrics = metrics;
        this.propagateCorrelation = propagateCorrelation;
    }

    public List<ReviewResult> executeStructured(Map<String, AgentConfig> agents,
                                                ReviewTarget target,
                                                ReviewContext sharedContext,
                                                AgentPassExecutor agentPassExecutor) {
        return executeStructured(agents, target, sharedContext, config.reviewPasses(),
            config.orchestratorTimeoutMinutes(), agentPassExecutor);
    }

    public List<ReviewResult> executeStructured(Map<String, AgentConfig> agents,
                                                ReviewTarget target,
                                                ReviewContext sharedContext,
                                                int reviewPasses,
                                                AgentPassExecutor agentPassExecutor) {
        return executeStructured(agents, target, sharedContext, reviewPasses,
            config.orchestratorTimeoutMinutes(), agentPassExecutor);
    }

    public List<ReviewResult> executeStructured(Map<String, AgentConfig> agents,
                                                ReviewTarget target,
                                                ReviewContext sharedContext,
                                                int reviewPasses,
                                                long orchestratorTimeoutMinutes,
                                                AgentPassExecutor agentPassExecutor) {
        metrics.markRunStart();
        try {
            return executeStructuredInternal(agents, target, sharedContext,
                reviewPasses, orchestratorTimeoutMinutes, agentPassExecutor);
        } finally {
            metrics.markRunEnd();
            metrics.logSummary();
        }
    }

    private List<ReviewResult> executeStructuredInternal(Map<String, AgentConfig> agents,
                                                         ReviewTarget target,
                                                         ReviewContext sharedContext,
                                                         int reviewPasses,
                                                         long orchestratorTimeoutMinutes,
                                                         AgentPassExecutor agentPassExecutor) {
        long perAgentTimeoutMinutes = config.agentTimeoutMinutes() * (config.maxRetries() + 1L);
        List<SubtaskWithConfig> tasks = new ArrayList<>();
        // Captured on the orchestrator thread: each fork below runs on a fresh virtual
        // thread that would otherwise start with an empty correlation context.
        Map<String, String> parentContext = propagateCorrelation.captureContext();

        try (var scope = StructuredConcurrencyUtils.<List<ReviewResult>>openAwaitAllScope()) {
            for (Map.Entry<String, AgentConfig> entry : agents.entrySet()) {
                AgentConfig agentConfig = entry.getValue();
                var subtask = scope.fork(() -> propagateCorrelation.callWithContext(parentContext, () -> {
                    logAgentStart(agentConfig, reviewPasses);
                    return agentPassExecutor.execute(
                        agentConfig, target, sharedContext, reviewPasses, perAgentTimeoutMinutes);
                }));
                tasks.add(new SubtaskWithConfig(subtask, agentConfig));
            }
            joinStructuredWithTimeout(scope, tasks, orchestratorTimeoutMinutes);
        }

        List<ReviewResult> allResults = collectStructuredResults(
            tasks, target, perAgentTimeoutMinutes, reviewPasses);
        return reviewResultPipeline.finalizeResults(allResults, reviewPasses);
    }

    private List<ReviewResult> collectStructuredResults(List<SubtaskWithConfig> tasks,
                                                        ReviewTarget target,
                                                        long perAgentTimeoutMinutes,
                                                        int reviewPasses) {
        var results = new ArrayList<ReviewResult>();
        for (var task : tasks) {
            results.addAll(summarizeTaskResult(task, target, perAgentTimeoutMinutes, reviewPasses));
        }
        return results;
    }

    private List<ReviewResult> summarizeTaskResult(SubtaskWithConfig taskWithConfig,
                                                    ReviewTarget target,
                                                    long perAgentTimeoutMinutes,
                                                    int reviewPasses) {
        var subtask = taskWithConfig.subtask();
        var state = subtask.state();
        if (state == StructuredTaskScope.Subtask.State.SUCCESS) {
            return subtask.get();
        }
        if (state == StructuredTaskScope.Subtask.State.FAILED) {
            Throwable cause = subtask.exception();
            return ReviewResult.failedResults(taskWithConfig.config(), target.displayName(), reviewPasses,
                "Review failed: " + (cause != null ? cause.getMessage() : "unknown"));
        }
        return ReviewResult.failedResults(taskWithConfig.config(), target.displayName(), reviewPasses,
            "Review cancelled after " + perAgentTimeoutMinutes + " minutes");
    }

    @SuppressWarnings("rawtypes")
    private void joinStructuredWithTimeout(StructuredTaskScope scope,
                                           List<SubtaskWithConfig> tasks,
                                           long timeoutMinutes) {
        try {
            StructuredConcurrencyUtils.joinWithTimeout(scope, timeoutMinutes, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.severe("Structured concurrency interrupted");
        } catch (TimeoutException e) {
            int unfinished = (int) tasks.stream()
                .map(SubtaskWithConfig::subtask)
                .filter(s -> s.state() == StructuredTaskScope.Subtask.State.UNAVAILABLE)
                .count();
            logger.severe("Structured concurrency timed out after " + timeoutMinutes
                + " minutes; cancelling " + unfinished + " unfinished task(s)");
            scope.close();
        }
    }

    private void logAgentStart(AgentConfig agentConfig, int reviewPasses) {
        if (reviewPasses > 1) {
            logger.info("Agent '" + agentConfig.name() + "': starting " + reviewPasses + " passes (structured)");
        }
    }
}
