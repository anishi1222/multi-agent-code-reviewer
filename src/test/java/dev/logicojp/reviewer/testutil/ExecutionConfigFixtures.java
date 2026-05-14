package dev.logicojp.reviewer.testutil;

import dev.logicojp.reviewer.config.ExecutionConfig;

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
        return ExecutionConfig.Builder.from(ExecutionConfig.defaults())
            .parallelism(parallelism)
            .reviewPasses(reviewPasses)
            .orchestratorTimeoutMinutes(orchestratorTimeoutMinutes)
            .agentTimeoutMinutes(agentTimeoutMinutes)
            .idleTimeoutMinutes(idleTimeoutMinutes)
            .skillTimeoutMinutes(skillTimeoutMinutes)
            .summaryTimeoutMinutes(summaryTimeoutMinutes)
            .ghAuthTimeoutSeconds(ghAuthTimeoutSeconds)
            .ghAuthFallbackEnabled(false)
            .maxRetries(maxRetries)
            .instructionBufferExtraCapacity(instructionBufferExtraCapacity)
            .build();
    }
}
