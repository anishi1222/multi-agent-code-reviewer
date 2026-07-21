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

    private record ExecutionParams(int agentCount, long timeoutMinutes, long perAgentTimeoutMinutes) {
    }

    private record SubtaskWithConfig(StructuredTaskScope.Subtask<ReviewResult> subtask, AgentConfig config) {
    }

    @FunctionalInterface
    interface AgentExecutor {
        ReviewResult execute(AgentConfig config,
                             ReviewTarget target,
                             ReviewContext context,
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
                                         AgentExecutor agentExecutor) {
        return executeStructured(
            agents,
            target,
            sharedContext,
            executionConfig.orchestratorTimeoutMinutes(),
            agentExecutor
        );
    }

    List<ReviewResult> executeStructured(Map<String, AgentConfig> agents,
                                         ReviewTarget target,
                                         ReviewContext sharedContext,
                                         long orchestratorTimeoutMinutes,
                                         AgentExecutor agentExecutor) {
        metrics.markRunStart();
        try {
            return executeStructuredInternal(
                agents,
                target,
                sharedContext,
                orchestratorTimeoutMinutes,
                agentExecutor
            );
        } finally {
            metrics.markRunEnd();
            metrics.logSummary();
        }
    }

    private List<ReviewResult> executeStructuredInternal(Map<String, AgentConfig> agents,
                                                         ReviewTarget target,
                                                         ReviewContext sharedContext,
                                                         long orchestratorTimeoutMinutes,
                                                         AgentExecutor agentExecutor) {
        ExecutionParams params = executionParams(agents.size(), orchestratorTimeoutMinutes);
        List<SubtaskWithConfig> tasks = new ArrayList<>(params.agentCount());
        var parentMdcContext = ExecutionCorrelation.captureMdcContext();
        try (var scope = StructuredConcurrencyUtils.<ReviewResult>openAwaitAllScope()) {
            for (var config : agents.values()) {
                var subtask = scope.fork(() -> executeAgent(
                    config,
                    target,
                    sharedContext,
                    params.perAgentTimeoutMinutes(),
                    agentExecutor,
                    parentMdcContext
                ));
                tasks.add(new SubtaskWithConfig(subtask, config));
            }

            joinStructuredWithTimeout(scope, tasks, params.timeoutMinutes());
            return reviewResultPipeline.finalizeResults(
                collectStructuredResults(tasks, target, params.perAgentTimeoutMinutes())
            );
        }
    }

    private ExecutionParams executionParams(int agentCount, long orchestratorTimeoutMinutes) {
        return new ExecutionParams(
            agentCount,
            Math.max(1L, orchestratorTimeoutMinutes),
            executionConfig.agentTimeoutMinutes() * (executionConfig.maxRetries() + 1L)
        );
    }

    private ReviewResult summarizeTaskResult(SubtaskWithConfig taskWithConfig,
                                             ReviewTarget target,
                                             long perAgentTimeoutMinutes) {
        return switch (taskWithConfig.subtask().state()) {
            case SUCCESS -> taskWithConfig.subtask().get();
            case FAILED -> {
                Throwable cause = taskWithConfig.subtask().exception();
                yield ReviewResult.failed(
                    taskWithConfig.config(),
                    target.displayName(),
                    "Review failed: " + (cause != null ? cause.getMessage() : "unknown")
                );
            }
            case UNAVAILABLE -> ReviewResult.failed(
                taskWithConfig.config(),
                target.displayName(),
                "Review cancelled after " + perAgentTimeoutMinutes + " minutes"
            );
        };
    }

    private List<ReviewResult> collectStructuredResults(
            List<SubtaskWithConfig> tasks,
            ReviewTarget target,
            long perAgentTimeoutMinutes) {
        List<ReviewResult> results = new ArrayList<>(tasks.size());
        for (var task : tasks) {
            results.add(summarizeTaskResult(task, target, perAgentTimeoutMinutes));
        }
        return results;
    }

    @SuppressWarnings("rawtypes")
    private void joinStructuredWithTimeout(StructuredTaskScope scope,
                                           List<SubtaskWithConfig> tasks,
                                           long timeoutMinutes) {
        try {
            StructuredConcurrencyUtils.joinWithTimeout(scope, timeoutMinutes, TimeUnit.MINUTES);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.error("Structured concurrency interrupted", exception);
        } catch (TimeoutException exception) {
            int unfinishedTaskCount = (int) tasks.stream()
                .map(SubtaskWithConfig::subtask)
                .filter(subtask -> subtask.state() == StructuredTaskScope.Subtask.State.UNAVAILABLE)
                .count();
            logger.error(
                "Structured concurrency timed out after {} minutes; cancelling {} unfinished task(s)",
                timeoutMinutes,
                unfinishedTaskCount,
                exception
            );
            scope.close();
        }
    }

    private ReviewResult executeAgent(AgentConfig config,
                                      ReviewTarget target,
                                      ReviewContext sharedContext,
                                      long perAgentTimeoutMinutes,
                                      AgentExecutor agentExecutor,
                                      Map<String, String> parentMdcContext) {
        try {
            return ExecutionCorrelation.callWithMdcContext(
                parentMdcContext,
                () -> agentExecutor.execute(
                    config,
                    target,
                    sharedContext,
                    perAgentTimeoutMinutes
                )
            );
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to execute agent review", exception);
        }
    }
}
