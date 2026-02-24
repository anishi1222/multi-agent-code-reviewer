package dev.logicojp.reviewer.util;

import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/// Lightweight circuit breaker for transient failures against Copilot API calls.
public final class ApiCircuitBreaker {

    private static final int MAX_OPEN_DURATION_MULTIPLIER = 8;

    private final int failureThreshold;
    private final long openDurationMs;
    private final Clock clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicInteger consecutiveProbeFailures = new AtomicInteger();
    private final AtomicLong openedAtMs = new AtomicLong(-1L);
    private final AtomicLong currentOpenDurationMs;
    private final AtomicBoolean halfOpenProbeInFlight = new AtomicBoolean(false);

    public ApiCircuitBreaker(int failureThreshold, long openDurationMs, Clock clock) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDurationMs = Math.max(1L, openDurationMs);
        this.clock = clock;
        this.currentOpenDurationMs = new AtomicLong(this.openDurationMs);
    }

    public static ApiCircuitBreaker forReview() {
        return new ApiCircuitBreaker(5, TimeUnit.SECONDS.toMillis(30), Clock.systemUTC());
    }

    public static ApiCircuitBreaker forSummary() {
        return new ApiCircuitBreaker(3, TimeUnit.SECONDS.toMillis(20), Clock.systemUTC());
    }

    public static ApiCircuitBreaker forSkill() {
        return new ApiCircuitBreaker(3, TimeUnit.SECONDS.toMillis(20), Clock.systemUTC());
    }

    public boolean isRequestAllowed() {
        long openedAt = openedAtMs.get();
        if (openedAt < 0) {
            return true;
        }
        long now = clock.millis();
        long effectiveOpenDuration = currentOpenDurationMs.get();
        if (now - openedAt >= effectiveOpenDuration) {
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
        return Math.max(0L, currentOpenDurationMs.get() - elapsed);
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        consecutiveProbeFailures.set(0);
        openedAtMs.set(-1L);
        currentOpenDurationMs.set(openDurationMs);
        halfOpenProbeInFlight.set(false);
    }

    public void recordFailure() {
        if (halfOpenProbeInFlight.getAndSet(false)) {
            int probeFailures = consecutiveProbeFailures.updateAndGet(value ->
                Math.min(MAX_OPEN_DURATION_MULTIPLIER, value + 1));
            currentOpenDurationMs.set(Math.max(openDurationMs,
                safeMultiply(openDurationMs, probeFailures)));
            openedAtMs.set(clock.millis());
            return;
        }
        int failures = consecutiveFailures.updateAndGet(value -> Math.min(Integer.MAX_VALUE - 1, value + 1));
        if (failures >= failureThreshold) {
            consecutiveProbeFailures.set(0);
            currentOpenDurationMs.set(openDurationMs);
            openedAtMs.set(clock.millis());
        }
    }

    private static long safeMultiply(long value, int multiplier) {
        if (multiplier <= 1) {
            return value;
        }
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }

    /// Manually resets the circuit breaker to closed state.
    /// Use after confirming the external service has recovered.
    public void reset() {
        consecutiveFailures.set(0);
        consecutiveProbeFailures.set(0);
        openedAtMs.set(-1L);
        currentOpenDurationMs.set(openDurationMs);
        halfOpenProbeInFlight.set(false);
    }
}