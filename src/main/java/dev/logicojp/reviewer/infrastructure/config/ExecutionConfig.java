package dev.logicojp.reviewer.infrastructure.config;

import dev.logicojp.reviewer.shared.ConfigDefaults;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;

import java.util.Objects;

/// Configuration for execution settings (parallelism, timeouts).
@ConfigurationProperties("reviewer.execution")
public record ExecutionConfig(
    @Nullable ConcurrencySettings concurrency,
    @Nullable TimeoutSettings timeouts,
    @Nullable RetrySettings retry,
    @Nullable BufferSettings buffers,
    @Bindable(defaultValue = "true") @Nullable Boolean sharedSessionEnabled,
    @Bindable(defaultValue = "false") @Nullable Boolean ghAuthFallbackEnabled
) {

    @ConfigurationProperties("concurrency")
    public record ConcurrencySettings(
        @Bindable(defaultValue = "4") int parallelism,
        @Bindable(defaultValue = "1") int reviewPasses
    ) {
        public ConcurrencySettings {
            parallelism = ConfigDefaults.defaultIfNonPositive(parallelism, DEFAULT_PARALLELISM);
            reviewPasses = ConfigDefaults.defaultIfNonPositive(reviewPasses, DEFAULT_REVIEW_PASSES);
        }
    }

    @ConfigurationProperties("timeouts")
    public record TimeoutSettings(
        @Bindable(defaultValue = "10") long orchestratorTimeoutMinutes,
        @Bindable(defaultValue = "5") long agentTimeoutMinutes,
        @Bindable(defaultValue = "5") long idleTimeoutMinutes,
        @Bindable(defaultValue = "5") long skillTimeoutMinutes,
        @Bindable(defaultValue = "5") long summaryTimeoutMinutes,
        @Bindable(defaultValue = "10") long ghAuthTimeoutSeconds
    ) {
        public TimeoutSettings {
            orchestratorTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(orchestratorTimeoutMinutes, DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES);
            agentTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(agentTimeoutMinutes, DEFAULT_AGENT_TIMEOUT_MINUTES);
            idleTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(idleTimeoutMinutes, DEFAULT_IDLE_TIMEOUT_MINUTES);
            skillTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(skillTimeoutMinutes, DEFAULT_SKILL_TIMEOUT_MINUTES);
            summaryTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(summaryTimeoutMinutes, DEFAULT_SUMMARY_TIMEOUT_MINUTES);
            ghAuthTimeoutSeconds = ConfigDefaults.defaultIfNonPositive(ghAuthTimeoutSeconds, DEFAULT_GH_AUTH_TIMEOUT_SECONDS);
        }
    }

    @ConfigurationProperties("retry")
    public record RetrySettings(@Bindable(defaultValue = "2") int maxRetries) {
        public RetrySettings {
            maxRetries = ConfigDefaults.defaultIfNegative(maxRetries, DEFAULT_MAX_RETRIES);
        }
    }

    @ConfigurationProperties("buffers")
    public record BufferSettings(@Bindable(defaultValue = "32") int instructionBufferExtraCapacity) {
        public BufferSettings {
            instructionBufferExtraCapacity = ConfigDefaults.defaultIfNonPositive(
                instructionBufferExtraCapacity, DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY);
        }
    }

    public static final int DEFAULT_MAX_RETRIES = 2;
    public static final long DEFAULT_IDLE_TIMEOUT_MINUTES = 5;
    public static final int DEFAULT_REVIEW_PASSES = 1;
    public static final boolean DEFAULT_SHARED_SESSION_ENABLED = true;
    public static final boolean DEFAULT_GH_AUTH_FALLBACK_ENABLED = false;
    private static final int DEFAULT_PARALLELISM = 4;
    private static final long DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES = 10;
    private static final long DEFAULT_AGENT_TIMEOUT_MINUTES = 5;
    private static final long DEFAULT_SKILL_TIMEOUT_MINUTES = 5;
    private static final long DEFAULT_SUMMARY_TIMEOUT_MINUTES = 5;
    private static final long DEFAULT_GH_AUTH_TIMEOUT_SECONDS = 10;
    public static final int DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY = 32;

    public ExecutionConfig {
        concurrency = Objects.requireNonNullElse(concurrency, defaultConcurrency());
        timeouts = Objects.requireNonNullElse(timeouts, defaultTimeouts());
        retry = Objects.requireNonNullElse(retry, defaultRetry());
        buffers = Objects.requireNonNullElse(buffers, defaultBuffers());
        sharedSessionEnabled = sharedSessionEnabled != null ? sharedSessionEnabled : DEFAULT_SHARED_SESSION_ENABLED;
        ghAuthFallbackEnabled = ghAuthFallbackEnabled != null ? ghAuthFallbackEnabled : DEFAULT_GH_AUTH_FALLBACK_ENABLED;
    }

    private static ConcurrencySettings defaultConcurrency() {
        return new ConcurrencySettings(DEFAULT_PARALLELISM, DEFAULT_REVIEW_PASSES);
    }

    private static TimeoutSettings defaultTimeouts() {
        return new TimeoutSettings(DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES, DEFAULT_AGENT_TIMEOUT_MINUTES,
            DEFAULT_IDLE_TIMEOUT_MINUTES, DEFAULT_SKILL_TIMEOUT_MINUTES,
            DEFAULT_SUMMARY_TIMEOUT_MINUTES, DEFAULT_GH_AUTH_TIMEOUT_SECONDS);
    }

    private static RetrySettings defaultRetry() {
        return new RetrySettings(DEFAULT_MAX_RETRIES);
    }

    private static BufferSettings defaultBuffers() {
        return new BufferSettings(DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY);
    }

    public static ExecutionConfig defaults() {
        return new ExecutionConfig(null, null, null, null, null, null);
    }

    public int parallelism() { return concurrency.parallelism(); }
    public int reviewPasses() { return concurrency.reviewPasses(); }
    public long orchestratorTimeoutMinutes() { return timeouts.orchestratorTimeoutMinutes(); }
    public long agentTimeoutMinutes() { return timeouts.agentTimeoutMinutes(); }
    public long idleTimeoutMinutes() { return timeouts.idleTimeoutMinutes(); }
    public long skillTimeoutMinutes() { return timeouts.skillTimeoutMinutes(); }
    public long summaryTimeoutMinutes() { return timeouts.summaryTimeoutMinutes(); }
    public long ghAuthTimeoutSeconds() { return timeouts.ghAuthTimeoutSeconds(); }
    public int maxRetries() { return retry.maxRetries(); }
    public int instructionBufferExtraCapacity() { return buffers.instructionBufferExtraCapacity(); }
    public boolean isSharedSessionEnabled() { return Boolean.TRUE.equals(sharedSessionEnabled); }
    public boolean isGhAuthFallbackEnabled() { return Boolean.TRUE.equals(ghAuthFallbackEnabled); }
}
