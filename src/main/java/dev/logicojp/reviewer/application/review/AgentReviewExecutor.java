package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.outbound.McpServerSpec;
import dev.logicojp.reviewer.application.port.outbound.PropagateCorrelationPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.review.ReviewContext;
import dev.logicojp.reviewer.domain.review.ReviewTarget;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Logger;

/// Dispatches per-agent review execution with concurrency permit and timeout management.
///
/// Purified from {@code orchestrator.AgentReviewExecutor}:
/// - Replaced {@code AgentReviewerFactory} with {@link ReviewPassRunner}
///   + {@link RubberDuckDialogueRunner}.
/// - Replaced SLF4J with {@code java.util.logging} for this class's own diagnostics.
/// - Correlation-context propagation is no longer done with a direct MDC call; it goes
///   through {@link PropagateCorrelationPort} so the capability survives without the
///   application layer importing a logging framework.
/// - Removed {@code TemplateService} reference (template loading moved to {@code RubberDuckDialogueRunner}).
public final class AgentReviewExecutor {

    private static final Logger logger = Logger.getLogger(AgentReviewExecutor.class.getName());

    private final Semaphore concurrencyLimit;
    private final ExecutorService agentExecutionExecutor;
    private final ReviewPassRunner reviewPassRunner;
    private final RubberDuckDialogueRunner rubberDuckDialogueRunner;
    private final OrchestratorMetrics metrics;
    private final PropagateCorrelationPort propagateCorrelation;

    public AgentReviewExecutor(Semaphore concurrencyLimit,
                               ExecutorService agentExecutionExecutor,
                               ReviewPassRunner reviewPassRunner,
                               RubberDuckDialogueRunner rubberDuckDialogueRunner,
                               OrchestratorMetrics metrics,
                               PropagateCorrelationPort propagateCorrelation) {
        this.concurrencyLimit = concurrencyLimit;
        this.agentExecutionExecutor = agentExecutionExecutor;
        this.reviewPassRunner = reviewPassRunner;
        this.rubberDuckDialogueRunner = rubberDuckDialogueRunner;
        this.metrics = metrics;
        this.propagateCorrelation = propagateCorrelation;
    }

    /// Executes standard review passes for an agent with concurrency permit and metrics.
    public List<ReviewResult> executeAgentPassesSafely(AgentConfig config,
                                                        ReviewTarget target,
                                                        ReviewContext context,
                                                        int reviewPasses,
                                                        long perAgentTimeoutMinutes,
                                                        List<McpServerSpec> mcpServers,
                                                        int maxRetries) {
        return executeWithPermitAndMetrics(
            config, target.displayName(), reviewPasses,
            () -> executePassesWithTimeout(
                config, target, context, reviewPasses, perAgentTimeoutMinutes, mcpServers, maxRetries)
        );
    }

    /// Executes rubber-duck dialogue review for an agent with concurrency permit and metrics.
    public List<ReviewResult> executeRubberDuckSafely(AgentConfig config,
                                                       ReviewTarget target,
                                                       ReviewContext context,
                                                       int rubberDuckRounds,
                                                       long perAgentTimeoutMinutes,
                                                       List<McpServerSpec> mcpServers) {
        return executeWithPermitAndMetrics(
            config, target.displayName(), 1,
            () -> executeRubberDuckWithTimeout(
                config, target, context, rubberDuckRounds, perAgentTimeoutMinutes, mcpServers)
        );
    }

    private List<ReviewResult> executeWithPermitAndMetrics(AgentConfig config,
                                                            String targetDisplayName,
                                                            int failureResultCount,
                                                            Supplier<List<ReviewResult>> execution) {
        long permitWaitStartNanos = System.nanoTime();
        try {
            concurrencyLimit.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long permitWaitMs = OrchestratorMetrics.nanosToMillis(System.nanoTime() - permitWaitStartNanos);
            metrics.recordInterrupted(config.name(), 0, permitWaitMs);
            return ReviewResult.failedResults(config, targetDisplayName, failureResultCount,
                "Review interrupted while waiting for concurrency permit");
        }
        long permitWaitMs = OrchestratorMetrics.nanosToMillis(System.nanoTime() - permitWaitStartNanos);
        long executionStartNanos = System.nanoTime();
        try {
            var results = execution.get();
            long durationMs = OrchestratorMetrics.nanosToMillis(System.nanoTime() - executionStartNanos);
            boolean success = results.stream().anyMatch(ReviewResult::success);
            if (success) {
                metrics.recordSuccess(config.name(), durationMs, permitWaitMs);
            } else {
                metrics.recordFailure(config.name(), durationMs, permitWaitMs);
            }
            return results;
        } catch (Exception e) {
            long durationMs = OrchestratorMetrics.nanosToMillis(System.nanoTime() - executionStartNanos);
            metrics.recordFailure(config.name(), durationMs, permitWaitMs);
            logger.warning(() -> "Agent '" + config.name() + "' threw exception: " + e.getMessage());
            return ReviewResult.failedResults(config, targetDisplayName, failureResultCount,
                "Review threw exception: " + e.getMessage());
        } finally {
            concurrencyLimit.release();
        }
    }

    private List<ReviewResult> executePassesWithTimeout(AgentConfig config,
                                                         ReviewTarget target,
                                                         ReviewContext context,
                                                         int reviewPasses,
                                                         long perAgentTimeoutMinutes,
                                                         List<McpServerSpec> mcpServers,
                                                         int maxRetries) {
        // Capture on the caller's thread; the submitted task runs on a pool thread whose
        // correlation context would otherwise start empty (see PropagateCorrelationPort).
        Map<String, String> parentContext = propagateCorrelation.captureContext();
        Future<List<ReviewResult>> future = agentExecutionExecutor.submit(
            () -> propagateCorrelation.callWithContext(parentContext,
                () -> reviewPassRunner.run(config, target, context, reviewPasses, mcpServers, maxRetries)));
        return getWithTimeout(future, config, target, reviewPasses, perAgentTimeoutMinutes);
    }

    private List<ReviewResult> executeRubberDuckWithTimeout(AgentConfig config,
                                                             ReviewTarget target,
                                                             ReviewContext context,
                                                             int rubberDuckRounds,
                                                             long perAgentTimeoutMinutes,
                                                             List<McpServerSpec> mcpServers) {
        Map<String, String> parentContext = propagateCorrelation.captureContext();
        Future<List<ReviewResult>> future = agentExecutionExecutor.submit(
            () -> propagateCorrelation.callWithContext(parentContext,
                () -> rubberDuckDialogueRunner.run(config, target, context, rubberDuckRounds, mcpServers)));
        return getWithTimeout(future, config, target, 1, perAgentTimeoutMinutes);
    }

    private List<ReviewResult> getWithTimeout(Future<List<ReviewResult>> future,
                                               AgentConfig config,
                                               ReviewTarget target,
                                               int failureCount,
                                               long timeoutMinutes) {
        try {
            return future.get(timeoutMinutes, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            future.cancel(true);
            return ReviewResult.failedResults(config, target.displayName(), failureCount,
                "Review timed out after " + timeoutMinutes + " minutes");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return ReviewResult.failedResults(config, target.displayName(), failureCount, "Review interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return ReviewResult.failedResults(config, target.displayName(), failureCount,
                "Review failed: " + (cause != null ? cause.getMessage() : e.getMessage()));
        }
    }
}
