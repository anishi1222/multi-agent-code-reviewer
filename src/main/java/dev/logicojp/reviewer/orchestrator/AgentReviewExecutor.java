package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.util.ExecutionCorrelation;
import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.List;

final class AgentReviewExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AgentReviewExecutor.class);
    private final Semaphore concurrencyLimit;
    private final ExecutorService agentExecutionExecutor;
    private final AgentReviewerFactory reviewerFactory;

    AgentReviewExecutor(Semaphore concurrencyLimit,
                        ExecutorService agentExecutionExecutor,
                        AgentReviewerFactory reviewerFactory) {
        this.concurrencyLimit = concurrencyLimit;
        this.agentExecutionExecutor = agentExecutionExecutor;
        this.reviewerFactory = reviewerFactory;
    }

    List<ReviewResult> executeAgentPassesSafely(AgentConfig config,
                                                ReviewTarget target,
                                                ReviewContext context,
                                                int reviewPasses,
                                                long perAgentTimeoutMinutes) {
        try {
            concurrencyLimit.acquire();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return ReviewResult.failedResults(config, target.displayName(), reviewPasses,
                "Review interrupted while waiting for concurrency permit");
        }
        try {
            return executePassesWithTimeout(config, target, context, reviewPasses, perAgentTimeoutMinutes);
        } finally {
            concurrencyLimit.release();
        }
    }

    private List<ReviewResult> executePassesWithTimeout(AgentConfig config,
                                                        ReviewTarget target,
                                                        ReviewContext context,
                                                        int reviewPasses,
                                                        long perAgentTimeoutMinutes) {
        Future<List<ReviewResult>> future = null;
        try {
            AgentReviewer reviewer = reviewerFactory.create(config, context);
            var parentMdcContext = ExecutionCorrelation.captureMdcContext();
            future = agentExecutionExecutor.submit(
                () -> ExecutionCorrelation.callWithMdcContext(parentMdcContext, () -> reviewer.reviewPasses(target, reviewPasses))
            );
            try {
                long totalTimeoutMinutes = perAgentTimeoutMinutes * Math.max(1, reviewPasses);
                return future.get(totalTimeoutMinutes, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw e;
            }
        } catch (TimeoutException e) {
            long totalTimeoutMinutes = perAgentTimeoutMinutes * Math.max(1, reviewPasses);
            logger.warn("Agent {} timed out after {} minutes for {} pass(es)",
                config.name(), totalTimeoutMinutes, reviewPasses, e);
            return ReviewResult.failedResults(config, target.displayName(), reviewPasses,
                "Review timed out after " + totalTimeoutMinutes + " minutes");
        } catch (ExecutionException e) {
            logger.error("Agent {} execution failed: {}", config.name(), e.getMessage(), e);
            return ReviewResult.failedResults(config, target.displayName(), reviewPasses,
                "Review failed: " + e.getMessage());
        } catch (InterruptedException _) {
            cancelIfRunning(future);
            Thread.currentThread().interrupt();
            return ReviewResult.failedResults(config, target.displayName(), reviewPasses,
                "Review interrupted during execution");
        }
    }

    private void cancelIfRunning(Future<List<ReviewResult>> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }
}
