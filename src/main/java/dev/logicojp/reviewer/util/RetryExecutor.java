package dev.logicojp.reviewer.util;

import dev.logicojp.reviewer.agent.SharedCircuitBreaker;

/// Generic retry executor with backoff and shared circuit-breaker integration.
public final class RetryExecutor<T> {

    @FunctionalInterface
    public interface AttemptExecutor<T> {
        T execute() throws Exception;
    }

    @FunctionalInterface
    public interface ExceptionMapper<T> {
        T map(Exception exception);
    }

    @FunctionalInterface
    public interface ResultSuccessPredicate<T> {
        boolean isSuccess(T result);
    }

    @FunctionalInterface
    public interface RetryableResultPredicate<T> {
        boolean isRetryable(T result);
    }

    @FunctionalInterface
    public interface TransientExceptionPredicate {
        boolean isTransient(Exception exception);
    }

    @FunctionalInterface
    public interface SleepStrategy {
        void sleep(long millis) throws InterruptedException;
    }

    public interface RetryObserver<T> {
        default void onCircuitOpen() {}

        default void onSuccess(int attempt, int totalAttempts, T result) {}

        default void onRetryableResult(int attempt, int totalAttempts, T result) {}

        default void onFinalResultFailure(int attempt, int totalAttempts, T result, boolean retryable) {}

        default void onRetryableException(int attempt, int totalAttempts, Exception exception) {}

        default void onFinalException(int attempt, int totalAttempts, Exception exception, boolean transientFailure) {}
    }

    private final int maxRetries;
    private final long backoffBaseMs;
    private final long backoffMaxMs;
    private final SleepStrategy sleepStrategy;
    private final SharedCircuitBreaker circuitBreaker;

    public RetryExecutor(int maxRetries,
                         long backoffBaseMs,
                         long backoffMaxMs,
                         SleepStrategy sleepStrategy,
                         SharedCircuitBreaker circuitBreaker) {
        this.maxRetries = maxRetries;
        this.backoffBaseMs = backoffBaseMs;
        this.backoffMaxMs = backoffMaxMs;
        this.sleepStrategy = sleepStrategy;
        this.circuitBreaker = circuitBreaker;
    }

    public T execute(AttemptExecutor<T> attemptExecutor,
                     ExceptionMapper<T> exceptionMapper,
                     ResultSuccessPredicate<T> successPredicate,
                     RetryableResultPredicate<T> retryableResultPredicate,
                     TransientExceptionPredicate transientExceptionPredicate,
                     RetryObserver<T> observer) {
        int totalAttempts = maxRetries + 1;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            if (!circuitBreaker.allowRequest()) {
                observer.onCircuitOpen();
                return exceptionMapper.map(new IllegalStateException("Circuit breaker is open for Copilot calls"));
            }
            try {
                T result = attemptExecutor.execute();
                if (successPredicate.isSuccess(result)) {
                    circuitBreaker.onSuccess();
                    observer.onSuccess(attempt, totalAttempts, result);
                    return result;
                }

                circuitBreaker.onFailure();
                boolean retryable = retryableResultPredicate.isRetryable(result);
                if (RetryPolicyUtils.shouldRetry(attempt, totalAttempts, retryable)) {
                    waitRetryBackoff(attempt);
                    observer.onRetryableResult(attempt, totalAttempts, result);
                    continue;
                }

                observer.onFinalResultFailure(attempt, totalAttempts, result, retryable);
                return result;
            } catch (Exception e) {
                T mapped = exceptionMapper.map(e);
                circuitBreaker.onFailure();

                boolean transientFailure = transientExceptionPredicate.isTransient(e);
                if (RetryPolicyUtils.shouldRetry(attempt, totalAttempts, transientFailure)) {
                    waitRetryBackoff(attempt);
                    observer.onRetryableException(attempt, totalAttempts, e);
                    continue;
                }

                observer.onFinalException(attempt, totalAttempts, e, transientFailure);
                return mapped;
            }
        }

        return exceptionMapper.map(new IllegalStateException("Retry execution exhausted all attempts"));
    }

    private void waitRetryBackoff(int attempt) {
        long backoffMs = RetryPolicyUtils.computeBackoffWithJitter(backoffBaseMs, backoffMaxMs, attempt);
        try {
            sleepStrategy.sleep(backoffMs);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}
