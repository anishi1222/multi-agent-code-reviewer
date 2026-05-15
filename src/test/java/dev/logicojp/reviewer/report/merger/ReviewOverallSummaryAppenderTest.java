package dev.logicojp.reviewer.report.merger;

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
    @DisplayName("マージ済み本文から総評を算出して追記する")
    void appendsSummaryFromMergedContent() {
        AgentConfig agent = new AgentConfig("security", "Security", "model", "sys", "inst", null, List.of(), List.of());
        ReviewResult merged = ReviewResult.builder()
            .agentConfig(agent)
            .repository("owner/repo")
            .content("""
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
                """)
            .success(true)
            .timestamp(Instant.now())
            .build();

        List<ReviewResult> finalized = ReviewOverallSummaryAppender.appendToMergedResults(List.of(merged));

        assertThat(finalized).hasSize(1);
        assertThat(finalized.getFirst().content()).contains("**総評**");
        assertThat(finalized.getFirst().content()).contains("2件の指摘事項");
        assertThat(finalized.getFirst().content()).contains("High 1件");
        assertThat(finalized.getFirst().content()).contains("Medium 1件");
    }

    @Test
    @DisplayName("既存の総評は除去して再計算結果で置き換える")
    void replacesExistingOverallSummary() {
        AgentConfig agent = new AgentConfig("quality", "Quality", "model", "sys", "inst", null, List.of(), List.of());
        ReviewResult merged = ReviewResult.builder()
            .agentConfig(agent)
            .repository("owner/repo")
            .content("""
                ### 1. Naming issue

                | 項目 | 内容 |
                |------|------|
                | **Priority** | Low |

                **総評**

                古い総評
                """)
            .success(true)
            .timestamp(Instant.now())
            .build();

        ReviewResult result = ReviewOverallSummaryAppender.appendToMergedResults(List.of(merged)).getFirst();

        assertThat(result.content()).containsOnlyOnce("**総評**");
        assertThat(result.content()).doesNotContain("古い総評");
    }

    @Test
    @DisplayName("指摘事項なしブロックは件数に含めない")
    void excludesNoFindingsPlaceholderFromSummaryCount() {
        AgentConfig agent = new AgentConfig("best-practices", "Best Practices", "model", "sys", "inst", null, List.of(), List.of());
        ReviewResult merged = ReviewResult.builder()
            .agentConfig(agent)
            .repository("owner/repo")
            .content("""
                ### 1. レビュー結果

                指摘事項なし
                """)
            .success(true)
            .timestamp(Instant.now())
            .build();

        ReviewResult result = ReviewOverallSummaryAppender.appendToMergedResults(List.of(merged)).getFirst();

        assertThat(result.content()).contains("重大な指摘事項は確認されませんでした。");
        assertThat(result.content()).doesNotContain("1件の指摘事項");
    }
}
