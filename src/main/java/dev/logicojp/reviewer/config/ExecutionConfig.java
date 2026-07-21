package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;

import java.util.Objects;

/// Configuration for execution settings.
@ConfigurationProperties("reviewer.execution")
public record ExecutionConfig(
    @Nullable ConcurrencySettings concurrency,
    @Nullable TimeoutSettings timeouts,
    @Nullable RetrySettings retry,
    @Nullable BufferSettings buffers,
    @Bindable(defaultValue = "false") @Nullable Boolean ghAuthFallbackEnabled
) {

    @ConfigurationProperties("concurrency")
    public record ConcurrencySettings(@Bindable(defaultValue = "4") int parallelism) {
        public ConcurrencySettings {
            parallelism = ConfigDefaults.defaultIfNonPositive(parallelism, DEFAULT_PARALLELISM);
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
    public static final boolean DEFAULT_GH_AUTH_FALLBACK_ENABLED = false;
    private static final int DEFAULT_PARALLELISM = 4;
    private static final long DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES = 10;
    private static final long DEFAULT_AGENT_TIMEOUT_MINUTES = 5;
    private static final long DEFAULT_SKILL_TIMEOUT_MINUTES = 5;
    private static final long DEFAULT_SUMMARY_TIMEOUT_MINUTES = 5;
    private static final long DEFAULT_GH_AUTH_TIMEOUT_SECONDS = 10;
    public static final int DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY = 32;

    private static final ExecutionDefaults DEFAULTS = new ExecutionDefaults(
        new ConcurrencySettings(DEFAULT_PARALLELISM),
        new TimeoutSettings(
            DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES,
            DEFAULT_AGENT_TIMEOUT_MINUTES,
            DEFAULT_IDLE_TIMEOUT_MINUTES,
            DEFAULT_SKILL_TIMEOUT_MINUTES,
            DEFAULT_SUMMARY_TIMEOUT_MINUTES,
            DEFAULT_GH_AUTH_TIMEOUT_SECONDS
        ),
        new RetrySettings(DEFAULT_MAX_RETRIES),
        new BufferSettings(DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY),
        DEFAULT_GH_AUTH_FALLBACK_ENABLED
    );

    private record ExecutionDefaults(
        ConcurrencySettings concurrency,
        TimeoutSettings timeouts,
        RetrySettings retry,
        BufferSettings buffers,
        boolean ghAuthFallbackEnabled
    ) {
    }

    public ExecutionConfig {
        concurrency = Objects.requireNonNullElseGet(concurrency, ExecutionConfig::defaultConcurrency);
        timeouts = Objects.requireNonNullElseGet(timeouts, ExecutionConfig::defaultTimeouts);
        retry = Objects.requireNonNullElseGet(retry, ExecutionConfig::defaultRetry);
        buffers = Objects.requireNonNullElseGet(buffers, ExecutionConfig::defaultBuffers);
        ghAuthFallbackEnabled = ghAuthFallbackEnabled != null
            ? ghAuthFallbackEnabled
            : DEFAULT_GH_AUTH_FALLBACK_ENABLED;
    }

    private static ConcurrencySettings defaultConcurrency() {
        return DEFAULTS.concurrency();
    }

    private static TimeoutSettings defaultTimeouts() {
        return DEFAULTS.timeouts();
    }

    private static RetrySettings defaultRetry() {
        return DEFAULTS.retry();
    }

    private static BufferSettings defaultBuffers() {
        return DEFAULTS.buffers();
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
            DEFAULTS.ghAuthFallbackEnabled()
        );
    }

    public int parallelism() {
        return concurrency.parallelism();
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

    public boolean isGhAuthFallbackEnabled() {
        return Boolean.TRUE.equals(ghAuthFallbackEnabled);
    }

    public ExecutionConfig withParallelism(int newParallelism) {
        return Builder.from(this)
            .parallelism(newParallelism)
            .build();
    }

    public ExecutionConfig withGhAuthFallbackEnabled(boolean enabled) {
        return Builder.from(this)
            .ghAuthFallbackEnabled(enabled)
            .build();
    }

    public static ExecutionConfig defaults() {
        return new ExecutionConfig(
            DEFAULTS.concurrency(),
            DEFAULTS.timeouts(),
            DEFAULTS.retry(),
            DEFAULTS.buffers(),
            DEFAULTS.ghAuthFallbackEnabled()
        );
    }

    public static Builder builder() {
        return Builder.from(defaults());
    }

    public static final class Builder {
        private int parallelism;
        private long orchestratorTimeoutMinutes;
        private long agentTimeoutMinutes;
        private long idleTimeoutMinutes;
        private long skillTimeoutMinutes;
        private long summaryTimeoutMinutes;
        private long ghAuthTimeoutSeconds;
        private int maxRetries;
        private int instructionBufferExtraCapacity;
        private boolean ghAuthFallbackEnabled;

        public static Builder from(ExecutionConfig source) {
            var builder = new Builder();
            builder.parallelism = source.parallelism();
            builder.orchestratorTimeoutMinutes = source.orchestratorTimeoutMinutes();
            builder.agentTimeoutMinutes = source.agentTimeoutMinutes();
            builder.idleTimeoutMinutes = source.idleTimeoutMinutes();
            builder.skillTimeoutMinutes = source.skillTimeoutMinutes();
            builder.summaryTimeoutMinutes = source.summaryTimeoutMinutes();
            builder.ghAuthTimeoutSeconds = source.ghAuthTimeoutSeconds();
            builder.maxRetries = source.maxRetries();
            builder.instructionBufferExtraCapacity = source.instructionBufferExtraCapacity();
            builder.ghAuthFallbackEnabled = source.isGhAuthFallbackEnabled();
            return builder;
        }

        public Builder parallelism(int parallelism) {
            this.parallelism = parallelism;
            return this;
        }

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

        public Builder ghAuthFallbackEnabled(boolean ghAuthFallbackEnabled) {
            this.ghAuthFallbackEnabled = ghAuthFallbackEnabled;
            return this;
        }

        public ExecutionConfig build() {
            return new ExecutionConfig(
                new ConcurrencySettings(parallelism),
                new TimeoutSettings(
                    orchestratorTimeoutMinutes,
                    agentTimeoutMinutes,
                    idleTimeoutMinutes,
                    skillTimeoutMinutes,
                    summaryTimeoutMinutes,
                    ghAuthTimeoutSeconds
                ),
                new RetrySettings(maxRetries),
                new BufferSettings(instructionBufferExtraCapacity),
                ghAuthFallbackEnabled
            );
        }
    }
}
