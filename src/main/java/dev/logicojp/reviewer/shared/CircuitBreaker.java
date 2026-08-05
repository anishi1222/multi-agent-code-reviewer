package dev.logicojp.reviewer.shared;

/// Minimal circuit-breaker contract used by {@link RetryExecutor}.
///
/// Implementations live in {@code domain.resilience}; the shared layer sees
/// only this interface, keeping the dependency direction clean
/// (shared ← domain, not shared → domain).
public interface CircuitBreaker {

    /// Returns {@code true} if the next request is permitted through the breaker.
    boolean allowRequest();

    /// Records a successful request, potentially resetting the failure counter.
    void onSuccess();

    /// Records a failed request, potentially opening the breaker.
    void onFailure();

    /// Returns a no-op {@link CircuitBreaker} that always allows requests.
    /// Useful for tests and environments that do not need resilience tracking.
    static CircuitBreaker noOp() {
        return new CircuitBreaker() {
            @Override
            public boolean allowRequest() {
                return true;
            }

            @Override
            public void onSuccess() {
                // no-op
            }

            @Override
            public void onFailure() {
                // no-op
            }

            @Override
            public String toString() {
                return "CircuitBreaker.noOp()";
            }
        };
    }
}
