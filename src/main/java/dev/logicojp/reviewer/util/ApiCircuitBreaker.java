package dev.logicojp.reviewer.util;

import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/// Lightweight circuit breaker for transient failures against Copilot API calls.
public final class ApiCircuitBreaker {

    private static final ApiCircuitBreaker REVIEW_BREAKER =
        new ApiCircuitBreaker(5, TimeUnit.SECONDS.toMillis(30), Clock.systemUTC());
    private static final ApiCircuitBreaker SUMMARY_BREAKER =
        new ApiCircuitBreaker(3, TimeUnit.SECONDS.toMillis(20), Clock.systemUTC());
    private static final ApiCircuitBreaker SKILL_BREAKER =
        new ApiCircuitBreaker(3, TimeUnit.SECONDS.toMillis(20), Clock.systemUTC());

    private final int failureThreshold;
    private final long openDurationMs;
    private final Clock clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openedAtMs = new AtomicLong(-1L);
    private final AtomicBoolean halfOpenProbeInFlight = new AtomicBoolean(false);

    public ApiCircuitBreaker(int failureThreshold, long openDurationMs, Clock clock) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDurationMs = Math.max(1L, openDurationMs);
        this.clock = clock;
    }

    public static ApiCircuitBreaker forReview() {
        return REVIEW_BREAKER;
    }

    public static ApiCircuitBreaker forSummary() {
        return SUMMARY_BREAKER;
    }

    public static ApiCircuitBreaker forSkill() {
        return SKILL_BREAKER;
    }

    public boolean isRequestAllowed() {
        long openedAt = openedAtMs.get();
        if (openedAt < 0) {
            return true;
        }
        long now = clock.millis();
        if (now - openedAt >= openDurationMs) {
            return halfOpenProbeInFlight.compareAndSet(false, true);
        }
        return false;
    }

    public long remainingOpenMs() {
        long openedAt = openedAtMs.get();
        if (openedAt < 0) {
            return 0L;
        }
        long elapsed = clock.millis() - openedAt;
        return Math.max(0L, openDurationMs - elapsed);
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        openedAtMs.set(-1L);
        halfOpenProbeInFlight.set(false);
    }

    public void recordFailure() {
        if (halfOpenProbeInFlight.getAndSet(false)) {
            openedAtMs.set(clock.millis());
            return;
        }
        int failures = consecutiveFailures.updateAndGet(value -> Math.min(Integer.MAX_VALUE - 1, value + 1));
        if (failures >= failureThreshold) {
            openedAtMs.set(clock.millis());
        }
    }
}