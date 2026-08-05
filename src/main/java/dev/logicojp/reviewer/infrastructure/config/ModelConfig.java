package dev.logicojp.reviewer.infrastructure.config;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.List;

/// Configuration for LLM models used in different stages of the review process.
@ConfigurationProperties("reviewer.models")
public record ModelConfig(
    String reviewModel,
    String reportModel,
    String summaryModel,
    String reasoningEffort,
    String defaultModel
) {

    public static final String DEFAULT_MODEL = "claude-sonnet-4.5";
    public static final String DEFAULT_REASONING_EFFORT = "high";
    private static final List<String> REASONING_CONTAINS_PATTERNS = List.of("opus");
    private static final List<String> REASONING_PREFIX_PATTERNS = List.of("o3", "o4-mini");

    public ModelConfig {
        defaultModel = ConfigDefaults.defaultIfBlank(defaultModel, DEFAULT_MODEL);
        reviewModel = ConfigDefaults.defaultIfBlank(reviewModel, defaultModel);
        reportModel = ConfigDefaults.defaultIfBlank(reportModel, defaultModel);
        summaryModel = ConfigDefaults.defaultIfBlank(summaryModel, defaultModel);
        reasoningEffort = ConfigDefaults.defaultIfBlank(reasoningEffort, DEFAULT_REASONING_EFFORT);
    }

    public ModelConfig() {
        this(null, null, null, DEFAULT_REASONING_EFFORT, DEFAULT_MODEL);
    }

    public static String resolveReasoningEffort(String model, String configuredEffort) {
        if (isReasoningModel(model)) {
            return configuredEffort != null ? configuredEffort : DEFAULT_REASONING_EFFORT;
        }
        return null;
    }

    public static boolean isReasoningModel(String model) {
        if (model == null) return false;
        String lower = model.toLowerCase();
        return REASONING_CONTAINS_PATTERNS.stream().anyMatch(lower::contains)
            || REASONING_PREFIX_PATTERNS.stream().anyMatch(lower::startsWith);
    }
}
