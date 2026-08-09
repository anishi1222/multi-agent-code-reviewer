package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.inbound.ReviewPlan;
import dev.logicojp.reviewer.application.port.outbound.ResolveApplicationSettingsPort;
import dev.logicojp.reviewer.shared.PromptBudget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DescribeReviewPlanUseCase")
class DescribeReviewPlanUseCaseTest {

    @Test
    @DisplayName("設定所有者の全実効値をReviewPlanへ写像する")
    void mapsEveryEffectiveSettingIntoTheReviewPlan() {
        ResolveApplicationSettingsPort settings = new StubSettings(
            3, 8, "review-default", "report-default", "summary-default", "medium");

        ReviewPlan plan = new DescribeReviewPlanUseCase(settings).describePlan();

        assertThat(plan)
            .extracting(
                ReviewPlan::reviewPasses,
                ReviewPlan::defaultParallelism,
                ReviewPlan::defaultReviewModel,
                ReviewPlan::defaultReportModel,
                ReviewPlan::defaultSummaryModel,
                ReviewPlan::defaultReasoningEffort)
            .containsExactly(
                3, 8, "review-default", "report-default", "summary-default", "medium");
    }

    private record StubSettings(
        int reviewPasses,
        int defaultParallelism,
        String defaultReviewModel,
        String defaultReportModel,
        String defaultSummaryModel,
        String defaultReasoningEffort
    ) implements ResolveApplicationSettingsPort {

        @Override
        public String defaultSkillModel() {
            return defaultReviewModel;
        }

        @Override
        public SummarySettings summarySettings() {
            return new SummarySettings(1, 2, 3, 4, 5, 6, new PromptBudget());
        }

        @Override
        public boolean ghAuthFallbackEnabled() {
            return false;
        }
    }
}
