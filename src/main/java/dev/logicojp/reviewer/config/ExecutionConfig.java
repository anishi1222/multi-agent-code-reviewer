package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;

/// Configuration for execution settings (parallelism, timeouts, retries, buffers)
/// and executive summary generation settings.
///
/// Summary settings are nested under `reviewer.execution.summary` in YAML.
@ConfigurationProperties("reviewer.execution")
public record ExecutionConfig(
    int parallelism,
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
    int instructionBufferExtraCapacity,
    String checkpointDirectory,
    SummarySettings summary
) {

    public static final int DEFAULT_PARALLELISM = 4;
    public static final int DEFAULT_REVIEW_PASSES = 1;
    public static final long DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES = 10;
    public static final long DEFAULT_AGENT_TIMEOUT_MINUTES = 5;
    public static final long DEFAULT_IDLE_TIMEOUT_MINUTES = 5;
    public static final long DEFAULT_SKILL_TIMEOUT_MINUTES = 5;
    public static final long DEFAULT_SUMMARY_TIMEOUT_MINUTES = 5;
    public static final long DEFAULT_GH_AUTH_TIMEOUT_SECONDS = 10;
    public static final int DEFAULT_MAX_RETRIES = 2;
    public static final int DEFAULT_MAX_ACCUMULATED_SIZE = 4 * 1024 * 1024;
    public static final int DEFAULT_INITIAL_ACCUMULATED_CAPACITY = 4096;
    public static final int DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY = 32;
    public static final String DEFAULT_CHECKPOINT_DIRECTORY = "reports/.checkpoints";

    public ExecutionConfig {
        parallelism = ConfigDefaults.defaultIfNonPositive(parallelism, DEFAULT_PARALLELISM);
        reviewPasses = ConfigDefaults.defaultIfNonPositive(reviewPasses, DEFAULT_REVIEW_PASSES);
        orchestratorTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(orchestratorTimeoutMinutes, DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES);
        agentTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(agentTimeoutMinutes, DEFAULT_AGENT_TIMEOUT_MINUTES);
        idleTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(idleTimeoutMinutes, DEFAULT_IDLE_TIMEOUT_MINUTES);
        skillTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(skillTimeoutMinutes, DEFAULT_SKILL_TIMEOUT_MINUTES);
        summaryTimeoutMinutes = ConfigDefaults.defaultIfNonPositive(summaryTimeoutMinutes, DEFAULT_SUMMARY_TIMEOUT_MINUTES);
        ghAuthTimeoutSeconds = ConfigDefaults.defaultIfNonPositive(ghAuthTimeoutSeconds, DEFAULT_GH_AUTH_TIMEOUT_SECONDS);
        maxRetries = ConfigDefaults.defaultIfNegative(maxRetries, DEFAULT_MAX_RETRIES);
        maxAccumulatedSize = ConfigDefaults.defaultIfNonPositive(maxAccumulatedSize, DEFAULT_MAX_ACCUMULATED_SIZE);
        initialAccumulatedCapacity = ConfigDefaults.defaultIfNonPositive(initialAccumulatedCapacity, DEFAULT_INITIAL_ACCUMULATED_CAPACITY);
        instructionBufferExtraCapacity = ConfigDefaults.defaultIfNonPositive(instructionBufferExtraCapacity, DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY);
        checkpointDirectory = ConfigDefaults.defaultIfBlank(checkpointDirectory, DEFAULT_CHECKPOINT_DIRECTORY);
        summary = summary != null ? summary : new SummarySettings(0, 0, 0, 0, 0, 0);
    }

    /// Returns a copy with the parallelism value replaced.
    public ExecutionConfig withParallelism(int newParallelism) {
        return new ExecutionConfig(
            newParallelism, reviewPasses, orchestratorTimeoutMinutes,
            agentTimeoutMinutes, idleTimeoutMinutes, skillTimeoutMinutes,
            summaryTimeoutMinutes, ghAuthTimeoutSeconds, maxRetries,
            maxAccumulatedSize, initialAccumulatedCapacity, instructionBufferExtraCapacity,
            checkpointDirectory,
            summary
        );
    }

    /// Executive summary generation settings.
    /// Bound to `reviewer.execution.summary` in YAML.
    @ConfigurationProperties("summary")
    public record SummarySettings(
        int maxContentPerAgent,
        int maxTotalPromptContent,
        int fallbackExcerptLength,
        int averageResultContentEstimate,
        int initialBufferMargin,
        int excerptNormalizationMultiplier
    ) {

        public static final int DEFAULT_MAX_CONTENT_PER_AGENT = 50_000;
        public static final int DEFAULT_MAX_TOTAL_PROMPT_CONTENT = 200_000;
        public static final int DEFAULT_FALLBACK_EXCERPT_LENGTH = 180;
        public static final int DEFAULT_AVERAGE_RESULT_CONTENT_ESTIMATE = 8192;
        public static final int DEFAULT_INITIAL_BUFFER_MARGIN = 4096;
        public static final int DEFAULT_EXCERPT_NORMALIZATION_MULTIPLIER = 3;

        public SummarySettings {
            maxContentPerAgent = ConfigDefaults.defaultIfNonPositive(maxContentPerAgent, DEFAULT_MAX_CONTENT_PER_AGENT);
            maxTotalPromptContent = ConfigDefaults.defaultIfNonPositive(maxTotalPromptContent, DEFAULT_MAX_TOTAL_PROMPT_CONTENT);
            fallbackExcerptLength = ConfigDefaults.defaultIfNonPositive(fallbackExcerptLength, DEFAULT_FALLBACK_EXCERPT_LENGTH);
            averageResultContentEstimate = ConfigDefaults.defaultIfNonPositive(averageResultContentEstimate, DEFAULT_AVERAGE_RESULT_CONTENT_ESTIMATE);
            initialBufferMargin = ConfigDefaults.defaultIfNonPositive(initialBufferMargin, DEFAULT_INITIAL_BUFFER_MARGIN);
            excerptNormalizationMultiplier = ConfigDefaults.defaultIfNonPositive(excerptNormalizationMultiplier, DEFAULT_EXCERPT_NORMALIZATION_MULTIPLIER);
        }
    }
}
