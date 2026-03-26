package dev.logicojp.reviewer.testutil;

import dev.logicojp.reviewer.config.ExecutionConfig;

public final class ExecutionConfigFixtures {

    private ExecutionConfigFixtures() {
    }

    public static ExecutionConfig config(int parallelism,
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
            .maxAccumulatedSize(maxAccumulatedSize)
            .initialAccumulatedCapacity(initialAccumulatedCapacity)
            .instructionBufferExtraCapacity(instructionBufferExtraCapacity)
            .build();
    }
}
