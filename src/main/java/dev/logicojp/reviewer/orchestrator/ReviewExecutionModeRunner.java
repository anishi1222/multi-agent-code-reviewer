package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.ExecutionCorrelation;
import dev.logicojp.reviewer.util.StructuredConcurrencyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class ReviewExecutionModeRunner {

    private record ExecutionParams(int reviewPasses, int agentCount, long timeoutMinutes, long perAgentTimeoutMinutes) {
    }

    private record SubtaskWithConfig(StructuredTaskScope.Subtask<List<ReviewResult>> subtask, AgentConfig config) {
    }

    @FunctionalInterface
    interface AgentPassExecutor {
        List<ReviewResult> execute(AgentConfig config,
                                   ReviewTarget target,
                                   ReviewContext context,
                                   int reviewPasses,
                                   long perAgentTimeoutMinutes);
    }

    private static final Logger logger = LoggerFactory.getLogger(ReviewExecutionModeRunner.class);

    private final ExecutionConfig executionConfig;
    private final ReviewResultPipeline reviewResultPipeline;
    private final OrchestratorMetrics metrics;

    ReviewExecutionModeRunner(ExecutionConfig executionConfig,
                              ReviewResultPipeline reviewResultPipeline,
                              OrchestratorMetrics metrics) {
        this.executionConfig = executionConfig;
        this.reviewResultPipeline = reviewResultPipeline;
        this.metrics = metrics;
    }

    List<ReviewResult> executeStructured(Map<String, AgentConfig> agents,
                                         ReviewTarget target,
                                         ReviewContext sharedContext,
                                         AgentPassExecutor agentPassExecutor) {
        return executeStructured(
            agents,
            target,
            sharedContext,
            executionConfig.reviewPasses(),
            executionConfig.orchestratorTimeoutMinutes(),
            agentPassExecutor
        );
    }

    List<ReviewResult> executeStructured(Map<String, AgentConfig> agents,
                                         ReviewTarget target,
                                         ReviewContext sharedContext,
                                         int reviewPasses,
                                         AgentPassExecutor agentPassExecutor) {
        return executeStructured(
            agents,
            target,
            sharedContext,
            reviewPasses,
            executionConfig.orchestratorTimeoutMinutes(),
            agentPassExecutor
        );
    }

    List<ReviewResult> executeStructured(Map<String, AgentConfig> agents,
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
        ExecutionParams params = executionParams(agents.size(), reviewPasses, orchestratorTimeoutMinutes);
        List<SubtaskWithConfig> tasks = new ArrayList<>(params.agentCount());
        var parentMdcContext = ExecutionCorrelation.captureMdcContext();
        try (var scope = StructuredConcurrencyUtils.<List<ReviewResult>>openAwaitAllScope()) {
            for (var config : agents.values()) {
                tasks.add(new SubtaskWithConfig(scope.fork(() -> executeAgentPasses(
                    config,
                    target,
                    sharedContext,
                    params.reviewPasses(),
                    params.perAgentTimeoutMinutes(),
                    agentPassExecutor,
                    parentMdcContext
                )), config));
            }

            joinStructuredWithTimeout(scope, tasks, params.timeoutMinutes());

            return finalizeResults(
                params.reviewPasses(),
                collectStructuredResults(tasks, target, params.perAgentTimeoutMinutes(), params.reviewPasses())
            );
        }
    }

    private ExecutionParams executionParams(int agentCount,
                                            int reviewPasses,
                                            long orchestratorTimeoutMinutes) {
        int normalizedReviewPasses = Math.max(1, reviewPasses);
        long normalizedTimeoutMinutes = Math.max(1L, orchestratorTimeoutMinutes);
        return new ExecutionParams(
            normalizedReviewPasses,
            agentCount,
            normalizedTimeoutMinutes,
            perAgentTimeoutMinutes()
        );
    }

    private long perAgentTimeoutMinutes() {
        return executionConfig.agentTimeoutMinutes() * (executionConfig.maxRetries() + 1L);
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

    private List<ReviewResult> collectStructuredResults(
            List<SubtaskWithConfig> tasks,
            ReviewTarget target,
            long perAgentTimeoutMinutes,
            int reviewPasses) {
        List<ReviewResult> results = new ArrayList<>(tasks.size() * reviewPasses);
        for (var task : tasks) {
            results.addAll(summarizeTaskResult(task, target, perAgentTimeoutMinutes, reviewPasses));
        }
        return results;
    }

    private List<ReviewResult> finalizeResults(int reviewPasses, List<ReviewResult> results) {
        return reviewResultPipeline.finalizeResults(results, reviewPasses);
    }

    private void joinStructuredWithTimeout(StructuredTaskScope<List<ReviewResult>, ?, RuntimeException> scope,
                                           List<SubtaskWithConfig> tasks,
                                           long timeoutMinutes) {
        try {
            StructuredConcurrencyUtils.joinWithTimeout(scope, timeoutMinutes, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Structured concurrency interrupted", e);
        } catch (TimeoutException e) {
            int unfinishedTaskCount = (int) tasks.stream()
                .map(SubtaskWithConfig::subtask)
                .filter(subtask -> subtask.state() == StructuredTaskScope.Subtask.State.UNAVAILABLE)
                .count();
            logger.error(
                "Structured concurrency timed out after {} minutes; cancelling {} unfinished task(s)",
                timeoutMinutes,
                unfinishedTaskCount,
                e
            );
            scope.close();
        }
    }

    private List<ReviewResult> executeAgentPasses(AgentConfig config,
                                                   ReviewTarget target,
                                                   ReviewContext sharedContext,
                                                   int reviewPasses,
                                                   long perAgentTimeoutMinutes,
                                                   AgentPassExecutor agentPassExecutor,
                                                   Map<String, String> parentMdcContext) {
        try {
            return ExecutionCorrelation.callWithMdcContext(parentMdcContext, () -> {
                logAgentStart(config, reviewPasses);
                return agentPassExecutor.execute(
                    config,
                    target,
                    sharedContext,
                    reviewPasses,
                    perAgentTimeoutMinutes
                );
            });
        } catch (Exception e) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to execute agent passes", e);
        }
    }

    private void logAgentStart(AgentConfig config,
                              int reviewPasses) {
        if (reviewPasses <= 1) {
            return;
        }
        logger.info("Agent {}: starting {} passes (structured)",
            config.name(), reviewPasses);
    }

}
