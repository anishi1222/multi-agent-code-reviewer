package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;

/// Configuration for execution settings (parallelism, timeouts).
@ConfigurationProperties("reviewer.execution")
public record ExecutionConfig(
    ConcurrencySettings concurrency,
    TimeoutSettings timeouts,
    RetrySettings retry,
    BufferSettings buffers
) {

    public record ConcurrencySettings(int parallelism, int reviewPasses) {
    }

    public record TimeoutSettings(long orchestratorTimeoutMinutes,
                                  long agentTimeoutMinutes,
                                  long idleTimeoutMinutes,
                                  long skillTimeoutMinutes,
                                  long summaryTimeoutMinutes,
                                  long ghAuthTimeoutSeconds) {
    }

    public record RetrySettings(int maxRetries) {
    }

    public record BufferSettings(int maxAccumulatedSize,
                                 int initialAccumulatedCapacity,
                                 int instructionBufferExtraCapacity) {
    }

    public static final int DEFAULT_MAX_RETRIES = 2;
    public static final long DEFAULT_IDLE_TIMEOUT_MINUTES = 5;
    public static final int DEFAULT_REVIEW_PASSES = 1;
    private static final int DEFAULT_PARALLELISM = 4;
    private static final long DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES = 10;
    private static final long DEFAULT_AGENT_TIMEOUT_MINUTES = 5;
    private static final long DEFAULT_SKILL_TIMEOUT_MINUTES = 5;
    private static final long DEFAULT_SUMMARY_TIMEOUT_MINUTES = 5;
    private static final long DEFAULT_GH_AUTH_TIMEOUT_SECONDS = 10;
    public static final int DEFAULT_MAX_ACCUMULATED_SIZE = 4 * 1024 * 1024;
    public static final int DEFAULT_INITIAL_ACCUMULATED_CAPACITY = 4096;
    public static final int DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY = 32;

    public ExecutionConfig {
        concurrency = concurrency != null
            ? new ConcurrencySettings(
                ConfigDefaults.defaultIfNonPositive(concurrency.parallelism(), DEFAULT_PARALLELISM),
                ConfigDefaults.defaultIfNonPositive(concurrency.reviewPasses(), DEFAULT_REVIEW_PASSES)
            )
            : new ConcurrencySettings(DEFAULT_PARALLELISM, DEFAULT_REVIEW_PASSES);

        timeouts = timeouts != null
            ? new TimeoutSettings(
                ConfigDefaults.defaultIfNonPositive(timeouts.orchestratorTimeoutMinutes(), DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES),
                ConfigDefaults.defaultIfNonPositive(timeouts.agentTimeoutMinutes(), DEFAULT_AGENT_TIMEOUT_MINUTES),
                ConfigDefaults.defaultIfNonPositive(timeouts.idleTimeoutMinutes(), DEFAULT_IDLE_TIMEOUT_MINUTES),
                ConfigDefaults.defaultIfNonPositive(timeouts.skillTimeoutMinutes(), DEFAULT_SKILL_TIMEOUT_MINUTES),
                ConfigDefaults.defaultIfNonPositive(timeouts.summaryTimeoutMinutes(), DEFAULT_SUMMARY_TIMEOUT_MINUTES),
                ConfigDefaults.defaultIfNonPositive(timeouts.ghAuthTimeoutSeconds(), DEFAULT_GH_AUTH_TIMEOUT_SECONDS)
            )
            : new TimeoutSettings(
                DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES,
                DEFAULT_AGENT_TIMEOUT_MINUTES,
                DEFAULT_IDLE_TIMEOUT_MINUTES,
                DEFAULT_SKILL_TIMEOUT_MINUTES,
                DEFAULT_SUMMARY_TIMEOUT_MINUTES,
                DEFAULT_GH_AUTH_TIMEOUT_SECONDS
            );

        retry = retry != null
            ? new RetrySettings(ConfigDefaults.defaultIfNegative(retry.maxRetries(), DEFAULT_MAX_RETRIES))
            : new RetrySettings(DEFAULT_MAX_RETRIES);

        buffers = buffers != null
            ? new BufferSettings(
                ConfigDefaults.defaultIfNonPositive(buffers.maxAccumulatedSize(), DEFAULT_MAX_ACCUMULATED_SIZE),
                ConfigDefaults.defaultIfNonPositive(buffers.initialAccumulatedCapacity(), DEFAULT_INITIAL_ACCUMULATED_CAPACITY),
                ConfigDefaults.defaultIfNonPositive(
                    buffers.instructionBufferExtraCapacity(),
                    DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY
                )
            )
            : new BufferSettings(
                DEFAULT_MAX_ACCUMULATED_SIZE,
                DEFAULT_INITIAL_ACCUMULATED_CAPACITY,
                DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY
            );
    }

    public static ExecutionConfig ofFlat(int parallelism,
                                         int reviewPasses,
                                         long orchestratorTimeoutMinutes,
                                         long agentTimeoutMinutes,
                                         long idleTimeoutMinutes,
                                         long skillTimeoutMinutes,
                                         long summaryTimeoutMinutes,
                                         long ghAuthTimeoutSeconds,
                                         int maxRetries,
                                         int maxAccumulatedSize,
                                         int initialAccumulatedCapacity,
                                         int instructionBufferExtraCapacity) {
        return new ExecutionConfig(
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
                maxAccumulatedSize,
                initialAccumulatedCapacity,
                instructionBufferExtraCapacity
            )
        );
    }

    public static ExecutionConfig of(ConcurrencySettings concurrency,
                                     TimeoutSettings timeouts,
                                     RetrySettings retry,
                                     BufferSettings buffers) {
        return new ExecutionConfig(concurrency, timeouts, retry, buffers);
    }

    public ConcurrencySettings concurrencySettings() {
        return concurrency;
    }

    public TimeoutSettings timeoutSettings() {
        return timeouts;
    }

    public RetrySettings retrySettings() {
        return retry;
    }

    public BufferSettings bufferSettings() {
        return buffers;
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

    public int maxAccumulatedSize() {
        return buffers.maxAccumulatedSize();
    }

    public int initialAccumulatedCapacity() {
        return buffers.initialAccumulatedCapacity();
    }

    public int instructionBufferExtraCapacity() {
        return buffers.instructionBufferExtraCapacity();
    }

    /// Returns a copy of this config with the parallelism value replaced.
    /// @param newParallelism the new parallelism value
    /// @return a new ExecutionConfig with the updated parallelism
    public ExecutionConfig withParallelism(int newParallelism) {
        return Builder.from(this)
            .parallelism(newParallelism)
            .build();
    }

    /// Returns a new ExecutionConfig with all default values.
    /// Useful in tests and as a starting point for the Builder.
    public static ExecutionConfig defaults() {
        return ExecutionConfig.of(
            new ConcurrencySettings(DEFAULT_PARALLELISM, DEFAULT_REVIEW_PASSES),
            new TimeoutSettings(
                DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES,
                DEFAULT_AGENT_TIMEOUT_MINUTES,
                DEFAULT_IDLE_TIMEOUT_MINUTES,
                DEFAULT_SKILL_TIMEOUT_MINUTES,
                DEFAULT_SUMMARY_TIMEOUT_MINUTES,
                DEFAULT_GH_AUTH_TIMEOUT_SECONDS
            ),
            new RetrySettings(DEFAULT_MAX_RETRIES),
            new BufferSettings(
                DEFAULT_MAX_ACCUMULATED_SIZE,
                DEFAULT_INITIAL_ACCUMULATED_CAPACITY,
                DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY
            )
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
        private int maxAccumulatedSize;
        private int initialAccumulatedCapacity;
        private int instructionBufferExtraCapacity;

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
            b.maxAccumulatedSize = source.maxAccumulatedSize();
            b.initialAccumulatedCapacity = source.initialAccumulatedCapacity();
            b.instructionBufferExtraCapacity = source.instructionBufferExtraCapacity();
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

        public Builder maxAccumulatedSize(int maxAccumulatedSize) {
            this.maxAccumulatedSize = maxAccumulatedSize;
            return this;
        }

        public Builder initialAccumulatedCapacity(int initialAccumulatedCapacity) {
            this.initialAccumulatedCapacity = initialAccumulatedCapacity;
            return this;
        }

        public Builder instructionBufferExtraCapacity(int instructionBufferExtraCapacity) {
            this.instructionBufferExtraCapacity = instructionBufferExtraCapacity;
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
                    maxAccumulatedSize,
                    initialAccumulatedCapacity,
                    instructionBufferExtraCapacity
                )
            );
        }
    }
}
