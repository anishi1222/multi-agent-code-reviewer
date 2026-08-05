package dev.logicojp.reviewer.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    @Test
    void noOp_alwaysAllowsRequests() {
        CircuitBreaker cb = CircuitBreaker.noOp();
        assertTrue(cb.allowRequest(), "noOp should always allow requests");
        cb.onFailure();
        cb.onFailure();
        assertTrue(cb.allowRequest(), "noOp should still allow after failures");
    }

    @Test
    void noOp_onSuccessAndOnFailureAreNoOps() {
        CircuitBreaker cb = CircuitBreaker.noOp();
        assertDoesNotThrow(cb::onSuccess);
        assertDoesNotThrow(cb::onFailure);
    }
}
