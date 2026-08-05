package dev.logicojp.reviewer.domain.resilience;

import dev.logicojp.reviewer.shared.CircuitBreaker;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/// Simple circuit breaker that tracks consecutive failures and temporarily
/// blocks requests after a threshold is exceeded.
///
/// Thread-safe via atomic operations. Implements the {@link CircuitBreaker}
/// shared-layer interface so that the shared layer has no dependency on this
/// domain class.
///
/// Constants (failure threshold, reset timeout) are inlined here; the
/// infrastructure-layer {@code CircuitBreakerConfig} is not imported.
public final class SharedCircuitBreaker implements CircuitBreaker {

    static final int DEFAULT_FAILURE_THRESHOLD = 8;
    static final long DEFAULT_RESET_TIMEOUT_MS = 30_000L;

    private static final SharedCircuitBreaker REVIEW_DOMAIN_BREAKER = withDefaultConfig();
    private static final SharedCircuitBreaker SKILL_DOMAIN_BREAKER = withDefaultConfig();
    private static final SharedCircuitBreaker SUMMARY_DOMAIN_BREAKER = withDefaultConfig();

    private final int failureThreshold;
    private final long resetTimeoutMs;
    private final LongSupplier clock;

    private record BreakerState(int consecutiveFailures, long openedAtMs) {
        static final BreakerState CLOSED = new BreakerState(0, -1L);
    }

    private final AtomicReference<BreakerState> state = new AtomicReference<>(BreakerState.CLOSED);

    public static SharedCircuitBreaker withDefaultConfig() {
        return new SharedCircuitBreaker(DEFAULT_FAILURE_THRESHOLD, DEFAULT_RESET_TIMEOUT_MS);
    }

    /// Returns the shared circuit breaker for the review execution domain.
    public static SharedCircuitBreaker forReviewDomain() {
        return REVIEW_DOMAIN_BREAKER;
    }

    /// Returns the shared circuit breaker for the skill execution domain.
    public static SharedCircuitBreaker forSkillDomain() {
        return SKILL_DOMAIN_BREAKER;
    }

    /// Returns the shared circuit breaker for the summary generation domain.
    public static SharedCircuitBreaker forSummaryDomain() {
        return SUMMARY_DOMAIN_BREAKER;
    }

    public SharedCircuitBreaker(int failureThreshold, long resetTimeoutMs) {
        this(failureThreshold, resetTimeoutMs, System::currentTimeMillis);
    }

    public SharedCircuitBreaker(int failureThreshold, long resetTimeoutMs, LongSupplier clock) {
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
        this.clock = clock;
    }

    @Override
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
            for (;;) {
                BreakerState latest = state.get();
                if (latest.consecutiveFailures() < failureThreshold) return true;
                if (latest.openedAtMs() < 0) return true;
                long latestElapsed = clock.getAsLong() - latest.openedAtMs();
                if (latestElapsed < resetTimeoutMs) return false;
                BreakerState halfOpen = new BreakerState(failureThreshold - 1, -1L);
                if (state.compareAndSet(latest, halfOpen)) return true;
            }
        }
        return false;
    }

    @Override
    public void onSuccess() {
        state.set(BreakerState.CLOSED);
    }

    @Override
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

    /// Resets the circuit breaker to its initial closed state (for testing).
    void reset() {
        state.set(BreakerState.CLOSED);
    }
}
