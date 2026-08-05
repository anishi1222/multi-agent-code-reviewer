package dev.logicojp.reviewer.domain.resilience;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SharedCircuitBreakerTest {

    @Test
    void allowsRequests_whenBelowThreshold() {
        SharedCircuitBreaker cb = new SharedCircuitBreaker(3, 10_000L);
        assertTrue(cb.allowRequest());
        cb.onFailure();
        cb.onFailure();
        assertTrue(cb.allowRequest(), "below threshold: should still allow");
    }

    @Test
    void blocksRequests_whenThresholdReached() {
        SharedCircuitBreaker cb = new SharedCircuitBreaker(3, 10_000L, System::currentTimeMillis);
        cb.onFailure();
        cb.onFailure();
        cb.onFailure();
        assertFalse(cb.allowRequest(), "at threshold: should block");
    }

    @Test
    void resetsAfterTimeout() {
        long[] fakeClock = { 0L };
        SharedCircuitBreaker cb = new SharedCircuitBreaker(2, 5_000L, () -> fakeClock[0]);
        cb.onFailure();
        cb.onFailure();
        assertFalse(cb.allowRequest(), "before timeout: should block");

        fakeClock[0] = 6_000L;
        assertTrue(cb.allowRequest(), "after timeout: should allow probe");
    }

    @Test
    void onSuccess_resetsBreakerToClosedState() {
        SharedCircuitBreaker cb = new SharedCircuitBreaker(2, 10_000L);
        cb.onFailure();
        cb.onFailure();
        assertFalse(cb.allowRequest());

        cb.reset();
        assertTrue(cb.allowRequest(), "after reset: should allow again");
        cb.onSuccess();
        assertTrue(cb.allowRequest(), "after onSuccess: should allow");
    }

    @Test
    void defaultConstants_matchExpectedValues() {
        assertEquals(8, SharedCircuitBreaker.DEFAULT_FAILURE_THRESHOLD);
        assertEquals(30_000L, SharedCircuitBreaker.DEFAULT_RESET_TIMEOUT_MS);
    }

    @Test
    void implementsCircuitBreakerInterface() {
        SharedCircuitBreaker cb = SharedCircuitBreaker.withDefaultConfig();
        assertTrue(cb instanceof dev.logicojp.reviewer.shared.CircuitBreaker,
            "SharedCircuitBreaker must implement shared.CircuitBreaker");
    }
}
