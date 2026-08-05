package dev.logicojp.reviewer.infrastructure.config;

import io.micronaut.context.annotation.ConfigurationProperties;

/// Configuration for executive summary generation settings.
@ConfigurationProperties("reviewer.summary")
public record SummaryConfig(
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

    public SummaryConfig {
        maxContentPerAgent = ConfigDefaults.defaultIfNonPositive(maxContentPerAgent, DEFAULT_MAX_CONTENT_PER_AGENT);
        maxTotalPromptContent = ConfigDefaults.defaultIfNonPositive(maxTotalPromptContent, DEFAULT_MAX_TOTAL_PROMPT_CONTENT);
        fallbackExcerptLength = ConfigDefaults.defaultIfNonPositive(fallbackExcerptLength, DEFAULT_FALLBACK_EXCERPT_LENGTH);
        averageResultContentEstimate = ConfigDefaults.defaultIfNonPositive(averageResultContentEstimate, DEFAULT_AVERAGE_RESULT_CONTENT_ESTIMATE);
        initialBufferMargin = ConfigDefaults.defaultIfNonPositive(initialBufferMargin, DEFAULT_INITIAL_BUFFER_MARGIN);
        excerptNormalizationMultiplier = ConfigDefaults.defaultIfNonPositive(excerptNormalizationMultiplier, DEFAULT_EXCERPT_NORMALIZATION_MULTIPLIER);
    }
}
