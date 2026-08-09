package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.DescribeReviewPlanPort;
import dev.logicojp.reviewer.application.port.inbound.ReviewPlan;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Objects;

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

    private final DescribeReviewPlanPort describeReviewPlanPort;

    @Inject
    public ReviewModelConfigResolver(DescribeReviewPlanPort describeReviewPlanPort) {
        this.describeReviewPlanPort =
            Objects.requireNonNull(describeReviewPlanPort, "describeReviewPlanPort must not be null");
    }

    public ResolvedModels resolve(ReviewOptions options) {
        Objects.requireNonNull(options, "options must not be null");
        ReviewPlan plan = describeReviewPlanPort.describePlan();
        String reviewModel = firstNonBlank(
            options.reviewModel(), options.defaultModel(), plan.defaultReviewModel());
        String reportModel = firstNonBlank(
            options.reportModel(), options.defaultModel(), plan.defaultReportModel());
        String summaryModel = firstNonBlank(
            options.summaryModel(), options.defaultModel(), plan.defaultSummaryModel());
        String reasoningEffort =
            firstNonBlank(options.reasoningEffort(), plan.defaultReasoningEffort());
        return new ResolvedModels(reviewModel, reportModel, summaryModel, reasoningEffort);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalStateException("review plan did not provide an effective model default");
    }
}
