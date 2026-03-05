package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
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

    ReviewExecutionModeRunner(ExecutionConfig executionConfig,
                              ReviewResultPipeline reviewResultPipeline) {
        this.executionConfig = executionConfig;
        this.reviewResultPipeline = reviewResultPipeline;
    }

    List<ReviewResult> executeStructured(Map<String, AgentConfig> agents,
                                         ReviewTarget target,
                                         ReviewContext sharedContext,
                                         AgentPassExecutor agentPassExecutor) {
        ExecutionParams params = executionParams(agents.size());
        List<SubtaskWithConfig> tasks = new ArrayList<>(params.agentCount());
        try (var scope = StructuredTaskScope.<List<ReviewResult>>open()) {
            for (var config : agents.values()) {
                tasks.add(new SubtaskWithConfig(scope.fork(() -> executeAgentPasses(
                    config,
                    target,
                    sharedContext,
                    params.reviewPasses(),
                    params.perAgentTimeoutMinutes(),
                    agentPassExecutor
                )), config));
            }

            joinStructuredWithTimeout(scope, params.timeoutMinutes());

            return finalizeResults(
                params.reviewPasses(),
                collectStructuredResults(tasks, target, params.perAgentTimeoutMinutes())
            );
        }
    }

    private ExecutionParams executionParams(int agentCount) {
        int reviewPasses = executionConfig.reviewPasses();
        return new ExecutionParams(
            reviewPasses,
            agentCount,
            executionConfig.orchestratorTimeoutMinutes(),
            perAgentTimeoutMinutes()
        );
    }

    private long perAgentTimeoutMinutes() {
        return executionConfig.agentTimeoutMinutes() * (executionConfig.maxRetries() + 1L);
    }

    private List<ReviewResult> summarizeTaskResult(SubtaskWithConfig taskWithConfig,
                                                   ReviewTarget target,
                                                   long perAgentTimeoutMinutes) {
        var subtask = taskWithConfig.subtask();
        var state = subtask.state();
        if (state == StructuredTaskScope.Subtask.State.SUCCESS) {
            return subtask.get();
        }
        if (state == StructuredTaskScope.Subtask.State.FAILED) {
            Throwable cause = subtask.exception();
            return ReviewResult.failedResults(taskWithConfig.config(), target.displayName(), executionConfig.reviewPasses(),
                "Review failed: " + (cause != null ? cause.getMessage() : "unknown"));
        }
        return ReviewResult.failedResults(taskWithConfig.config(), target.displayName(), executionConfig.reviewPasses(),
            "Review cancelled after " + perAgentTimeoutMinutes + " minutes");
    }

    private List<ReviewResult> collectStructuredResults(
            List<SubtaskWithConfig> tasks,
            ReviewTarget target,
            long perAgentTimeoutMinutes) {
        List<ReviewResult> results = new ArrayList<>(tasks.size() * executionConfig.reviewPasses());
        for (var task : tasks) {
            results.addAll(summarizeTaskResult(task, target, perAgentTimeoutMinutes));
        }
        return results;
    }

    private List<ReviewResult> finalizeResults(int reviewPasses, List<ReviewResult> results) {
        return reviewResultPipeline.finalizeResults(results, reviewPasses);
    }

    private void joinStructuredWithTimeout(StructuredTaskScope<List<ReviewResult>, Void> scope,
                                           long timeoutMinutes) {
        try {
            StructuredConcurrencyUtils.joinWithTimeout(scope, timeoutMinutes, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Structured concurrency interrupted", e);
        } catch (TimeoutException e) {
            logger.error("Structured concurrency timed out after {} minutes", timeoutMinutes, e);
            scope.close();
        }
    }

    private List<ReviewResult> executeAgentPasses(AgentConfig config,
                                                  ReviewTarget target,
                                                  ReviewContext sharedContext,
                                                  int reviewPasses,
                                                  long perAgentTimeoutMinutes,
                                                  AgentPassExecutor agentPassExecutor) {
        logAgentStart(config, reviewPasses);
        return agentPassExecutor.execute(
            config,
            target,
            sharedContext,
            reviewPasses,
            perAgentTimeoutMinutes
        );
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