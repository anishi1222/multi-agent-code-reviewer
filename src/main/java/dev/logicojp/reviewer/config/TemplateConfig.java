package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

/// Configuration for template paths.
/// Uses Micronaut's nested `@ConfigurationProperties` for logical grouping.
@ConfigurationProperties("reviewer.templates")
public record TemplateConfig(
    @Nullable String directory,
    @Nullable String defaultOutputFormat,
    @Nullable String report,
    @Nullable String localReviewContent,
    @Nullable String outputConstraints,
    @Nullable String reviewQualityConstraints,
    @Nullable String reportLinkEntry,
    @Nullable SummaryTemplates summary,
    @Nullable FallbackTemplates fallback
) {

    private static final String DEFAULT_DIRECTORY = "templates";
    private static final String DEFAULT_OUTPUT_FORMAT = "default-output-format.md";
    private static final String DEFAULT_REPORT = "report.md";
    private static final String DEFAULT_LOCAL_REVIEW_CONTENT = "local-review-content.md";
    private static final String DEFAULT_OUTPUT_CONSTRAINTS = "output-constraints.md";
    private static final String DEFAULT_REVIEW_QUALITY_CONSTRAINTS = "review-quality-constraints.md";
    private static final String DEFAULT_REPORT_LINK_ENTRY = "report-link-entry.md";

    public TemplateConfig {
        TopLevelValues normalized = normalizeTopLevelValues(
            directory,
            defaultOutputFormat,
            report,
            localReviewContent,
            outputConstraints,
            reviewQualityConstraints,
            reportLinkEntry
        );
        directory = normalized.directory();
        defaultOutputFormat = normalized.defaultOutputFormat();
        report = normalized.report();
        localReviewContent = normalized.localReviewContent();
        outputConstraints = normalized.outputConstraints();
        reviewQualityConstraints = normalized.reviewQualityConstraints();
        reportLinkEntry = normalized.reportLinkEntry();
        summary = summary != null ? summary : new SummaryTemplates(null, null, null, null, null);
        fallback = fallback != null ? fallback : new FallbackTemplates(null, null, null, null);
    }

    private static TopLevelValues normalizeTopLevelValues(String directory,
                                                          String defaultOutputFormat,
                                                          String report,
                                                          String localReviewContent,
                                                          String outputConstraints,
                                                          String reviewQualityConstraints,
                                                          String reportLinkEntry) {
        return new TopLevelValues(
            ConfigDefaults.defaultIfBlank(directory, DEFAULT_DIRECTORY),
            ConfigDefaults.defaultIfBlank(defaultOutputFormat, DEFAULT_OUTPUT_FORMAT),
            ConfigDefaults.defaultIfBlank(report, DEFAULT_REPORT),
            ConfigDefaults.defaultIfBlank(localReviewContent, DEFAULT_LOCAL_REVIEW_CONTENT),
            ConfigDefaults.defaultIfBlank(outputConstraints, DEFAULT_OUTPUT_CONSTRAINTS),
            ConfigDefaults.defaultIfBlank(reviewQualityConstraints, DEFAULT_REVIEW_QUALITY_CONSTRAINTS),
            ConfigDefaults.defaultIfBlank(reportLinkEntry, DEFAULT_REPORT_LINK_ENTRY)
        );
    }

    private record TopLevelValues(String directory,
                                  String defaultOutputFormat,
                                  String report,
                                  String localReviewContent,
                                  String outputConstraints,
                                  String reviewQualityConstraints,
                                  String reportLinkEntry) {
    }

    /// Summary-related template configuration.
    @ConfigurationProperties("summary")
    public record SummaryTemplates(
        @Nullable String systemPrompt,
        @Nullable String userPrompt,
        @Nullable String executiveSummary,
        @Nullable String resultEntry,
        @Nullable String resultErrorEntry
    ) {
        private static final String DEFAULT_SYSTEM = "summary-system.md";
        private static final String DEFAULT_USER = "summary-prompt.md";
        private static final String DEFAULT_EXECUTIVE_SUMMARY = "executive-summary.md";
        private static final String DEFAULT_RESULT_ENTRY = "summary-result-entry.md";
        private static final String DEFAULT_RESULT_ERROR_ENTRY = "summary-result-error-entry.md";

        public SummaryTemplates {
            systemPrompt = ConfigDefaults.defaultIfBlank(systemPrompt, DEFAULT_SYSTEM);
            userPrompt = ConfigDefaults.defaultIfBlank(userPrompt, DEFAULT_USER);
            executiveSummary = ConfigDefaults.defaultIfBlank(executiveSummary, DEFAULT_EXECUTIVE_SUMMARY);
            resultEntry = ConfigDefaults.defaultIfBlank(resultEntry, DEFAULT_RESULT_ENTRY);
            resultErrorEntry = ConfigDefaults.defaultIfBlank(resultErrorEntry, DEFAULT_RESULT_ERROR_ENTRY);
        }
    }

    /// Fallback template configuration (used when AI summary generation fails).
    @ConfigurationProperties("fallback")
    public record FallbackTemplates(
        @Nullable String summary,
        @Nullable String agentRow,
        @Nullable String agentSuccess,
        @Nullable String agentFailure
    ) {
        private static final String DEFAULT_SUMMARY = "fallback-summary.md";
        private static final String DEFAULT_AGENT_ROW = "fallback-agent-row.md";
        private static final String DEFAULT_AGENT_SUCCESS = "fallback-agent-success.md";
        private static final String DEFAULT_AGENT_FAILURE = "fallback-agent-failure.md";

        public FallbackTemplates {
            summary = ConfigDefaults.defaultIfBlank(summary, DEFAULT_SUMMARY);
            agentRow = ConfigDefaults.defaultIfBlank(agentRow, DEFAULT_AGENT_ROW);
            agentSuccess = ConfigDefaults.defaultIfBlank(agentSuccess, DEFAULT_AGENT_SUCCESS);
            agentFailure = ConfigDefaults.defaultIfBlank(agentFailure, DEFAULT_AGENT_FAILURE);
        }
    }
}
