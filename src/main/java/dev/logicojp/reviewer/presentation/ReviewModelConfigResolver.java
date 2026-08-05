package dev.logicojp.reviewer.presentation;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/// Resolves model configuration strings from CLI options and application configuration.
///
/// Returns a plain record — no infrastructure ModelConfig import. The individual strings
/// are used by the ApplicationPortFactory when wiring ports.
@Singleton
public class ReviewModelConfigResolver {

    public record ResolvedModels(
        String reviewModel,
        String reportModel,
        String summaryModel,
        String reasoningEffort
    ) {}

    private final String configuredReviewModel;
    private final String configuredReportModel;
    private final String configuredSummaryModel;
    private final String configuredReasoningEffort;

    @Inject
    public ReviewModelConfigResolver(
            @Value("${reviewer.model.review:}") String configuredReviewModel,
            @Value("${reviewer.model.report:}") String configuredReportModel,
            @Value("${reviewer.model.summary:}") String configuredSummaryModel,
            @Value("${reviewer.model.reasoning-effort:}") String configuredReasoningEffort) {
        this.configuredReviewModel = nullIfEmpty(configuredReviewModel);
        this.configuredReportModel = nullIfEmpty(configuredReportModel);
        this.configuredSummaryModel = nullIfEmpty(configuredSummaryModel);
        this.configuredReasoningEffort = nullIfEmpty(configuredReasoningEffort);
    }

    public ResolvedModels resolve(ReviewOptions options) {
        String reviewModel = firstNonNull(options.reviewModel(), configuredReviewModel);
        String reportModel = firstNonNull(options.reportModel(), configuredReportModel);
        String summaryModel = firstNonNull(options.summaryModel(), configuredSummaryModel);
        String reasoningEffort = firstNonNull(options.reasoningEffort(), configuredReasoningEffort);
        return new ResolvedModels(reviewModel, reportModel, summaryModel, reasoningEffort);
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private static String nullIfEmpty(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
