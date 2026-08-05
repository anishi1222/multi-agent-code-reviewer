package dev.logicojp.reviewer.testutil;

import dev.logicojp.reviewer.infrastructure.config.ExecutionConfig;

public final class ExecutionConfigFixtures {

    private ExecutionConfigFixtures() {
    }

    /// Convenience overload retained for backward compatibility with the test suite.
    /// {@code maxAccumulatedSize} and {@code initialAccumulatedCapacity} are accepted
    /// but ignored after Phase 3c removed the legacy event accumulator.
    public static ExecutionConfig config(int parallelism,
                                         int reviewPasses,
                                         long orchestratorTimeoutMinutes,
                                         long agentTimeoutMinutes,
                                         long idleTimeoutMinutes,
                                         long skillTimeoutMinutes,
                                         long summaryTimeoutMinutes,
                                         long ghAuthTimeoutSeconds,
                                         int maxRetries,
                                         @SuppressWarnings("unused") int maxAccumulatedSize,
                                         @SuppressWarnings("unused") int initialAccumulatedCapacity,
                                         int instructionBufferExtraCapacity) {
        return new ExecutionConfig(
            new ExecutionConfig.ConcurrencySettings(parallelism, reviewPasses),
            new ExecutionConfig.TimeoutSettings(orchestratorTimeoutMinutes, agentTimeoutMinutes,
                idleTimeoutMinutes, skillTimeoutMinutes, summaryTimeoutMinutes, ghAuthTimeoutSeconds),
            new ExecutionConfig.RetrySettings(maxRetries),
            new ExecutionConfig.BufferSettings(instructionBufferExtraCapacity),
            null,
            false);
    }
}
