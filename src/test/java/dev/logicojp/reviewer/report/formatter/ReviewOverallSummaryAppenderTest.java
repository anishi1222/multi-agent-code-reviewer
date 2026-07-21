package dev.logicojp.reviewer.report.formatter;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewOverallSummaryAppender")
class ReviewOverallSummaryAppenderTest {

    @Test
    @DisplayName("レビュー本文から総評を算出して追記する")
    void appendsSummaryFromReviewContent() {
        ReviewResult review = reviewResult("""
            ### 1. SQL Injection

            | 項目 | 内容 |
            |------|------|
            | **Priority** | High |
            | **指摘の概要** | Placeholder not used |

            ### 2. Secret exposure

            | 項目 | 内容 |
            |------|------|
            | **Priority** | Medium |
            | **指摘の概要** | Secret can be logged |
            """);

        List<ReviewResult> finalized = ReviewOverallSummaryAppender.appendToResults(List.of(review));

        assertThat(finalized).hasSize(1);
        assertThat(finalized.getFirst().content()).contains("**総評**");
        assertThat(finalized.getFirst().content()).contains("2件の指摘事項");
        assertThat(finalized.getFirst().content()).contains("High 1件");
        assertThat(finalized.getFirst().content()).contains("Medium 1件");
    }

    @Test
    @DisplayName("既存の総評は除去して再計算結果で置き換える")
    void replacesExistingOverallSummary() {
        ReviewResult review = reviewResult("""
            ### 1. Naming issue

            | 項目 | 内容 |
            |------|------|
            | **Priority** | Low |

            **総評**

            古い総評
            """);

        ReviewResult result = ReviewOverallSummaryAppender.appendToResults(List.of(review)).getFirst();

        assertThat(result.content()).containsOnlyOnce("**総評**");
        assertThat(result.content()).doesNotContain("古い総評");
    }

    @Test
    @DisplayName("指摘事項なしブロックは件数に含めない")
    void excludesNoFindingsPlaceholderFromSummaryCount() {
        ReviewResult review = reviewResult("""
            ### 1. レビュー結果

            指摘事項なし
            """);

        ReviewResult result = ReviewOverallSummaryAppender.appendToResults(List.of(review)).getFirst();

        assertThat(result.content()).contains("重大な指摘事項は確認されませんでした。");
        assertThat(result.content()).doesNotContain("1件の指摘事項");
    }

    private ReviewResult reviewResult(String content) {
        AgentConfig agent = new AgentConfig(
            "security",
            "Security",
            "model",
            "sys",
            "inst",
            null,
            List.of(),
            List.of()
        );
        return ReviewResult.builder()
            .agentConfig(agent)
            .repository("owner/repo")
            .content(content)
            .success(true)
            .timestamp(Instant.now())
            .build();
    }
}
