package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.CircuitBreakerConfig;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/// Simple circuit breaker that tracks consecutive failures and temporarily
/// blocks requests after a threshold is exceeded.
///
/// Thread-safe via atomic operations and volatile fields.
public final class SharedCircuitBreaker {

    private final int failureThreshold;
    private final long resetTimeoutMs;
    private final LongSupplier clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openedAtMs = new AtomicLong(-1L);

    public static SharedCircuitBreaker withDefaultConfig() {
        return new SharedCircuitBreaker(
            CircuitBreakerConfig.DEFAULT_FAILURE_THRESHOLD,
            CircuitBreakerConfig.DEFAULT_RESET_TIMEOUT_MS
        );
    }

    public SharedCircuitBreaker(int failureThreshold, long resetTimeoutMs) {
        this(failureThreshold, resetTimeoutMs, System::currentTimeMillis);
    }

    public SharedCircuitBreaker(int failureThreshold, long resetTimeoutMs, LongSupplier clock) {
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
        this.clock = clock;
    }

    public boolean allowRequest() {
        int failures = consecutiveFailures.get();
        if (failures < failureThreshold) return true;
        long openedAt = openedAtMs.get();
        if (openedAt < 0) return true;
        long elapsedMs = clock.getAsLong() - openedAt;
        if (elapsedMs >= resetTimeoutMs) {
            // CAS ensures only one thread transitions to half-open
            if (consecutiveFailures.compareAndSet(failures, failureThreshold - 1)) {
                openedAtMs.set(-1L);
                return true;
            }
            return false;
        }
        return false;
    }

    public void onSuccess() {
        consecutiveFailures.set(0);
        openedAtMs.set(-1L);
    }

    public void onFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            openedAtMs.compareAndSet(-1L, clock.getAsLong());
        }
    }

    /// Resets the circuit breaker to its initial state.
    void reset() {
        consecutiveFailures.set(0);
        openedAtMs.set(-1L);
    }
}
