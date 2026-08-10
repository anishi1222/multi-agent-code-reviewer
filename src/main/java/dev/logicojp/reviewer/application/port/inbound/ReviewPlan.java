package dev.logicojp.reviewer.application.port.inbound;

import java.util.Objects;

/// Inbound port DTO: the execution plan that a review run will follow.
///
/// Carries effective application-owned settings that a caller may legitimately need before
/// execution starts. Presentation may overlay explicit CLI values, but it never binds or
/// re-defaults infrastructure configuration.
///
/// ## Why this exists
///
/// Before t28, `presentation.formatter.ReviewOutputFormatter` obtained the pass count by binding
/// `@Value("${reviewer.execution.review-passes:1}")` — a configuration key that **nothing else
/// read**. The executor resolved `reviewer.execution.concurrency.review-passes` instead, so the
/// two could disagree in both directions: setting the real key ran N passes while the banner
/// reported 1, and setting the formatter's key printed N while a single pass ran (t24/F3).
///
/// The defect was structural, not a typo. A presentation class that names an infrastructure
/// configuration key by string has no compile-time or test-time link to the value the executor
/// uses, so drift is invisible. Routing the value through this port restores that link: the
/// banner can only display what the plan says, and the plan is derived from the same accessor
/// the executor resolves.
///
/// @param reviewPasses number of review passes each agent will run; always at least 1
/// @param defaultParallelism effective default worker concurrency; always at least 1
/// @param defaultReviewModel effective default model for review stages
/// @param defaultReportModel effective default model for report generation
/// @param defaultSummaryModel effective default model for summary generation
/// @param defaultReasoningEffort effective default reasoning effort
public record ReviewPlan(
    int reviewPasses,
    int defaultParallelism,
    String defaultReviewModel,
    String defaultReportModel,
    String defaultSummaryModel,
    String defaultReasoningEffort
) {

    /// Rejects a non-positive pass count rather than normalising it.
    ///
    /// Normalisation is already the responsibility of the configuration record that owns the
    /// value — ADR-0006 D6 gives cross-layer defaults a single owner. If a zero or negative
    /// count ever reaches this constructor, that owner has stopped normalising; silently
    /// substituting 1 here would hide the regression and re-create the class of defect this
    /// port was introduced to remove.
    public ReviewPlan {
        if (reviewPasses < 1) {
            throw new IllegalArgumentException(
                "reviewPasses must be at least 1, was " + reviewPasses
                    + " — the configuration source stopped normalising non-positive values");
        }
        if (defaultParallelism < 1) {
            throw new IllegalArgumentException(
                "defaultParallelism must be at least 1, was " + defaultParallelism
                    + " — the configuration source stopped normalising non-positive values");
        }
        defaultReviewModel = requireNonBlank(defaultReviewModel, "defaultReviewModel");
        defaultReportModel = requireNonBlank(defaultReportModel, "defaultReportModel");
        defaultSummaryModel = requireNonBlank(defaultSummaryModel, "defaultSummaryModel");
        defaultReasoningEffort =
            requireNonBlank(defaultReasoningEffort, "defaultReasoningEffort");
    }

    /// Whether the plan runs more than one pass per agent.
    ///
    /// A fact about the plan, not a display rule: callers decide what to do with it.
    public boolean isMultiPass() {
        return reviewPasses > 1;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
