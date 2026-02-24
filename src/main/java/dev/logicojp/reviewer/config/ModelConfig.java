package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.List;
import java.util.Locale;

/// Configuration for LLM models used in different stages of the review process.
///
/// `reasoningEffort` controls the effort level for reasoning models
/// (e.g. Claude Opus, o3). Valid values are `"low"`, `"medium"`, `"high"`.
///
/// `defaultModel` provides a single YAML-configurable fallback
/// (`reviewer.models.default-model`). When `reviewModel`, `reportModel`,
/// or `summaryModel` is not explicitly configured, `defaultModel` is used.
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

    /// Determines the appropriate reasoning effort for a model.
    /// Returns the configured effort if the model is a reasoning model, null otherwise.
    public static String resolveReasoningEffort(String model, String configuredEffort) {
        if (isReasoningModel(model)) {
            return configuredEffort != null ? configuredEffort : DEFAULT_REASONING_EFFORT;
        }
        return null;
    }

    /// Checks whether the given model requires explicit `reasoningEffort` configuration.
    public static boolean isReasoningModel(String model) {
        if (model == null) {
            return false;
        }
        String lower = model.toLowerCase(Locale.ROOT);
        return REASONING_CONTAINS_PATTERNS.stream().anyMatch(lower::contains)
            || REASONING_PREFIX_PATTERNS.stream().anyMatch(lower::startsWith);
    }

    @Override
    public String toString() {
        return "ModelConfig{review='%s', report='%s', summary='%s', reasoningEffort='%s', default='%s'}"
            .formatted(reviewModel, reportModel, summaryModel, reasoningEffort, defaultModel);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String reviewModel;
        private String reportModel;
        private String summaryModel;
        private String reasoningEffort = DEFAULT_REASONING_EFFORT;
        private String defaultModel;

        public Builder reviewModel(String model) { this.reviewModel = model; return this; }
        public Builder reportModel(String model) { this.reportModel = model; return this; }
        public Builder summaryModel(String model) { this.summaryModel = model; return this; }
        public Builder reasoningEffort(String effort) { this.reasoningEffort = effort; return this; }

        /// Sets all three model fields and the default to the same value.
        public Builder allModels(String model) {
            this.defaultModel = model;
            this.reviewModel = model;
            this.reportModel = model;
            this.summaryModel = model;
            return this;
        }

        public ModelConfig build() {
            return new ModelConfig(reviewModel, reportModel, summaryModel, reasoningEffort, defaultModel);
        }
    }
}
