package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.CircuitBreakerConfig;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/// Simple circuit breaker that tracks consecutive failures and temporarily
/// blocks requests after a threshold is exceeded.
///
/// Thread-safe via atomic operations and volatile fields.
public final class SharedCircuitBreaker {

    private final int failureThreshold;
    private final long resetTimeoutMs;
    private final LongSupplier clock;

    private record BreakerState(int consecutiveFailures, long openedAtMs) {
        static final BreakerState CLOSED = new BreakerState(0, -1L);
    }

    private final AtomicReference<BreakerState> state = new AtomicReference<>(BreakerState.CLOSED);

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
        BreakerState current = state.get();
        int failures = current.consecutiveFailures();
        if (failures < failureThreshold) {
            return true;
        }
        long openedAt = current.openedAtMs();
        if (openedAt < 0) return true;
        long elapsedMs = clock.getAsLong() - openedAt;
        if (elapsedMs >= resetTimeoutMs) {
            // CAS retry ensures we do not reject a valid probe request on contention.
            for (;;) {
                BreakerState latest = state.get();
                if (latest.consecutiveFailures() < failureThreshold) {
                    return true;
                }
                if (latest.openedAtMs() < 0) {
                    return true;
                }
                long latestElapsedMs = clock.getAsLong() - latest.openedAtMs();
                if (latestElapsedMs < resetTimeoutMs) {
                    return false;
                }
                BreakerState halfOpen = new BreakerState(failureThreshold - 1, -1L);
                if (state.compareAndSet(latest, halfOpen)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void onSuccess() {
        state.set(BreakerState.CLOSED);
    }

    public void onFailure() {
        state.updateAndGet(current -> {
            int failures = current.consecutiveFailures() + 1;
            long openedAt = current.openedAtMs();
            if (failures >= failureThreshold && openedAt < 0) {
                openedAt = clock.getAsLong();
            }
            return new BreakerState(failures, openedAt);
        });
    }

    /// Resets the circuit breaker to its initial state.
    void reset() {
        state.set(BreakerState.CLOSED);
    }
}
