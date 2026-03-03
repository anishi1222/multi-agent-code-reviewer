package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewResultPipeline")
class ReviewResultPipelineTest {

    private static AgentConfig agent(String name) {
        return new AgentConfig(name, name, "model", "system", "instruction", null, List.of(), List.of());
    }

    @Test
    @DisplayName("reviewPassesが1の場合はnull除外のみ行いマージをスキップする")
    void finalizeResultsSkipsMergeForSinglePassAfterFilteringNull() {
        var pipeline = new ReviewResultPipeline();
        var security = ReviewResult.builder()
            .agentConfig(agent("security"))
            .repository("owner/repo")
            .content("""
                ### 1. SQLインジェクション

                | 項目 | 内容 |
                |------|------|
                | **Priority** | High |
                | **指摘の概要** | プレースホルダ未使用 |
                | **該当箇所** | src/A.java L10 |

                **総評**

                追加の観点でも確認済み。
                """)
            .success(true)
            .timestamp(Instant.now())
            .build();

        List<ReviewResult> input = new ArrayList<>();
        input.add(security);
        input.add(null);
        List<ReviewResult> finalized = pipeline.finalizeResults(input, 1);

        assertThat(finalized).hasSize(1);
        assertThat(finalized.getFirst().agentConfig().name()).isEqualTo("security");
        assertThat(finalized.getFirst().content()).contains("### 1. SQLインジェクション");
        assertThat(finalized.getFirst().content()).contains("**総評**");
        assertThat(finalized.getFirst().content()).contains("追加の観点でも確認済み。");
    }

    @Test
    @DisplayName("reviewPassesが複数の場合はエージェント単位にマージする")
    void finalizeResultsMergesWhenMultiPass() {
        var pipeline = new ReviewResultPipeline();
        var pass1 = ReviewResult.builder()
            .agentConfig(agent("security"))
            .repository("owner/repo")
            .content("""
                ### 1. SQLインジェクション

                | 項目 | 内容 |
                |------|------|
                | **Priority** | High |
                | **指摘の概要** | プレースホルダ未使用 |
                | **該当箇所** | src/A.java L10 |
                """)
            .success(true)
            .timestamp(Instant.now())
            .build();
        var pass2 = ReviewResult.builder()
            .agentConfig(agent("security"))
            .repository("owner/repo")
            .content("""
                ### 1. SQLインジェクション

                | 項目 | 内容 |
                |------|------|
                | **Priority** | High |
                | **指摘の概要** | プレースホルダ未使用 |
                | **該当箇所** | src/A.java L10 |
                """)
            .success(true)
            .timestamp(Instant.now())
            .build();

        List<ReviewResult> finalized = pipeline.finalizeResults(List.of(pass1, pass2), 2);

        assertThat(finalized).hasSize(1);
        assertThat(finalized.getFirst().content()).contains("検出パス: 1, 2");
    }

}