package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.resilience.SharedCircuitBreaker;
import dev.logicojp.reviewer.shared.RetryExecutor;
import dev.logicojp.reviewer.shared.RetryPolicyUtils;

import java.util.logging.Logger;

/// Executes review attempts with retry/backoff and circuit-breaker integration.
///
/// Purified from {@code agent.ReviewRetryExecutor}:
/// - Fixed imports: {@code domain.resilience.SharedCircuitBreaker},
///   {@code shared.RetryExecutor}, {@code shared.RetryPolicyUtils},
///   {@code domain.report.ReviewResult}.
/// - Replaced SLF4J with {@code java.util.logging}.
/// - No DI annotations.
public final class ReviewRetryExecutor {

    public static final long DEFAULT_BACKOFF_BASE_MS = 1_000L;
    public static final long DEFAULT_BACKOFF_MAX_MS = 30_000L;

    @FunctionalInterface
    public interface AttemptExecutor {
        ReviewResult execute() throws Exception;
    }

    @FunctionalInterface
    public interface ExceptionMapper {
        ReviewResult map(Exception e);
    }

    @FunctionalInterface
    public interface SleepStrategy {
        void sleep(long millis) throws InterruptedException;
    }

    private static final Logger logger = Logger.getLogger(ReviewRetryExecutor.class.getName());
    private static final SharedCircuitBreaker DEFAULT_CIRCUIT_BREAKER = SharedCircuitBreaker.forReviewDomain();

    private final String agentName;
    private final RetryExecutor<ReviewResult> retryExecutor;

    public ReviewRetryExecutor(String agentName, int maxRetries) {
        this(agentName, maxRetries, DEFAULT_BACKOFF_BASE_MS, DEFAULT_BACKOFF_MAX_MS,
            Thread::sleep, DEFAULT_CIRCUIT_BREAKER);
    }

    public ReviewRetryExecutor(String agentName, int maxRetries, long backoffBaseMs, long backoffMaxMs) {
        this(agentName, maxRetries, backoffBaseMs, backoffMaxMs, Thread::sleep, DEFAULT_CIRCUIT_BREAKER);
    }

    public ReviewRetryExecutor(String agentName,
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

    public ReviewResult execute(AttemptExecutor attemptExecutor, ExceptionMapper exceptionMapper) {
        return retryExecutor.execute(
            attemptExecutor::execute,
            exceptionMapper::map,
            ReviewResult::success,
            this::isRetryableFailure,
            this::isTransientException,
            new RetryExecutor.RetryObserver<>() {
                @Override
                public void onCircuitOpen() {
                    logger.warning("Agent '" + agentName + "' skipped by open circuit breaker");
                }

                @Override
                public void onSuccess(int attempt, int totalAttempts, ReviewResult result) {
                    if (attempt > 1) {
                        logger.info("Agent '" + agentName + "' succeeded on attempt "
                            + attempt + "/" + totalAttempts);
                    }
                }

                @Override
                public void onRetryableResult(int attempt, int totalAttempts, ReviewResult result) {
                    logger.warning("Agent '" + agentName + "' failed on attempt " + attempt
                        + "/" + totalAttempts + ": " + result.errorMessage() + ". Retrying...");
                }

                @Override
                public void onFinalResultFailure(int attempt, int totalAttempts, ReviewResult result,
                                                 boolean retryable) {
                    logger.severe("Agent '" + agentName + "' failed on final attempt "
                        + attempt + "/" + totalAttempts + ": " + result.errorMessage());
                }

                @Override
                public void onRetryableException(int attempt, int totalAttempts, Exception exception) {
                    logger.warning("Agent '" + agentName + "' threw exception on attempt "
                        + attempt + "/" + totalAttempts + ": " + exception.getMessage() + ". Retrying...");
                }
            }
        );
    }

    private boolean isTransientException(Exception exception) {
        return RetryPolicyUtils.isTransientException(exception);
    }

    private boolean isRetryableFailure(ReviewResult result) {
        return RetryPolicyUtils.isRetryableFailureMessage(result.errorMessage());
    }
}
