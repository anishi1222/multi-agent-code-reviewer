package dev.logicojp.reviewer.infrastructure.config;

import io.micronaut.context.annotation.ConfigurationProperties;

/// Configuration for Copilot circuit breakers (review/skill/summary paths).
@ConfigurationProperties("reviewer.circuit-breaker")
public record CircuitBreakerConfig(
    int failureThreshold,
    long resetTimeoutMs
) {
    public static final int DEFAULT_FAILURE_THRESHOLD = 8;
    public static final long DEFAULT_RESET_TIMEOUT_MS = 30_000L;

    public CircuitBreakerConfig {
        failureThreshold = ConfigDefaults.defaultIfNonPositive(failureThreshold, DEFAULT_FAILURE_THRESHOLD);
        resetTimeoutMs = ConfigDefaults.defaultIfNonPositive(resetTimeoutMs, DEFAULT_RESET_TIMEOUT_MS);
    }
}
