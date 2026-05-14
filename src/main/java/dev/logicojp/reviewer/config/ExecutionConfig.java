package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.annotation.Nullable;
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
            orchestratorTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(
                orchestratorTimeoutMinutes,
                DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES
            );
            agentTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(agentTimeoutMinutes, DEFAULT_AGENT_TIMEOUT_MINUTES);
            idleTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(idleTimeoutMinutes, DEFAULT_IDLE_TIMEOUT_MINUTES);
            skillTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(skillTimeoutMinutes, DEFAULT_SKILL_TIMEOUT_MINUTES);
            summaryTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(
                summaryTimeoutMinutes,
                DEFAULT_SUMMARY_TIMEOUT_MINUTES
            );
            ghAuthTimeoutSeconds = ConfigDefaults.defaultIfNonPositive(
                ghAuthTimeoutSeconds,
                DEFAULT_GH_AUTH_TIMEOUT_SECONDS
            );
        }
    }

    @ConfigurationProperties("retry")
    public record RetrySettings(@Bindable(defaultValue = "2") int maxRetries) {
        public RetrySettings {
            maxRetries = ConfigDefaults.defaultIfNegative(maxRetries, DEFAULT_MAX_RETRIES);
        }
    }

    @ConfigurationProperties("buffers")
    public record BufferSettings(
        @Bindable(defaultValue = "32") int instructionBufferExtraCapacity
    ) {
        public BufferSettings {
            instructionBufferExtraCapacity = ConfigDefaults.defaultIfNonPositive(
                instructionBufferExtraCapacity,
                DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY
            );
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
        concurrency = Objects.requireNonNullElseGet(concurrency, ExecutionConfig::defaultConcurrency);
        timeouts = Objects.requireNonNullElseGet(timeouts, ExecutionConfig::defaultTimeouts);
        retry = Objects.requireNonNullElseGet(retry, ExecutionConfig::defaultRetry);
        buffers = Objects.requireNonNullElseGet(buffers, ExecutionConfig::defaultBuffers);

        sharedSessionEnabled = sharedSessionEnabled != null
            ? sharedSessionEnabled
            : DEFAULT_SHARED_SESSION_ENABLED;
        ghAuthFallbackEnabled = ghAuthFallbackEnabled != null
            ? ghAuthFallbackEnabled
            : DEFAULT_GH_AUTH_FALLBACK_ENABLED;
    }

    private static ConcurrencySettings defaultConcurrency() {
        return new ConcurrencySettings(DEFAULT_PARALLELISM, DEFAULT_REVIEW_PASSES);
    }

    private static TimeoutSettings defaultTimeouts() {
        return new TimeoutSettings(
            DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES,
            DEFAULT_AGENT_TIMEOUT_MINUTES,
            DEFAULT_IDLE_TIMEOUT_MINUTES,
            DEFAULT_SKILL_TIMEOUT_MINUTES,
            DEFAULT_SUMMARY_TIMEOUT_MINUTES,
            DEFAULT_GH_AUTH_TIMEOUT_SECONDS
        );
    }

    private static RetrySettings defaultRetry() {
        return new RetrySettings(DEFAULT_MAX_RETRIES);
    }

    private static BufferSettings defaultBuffers() {
        return new BufferSettings(
            DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY
        );
    }

    public static ExecutionConfig of(ConcurrencySettings concurrency,
                                     TimeoutSettings timeouts,
                                     RetrySettings retry,
                                     BufferSettings buffers) {
        return new ExecutionConfig(
            concurrency,
            timeouts,
            retry,
            buffers,
            DEFAULT_SHARED_SESSION_ENABLED,
            DEFAULT_GH_AUTH_FALLBACK_ENABLED
        );
    }

    public static ExecutionConfig of(ConcurrencySettings concurrency,
                                     TimeoutSettings timeouts,
                                     RetrySettings retry,
                                     BufferSettings buffers,
                                     boolean sharedSessionEnabled) {
        return new ExecutionConfig(
            concurrency,
            timeouts,
            retry,
            buffers,
            sharedSessionEnabled,
            DEFAULT_GH_AUTH_FALLBACK_ENABLED
        );
    }

    public static ExecutionConfig of(ConcurrencySettings concurrency,
                                     TimeoutSettings timeouts,
                                     RetrySettings retry,
                                     BufferSettings buffers,
                                     boolean sharedSessionEnabled,
                                     boolean ghAuthFallbackEnabled) {
        return new ExecutionConfig(concurrency, timeouts, retry, buffers, sharedSessionEnabled, ghAuthFallbackEnabled);
    }

    public int parallelism() {
        return concurrency.parallelism();
    }

    public int reviewPasses() {
        return concurrency.reviewPasses();
    }

    public long orchestratorTimeoutMinutes() {
        return timeouts.orchestratorTimeoutMinutes();
    }

    public long agentTimeoutMinutes() {
        return timeouts.agentTimeoutMinutes();
    }

    public long idleTimeoutMinutes() {
        return timeouts.idleTimeoutMinutes();
    }

    public long skillTimeoutMinutes() {
        return timeouts.skillTimeoutMinutes();
    }

    public long summaryTimeoutMinutes() {
        return timeouts.summaryTimeoutMinutes();
    }

    public long ghAuthTimeoutSeconds() {
        return timeouts.ghAuthTimeoutSeconds();
    }

    public int maxRetries() {
        return retry.maxRetries();
    }

    public int instructionBufferExtraCapacity() {
        return buffers.instructionBufferExtraCapacity();
    }

    public boolean isSharedSessionEnabled() {
        return Boolean.TRUE.equals(sharedSessionEnabled);
    }

    public boolean isGhAuthFallbackEnabled() {
        return Boolean.TRUE.equals(ghAuthFallbackEnabled);
    }

    /// Returns a copy of this config with the parallelism value replaced.
    /// @param newParallelism the new parallelism value
    /// @return a new ExecutionConfig with the updated parallelism
    public ExecutionConfig withParallelism(int newParallelism) {
        return Builder.from(this)
            .parallelism(newParallelism)
            .build();
    }

    public ExecutionConfig withSharedSessionEnabled(boolean enabled) {
        return Builder.from(this)
            .sharedSessionEnabled(enabled)
            .build();
    }

    public ExecutionConfig withGhAuthFallbackEnabled(boolean enabled) {
        return Builder.from(this)
            .ghAuthFallbackEnabled(enabled)
            .build();
    }

    /// Returns a new ExecutionConfig with all default values.
    /// Useful in tests and as a starting point for the Builder.
    public static ExecutionConfig defaults() {
        return ExecutionConfig.of(
            defaultConcurrency(),
            defaultTimeouts(),
                defaultRetry(),
                defaultBuffers(),
                DEFAULT_SHARED_SESSION_ENABLED,
                DEFAULT_GH_AUTH_FALLBACK_ENABLED
        );
    }

    public static final class Builder {
        private int parallelism;
        private int reviewPasses;
        private long orchestratorTimeoutMinutes;
        private long agentTimeoutMinutes;
        private long idleTimeoutMinutes;
        private long skillTimeoutMinutes;
        private long summaryTimeoutMinutes;
        private long ghAuthTimeoutSeconds;
        private int maxRetries;
        private int instructionBufferExtraCapacity;
        private boolean sharedSessionEnabled;
        private boolean ghAuthFallbackEnabled;

        public static Builder from(ExecutionConfig source) {
            var b = new Builder();
            b.parallelism = source.parallelism();
            b.reviewPasses = source.reviewPasses();
            b.orchestratorTimeoutMinutes = source.orchestratorTimeoutMinutes();
            b.agentTimeoutMinutes = source.agentTimeoutMinutes();
            b.idleTimeoutMinutes = source.idleTimeoutMinutes();
            b.skillTimeoutMinutes = source.skillTimeoutMinutes();
            b.summaryTimeoutMinutes = source.summaryTimeoutMinutes();
            b.ghAuthTimeoutSeconds = source.ghAuthTimeoutSeconds();
            b.maxRetries = source.maxRetries();
            b.instructionBufferExtraCapacity = source.instructionBufferExtraCapacity();
            b.sharedSessionEnabled = source.isSharedSessionEnabled();
            b.ghAuthFallbackEnabled = source.isGhAuthFallbackEnabled();
            return b;
        }

        public Builder parallelism(int parallelism) { this.parallelism = parallelism; return this; }

        public Builder reviewPasses(int reviewPasses) { this.reviewPasses = reviewPasses; return this; }

        public Builder orchestratorTimeoutMinutes(long orchestratorTimeoutMinutes) {
            this.orchestratorTimeoutMinutes = orchestratorTimeoutMinutes;
            return this;
        }

        public Builder agentTimeoutMinutes(long agentTimeoutMinutes) {
            this.agentTimeoutMinutes = agentTimeoutMinutes;
            return this;
        }

        public Builder idleTimeoutMinutes(long idleTimeoutMinutes) {
            this.idleTimeoutMinutes = idleTimeoutMinutes;
            return this;
        }

        public Builder skillTimeoutMinutes(long skillTimeoutMinutes) {
            this.skillTimeoutMinutes = skillTimeoutMinutes;
            return this;
        }

        public Builder summaryTimeoutMinutes(long summaryTimeoutMinutes) {
            this.summaryTimeoutMinutes = summaryTimeoutMinutes;
            return this;
        }

        public Builder ghAuthTimeoutSeconds(long ghAuthTimeoutSeconds) {
            this.ghAuthTimeoutSeconds = ghAuthTimeoutSeconds;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder instructionBufferExtraCapacity(int instructionBufferExtraCapacity) {
            this.instructionBufferExtraCapacity = instructionBufferExtraCapacity;
            return this;
        }

        public Builder sharedSessionEnabled(boolean sharedSessionEnabled) {
            this.sharedSessionEnabled = sharedSessionEnabled;
            return this;
        }

        public Builder ghAuthFallbackEnabled(boolean ghAuthFallbackEnabled) {
            this.ghAuthFallbackEnabled = ghAuthFallbackEnabled;
            return this;
        }

        public ExecutionConfig build() {
            return ExecutionConfig.of(
                new ConcurrencySettings(parallelism, reviewPasses),
                new TimeoutSettings(
                    orchestratorTimeoutMinutes,
                    agentTimeoutMinutes,
                    idleTimeoutMinutes,
                    skillTimeoutMinutes,
                    summaryTimeoutMinutes,
                    ghAuthTimeoutSeconds
                ),
                new RetrySettings(maxRetries),
                new BufferSettings(
                    instructionBufferExtraCapacity
                ),
                sharedSessionEnabled,
                ghAuthFallbackEnabled
            );
        }
    }
}
