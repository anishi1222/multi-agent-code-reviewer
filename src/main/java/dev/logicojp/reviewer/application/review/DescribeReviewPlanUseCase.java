package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.inbound.DescribeReviewPlanPort;
import dev.logicojp.reviewer.application.port.inbound.ReviewPlan;
import dev.logicojp.reviewer.application.port.outbound.ResolveApplicationSettingsPort;

import java.util.Objects;

/// Application use-case: describe the execution plan a review run will follow.
///
/// Implements {@link DescribeReviewPlanPort}. Holds no configuration of its own — effective
/// settings arrive through the outbound settings port bound by the composition root.
///
/// No framework annotations — DI is handled by the composition root.
///
/// Application layer: imports only {@code application.port.*}, {@code java.*}.
public final class DescribeReviewPlanUseCase implements DescribeReviewPlanPort {

    private final ResolveApplicationSettingsPort applicationSettings;

    public DescribeReviewPlanUseCase(ResolveApplicationSettingsPort applicationSettings) {
        this.applicationSettings =
            Objects.requireNonNull(applicationSettings, "applicationSettings must not be null");
    }

    /// {@inheritDoc}
    ///
    /// Reads the supplier on every call rather than caching, so the plan can never report a value
    /// the configuration no longer holds.
    @Override
    public ReviewPlan describePlan() {
        return new ReviewPlan(
            applicationSettings.reviewPasses(),
            applicationSettings.defaultParallelism(),
            applicationSettings.defaultReviewModel(),
            applicationSettings.defaultReportModel(),
            applicationSettings.defaultSummaryModel(),
            applicationSettings.defaultReasoningEffort()
        );
    }
}
