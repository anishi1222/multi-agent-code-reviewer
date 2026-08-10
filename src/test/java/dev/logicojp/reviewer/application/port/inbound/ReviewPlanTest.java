package dev.logicojp.reviewer.application.port.inbound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReviewPlan")
class ReviewPlanTest {

    @Test
    @DisplayName("設定所有者が正規化しなかった非正値を拒否する")
    void rejectsNonPositiveExecutionDefaults() {
        assertThatThrownBy(() -> plan(0, 4))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reviewPasses");
        assertThatThrownBy(() -> plan(1, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("defaultParallelism");
    }

    @Test
    @DisplayName("設定所有者が正規化しなかった空モデル値を拒否する")
    void rejectsBlankEffectiveModelDefaults() {
        assertThatThrownBy(() -> new ReviewPlan(1, 4, "", "report", "summary", "high"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("defaultReviewModel");
        assertThatThrownBy(() -> new ReviewPlan(1, 4, "review", null, "summary", "high"))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("defaultReportModel");
    }

    private static ReviewPlan plan(int reviewPasses, int defaultParallelism) {
        return new ReviewPlan(
            reviewPasses, defaultParallelism, "review", "report", "summary", "high");
    }
}
