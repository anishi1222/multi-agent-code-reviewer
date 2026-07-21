package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.config.RubberDuckConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.service.TemplateService;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.ExecutionCorrelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

final class AgentReviewExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AgentReviewExecutor.class);
    private final Semaphore concurrencyLimit;
    private final ExecutorService agentExecutionExecutor;
    private final AgentReviewerFactory reviewerFactory;
    private final OrchestratorMetrics metrics;

    AgentReviewExecutor(Semaphore concurrencyLimit,
                        ExecutorService agentExecutionExecutor,
                        AgentReviewerFactory reviewerFactory,
                        OrchestratorMetrics metrics) {
        this.concurrencyLimit = concurrencyLimit;
        this.agentExecutionExecutor = agentExecutionExecutor;
        this.reviewerFactory = reviewerFactory;
        this.metrics = metrics;
    }

    ReviewResult executeAgentSafely(AgentConfig config,
                                    ReviewTarget target,
                                    ReviewContext context,
                                    long perAgentTimeoutMinutes) {
        return executeWithPermitAndMetrics(
            config,
            target.displayName(),
            () -> executeReviewWithTimeout(config, target, context, perAgentTimeoutMinutes)
        );
    }

    ReviewResult executeRubberDuckSafely(AgentConfig config,
                                         ReviewTarget target,
                                         ReviewContext context,
                                         RubberDuckConfig rubberDuckConfig,
                                         TemplateService templateService,
                                         long perAgentTimeoutMinutes) {
        return executeWithPermitAndMetrics(
            config,
            target.displayName(),
            () -> executeRubberDuckWithTimeout(
                config,
                target,
                context,
                rubberDuckConfig,
                templateService,
                perAgentTimeoutMinutes
            )
        );
    }

    private ReviewResult executeWithPermitAndMetrics(
            AgentConfig config,
            String targetDisplayName,
            Supplier<ReviewResult> execution) {
        long permitWaitStartNanos = System.nanoTime();
        try {
            concurrencyLimit.acquire();
        } catch (InterruptedException _) {
            long permitWaitMs = OrchestratorMetrics.nanosToMillis(System.nanoTime() - permitWaitStartNanos);
            metrics.recordAgentExecution(config.name(), 0, permitWaitMs,
                OrchestratorMetrics.OutcomeType.INTERRUPTED);
            Thread.currentThread().interrupt();
            return ReviewResult.failed(
                config,
                targetDisplayName,
                "Review interrupted while waiting for concurrency permit"
            );
        }
        long permitWaitMs = OrchestratorMetrics.nanosToMillis(System.nanoTime() - permitWaitStartNanos);
        metrics.logPermitWaitIfSignificant(config.name(), permitWaitMs);

        long executionStartNanos = System.nanoTime();
        try {
            ReviewResult result = execution.get();
            long durationMs = OrchestratorMetrics.nanosToMillis(System.nanoTime() - executionStartNanos);
            metrics.recordAgentExecution(
                config.name(),
                durationMs,
                permitWaitMs,
                OrchestratorMetrics.classifyOutcome(result)
            );
            return result;
        } finally {
            concurrencyLimit.release();
        }
    }

    private ReviewResult executeReviewWithTimeout(AgentConfig config,
                                                  ReviewTarget target,
                                                  ReviewContext context,
                                                  long perAgentTimeoutMinutes) {
        Future<ReviewResult> future = null;
        try {
            AgentReviewer reviewer = reviewerFactory.create(config, context);
            var parentMdcContext = ExecutionCorrelation.captureMdcContext();
            future = agentExecutionExecutor.submit(
                () -> ExecutionCorrelation.callWithMdcContext(
                    parentMdcContext,
                    () -> reviewer.review(target)
                )
            );
            try {
                return future.get(perAgentTimeoutMinutes, TimeUnit.MINUTES);
            } catch (TimeoutException exception) {
                future.cancel(true);
                throw exception;
            }
        } catch (TimeoutException exception) {
            logger.warn("Agent {} timed out after {} minutes",
                config.name(), perAgentTimeoutMinutes, exception);
            return ReviewResult.failed(
                config,
                target.displayName(),
                "Review timed out after " + perAgentTimeoutMinutes + " minutes"
            );
        } catch (ExecutionException exception) {
            logger.error("Agent {} execution failed: {}", config.name(), exception.getMessage(), exception);
            return ReviewResult.failed(
                config,
                target.displayName(),
                "Review failed: " + exception.getMessage()
            );
        } catch (InterruptedException _) {
            cancelIfRunning(future);
            Thread.currentThread().interrupt();
            return ReviewResult.failed(
                config,
                target.displayName(),
                "Review interrupted during execution"
            );
        }
    }

    private ReviewResult executeRubberDuckWithTimeout(AgentConfig config,
                                                       ReviewTarget target,
                                                       ReviewContext context,
                                                       RubberDuckConfig rubberDuckConfig,
                                                       TemplateService templateService,
                                                       long perAgentTimeoutMinutes) {
        Future<ReviewResult> future = null;
        int dialogueRounds = config.effectiveDialogueRounds(rubberDuckConfig);
        long totalTimeoutMinutes = perAgentTimeoutMinutes * (dialogueRounds * 2L + 1);
        try {
            AgentReviewer reviewer = reviewerFactory.create(config, context);
            var parentMdcContext = ExecutionCorrelation.captureMdcContext();
            future = agentExecutionExecutor.submit(
                () -> ExecutionCorrelation.callWithMdcContext(
                    parentMdcContext,
                    () -> reviewer.reviewRubberDuck(target, rubberDuckConfig, templateService)
                )
            );
            try {
                return future.get(totalTimeoutMinutes, TimeUnit.MINUTES);
            } catch (TimeoutException exception) {
                future.cancel(true);
                throw exception;
            }
        } catch (TimeoutException exception) {
            logger.warn("Agent {} rubber-duck timed out after {} minutes ({} rounds)",
                config.name(), totalTimeoutMinutes, dialogueRounds, exception);
            return ReviewResult.failed(
                config,
                target.displayName(),
                "Rubber-duck review timed out after " + totalTimeoutMinutes + " minutes"
            );
        } catch (ExecutionException exception) {
            logger.error("Agent {} rubber-duck execution failed: {}",
                config.name(), exception.getMessage(), exception);
            return ReviewResult.failed(
                config,
                target.displayName(),
                "Rubber-duck review failed: " + exception.getMessage()
            );
        } catch (InterruptedException _) {
            cancelIfRunning(future);
            Thread.currentThread().interrupt();
            return ReviewResult.failed(
                config,
                target.displayName(),
                "Rubber-duck review interrupted during execution"
            );
        }
    }

    private static void cancelIfRunning(Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }
}
