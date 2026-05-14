package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.util.RetryExecutor;
import dev.logicojp.reviewer.util.RetryPolicyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Executes review attempts with retry/backoff behavior.
final class ReviewRetryExecutor {

    static final long DEFAULT_BACKOFF_BASE_MS = 1000L;
    static final long DEFAULT_BACKOFF_MAX_MS = 30_000L;
    private static final SharedCircuitBreaker DEFAULT_REVIEW_CIRCUIT_BREAKER =
        SharedCircuitBreaker.forReviewDomain();

    @FunctionalInterface
    interface AttemptExecutor {
        ReviewResult execute() throws Exception;
    }

    @FunctionalInterface
    interface ExceptionMapper {
        ReviewResult map(Exception e);
    }

    @FunctionalInterface
    interface SleepStrategy {
        void sleep(long millis) throws InterruptedException;
    }

    private static final Logger logger = LoggerFactory.getLogger(ReviewRetryExecutor.class);

    private final String agentName;
    private final RetryExecutor<ReviewResult> retryExecutor;

    ReviewRetryExecutor(String agentName,
                        int maxRetries,
                        long backoffBaseMs,
                        long backoffMaxMs) {
        this(agentName, maxRetries, backoffBaseMs, backoffMaxMs, Thread::sleep, DEFAULT_REVIEW_CIRCUIT_BREAKER);
    }

    ReviewRetryExecutor(String agentName, int maxRetries) {
        this(agentName, maxRetries, DEFAULT_BACKOFF_BASE_MS, DEFAULT_BACKOFF_MAX_MS, Thread::sleep,
            DEFAULT_REVIEW_CIRCUIT_BREAKER);
    }

    ReviewRetryExecutor(String agentName,
                        int maxRetries,
                        long backoffBaseMs,
                        long backoffMaxMs,
                        SleepStrategy sleepStrategy) {
        this(agentName, maxRetries, backoffBaseMs, backoffMaxMs, sleepStrategy, DEFAULT_REVIEW_CIRCUIT_BREAKER);
    }

    ReviewRetryExecutor(String agentName,
                        int maxRetries,
                        long backoffBaseMs,
                        long backoffMaxMs,
                        SleepStrategy sleepStrategy,
                        SharedCircuitBreaker circuitBreaker) {
        this.agentName = agentName;
        this.retryExecutor = new RetryExecutor<>(
            maxRetries,
            backoffBaseMs,
            backoffMaxMs,
            sleepStrategy::sleep,
            circuitBreaker
        );
    }

    ReviewResult execute(AttemptExecutor attemptExecutor, ExceptionMapper exceptionMapper) {
        return retryExecutor.execute(
            attemptExecutor::execute,
            exceptionMapper::map,
            ReviewResult::success,
            this::isRetryableFailure,
            this::isTransientException,
            new RetryExecutor.RetryObserver<>() {
                @Override
                public void onCircuitOpen() {
                    logger.warn("Agent {} skipped by open circuit breaker", agentName);
                }

                @Override
                public void onSuccess(int attempt, int totalAttempts, ReviewResult result) {
                    logRetrySuccess(attempt, totalAttempts);
                }

                @Override
                public void onRetryableResult(int attempt, int totalAttempts, ReviewResult result) {
                    logResultFailureRetry(attempt, totalAttempts, result.errorMessage());
                }

                @Override
                public void onFinalResultFailure(int attempt,
                                                 int totalAttempts,
                                                 ReviewResult result,
                                                 boolean retryable) {
                    logResultFailureFinal(attempt, totalAttempts, result.errorMessage());
                    if (!retryable) {
                        logger.info("Agent {} encountered non-retryable failure on attempt {}/{}",
                            agentName, attempt, totalAttempts);
                    }
                }

                @Override
                public void onRetryableException(int attempt, int totalAttempts, Exception exception) {
                    logExceptionRetry(attempt, totalAttempts, exception);
                }

                @Override
                public void onFinalException(int attempt,
                                             int totalAttempts,
                                             Exception exception,
                                             boolean transientFailure) {
                    logExceptionFinal(attempt, totalAttempts, exception);
                    if (!transientFailure) {
                        logger.info("Agent {} encountered non-transient exception and will not retry: {}",
                            agentName, exception.getClass().getSimpleName());
                    }
                }
            }
        );
    }

    private void logRetrySuccess(int attempt, int totalAttempts) {
        if (attempt > 1) {
            logger.info("Agent {} succeeded on attempt {}/{}", agentName, attempt, totalAttempts);
        }
    }

    private void logResultFailureRetry(int attempt, int totalAttempts, String errorMessage) {
        logger.warn("Agent {} failed on attempt {}/{}: {}. Retrying...",
            agentName, attempt, totalAttempts, errorMessage);
    }

    private void logResultFailureFinal(int attempt, int totalAttempts, String errorMessage) {
        logger.error("Agent {} failed on final attempt {}/{}: {}",
            agentName, attempt, totalAttempts, errorMessage);
    }

    private void logExceptionRetry(int attempt, int totalAttempts, Exception e) {
        logger.warn("Agent {} threw exception on attempt {}/{}: {}. Retrying...",
            agentName, attempt, totalAttempts, e.getMessage(), e);
    }

    private void logExceptionFinal(int attempt, int totalAttempts, Exception e) {
        logger.error("Agent {} threw exception on final attempt {}/{}: {}",
            agentName, attempt, totalAttempts, e.getMessage(), e);
    }

    private boolean isTransientException(Exception exception) {
        return RetryPolicyUtils.isTransientException(exception);
    }

    private boolean isRetryableFailure(ReviewResult result) {
        return RetryPolicyUtils.isRetryableFailureMessage(result.errorMessage());
    }
}
