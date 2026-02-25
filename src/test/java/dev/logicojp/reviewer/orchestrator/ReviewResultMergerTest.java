package dev.logicojp.reviewer.orchestrator;

import org.assertj.core.api.Assertions;
import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.report.ReviewResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;


@DisplayName("ReviewResultMerger")
class ReviewResultMergerTest {

    private static final AgentConfig AGENT_A = AgentConfig.builder()
            .name("security")
            .displayName("Security Reviewer")
            .build();

    private static final AgentConfig AGENT_B = AgentConfig.builder()
            .name("quality")
            .displayName("Quality Reviewer")
            .build();

    private ReviewResult successResult(AgentConfig config, String content) {
        return ReviewResult.builder()
                .agentConfig(config)
                .repository("owner/repo")
                .content(content)
                .success(true)
                .build();
    }

    private ReviewResult failedResult(AgentConfig config, String error) {
        return ReviewResult.builder()
                .agentConfig(config)
                .repository("owner/repo")
                .content("")
                .success(false)
                .errorMessage(error)
                .build();
    }

    // ========================================================================
    // mergeByAgent
    // ========================================================================
    @Nested
    @DisplayName("mergeByAgent")
    class MergeByAgentTest {

        @Test
        @DisplayName("nullの場合は空リストを返す")
        void nullReturnsEmpty() {
            Assertions.assertThat(ReviewResultMerger.mergeByAgent(null)).isEmpty();
        }

        @Test
        @DisplayName("空リストの場合は空リストを返す")
        void emptyReturnsEmpty() {
            Assertions.assertThat(ReviewResultMerger.mergeByAgent(List.of())).isEmpty();
        }

        @Test
        @DisplayName("エージェントごとに1つの結果の場合はそのまま返す")
        void singleResultPerAgentPassedThrough() {
            var results = List.of(
                    successResult(AGENT_A, "Result A"),
                    successResult(AGENT_B, "Result B"));

            var merged = ReviewResultMerger.mergeByAgent(results);

            Assertions.assertThat(merged).hasSize(2);
            Assertions.assertThat(merged.get(0).agentConfig().name()).isEqualTo("security");
            Assertions.assertThat(merged.get(1).agentConfig().name()).isEqualTo("quality");
        }

        @Test
        @DisplayName("同一エージェントの複数パスがマージされる")
        void multiplePassesMerged() {
            String pass1 = """
                    ### 1. SQLインジェクション
                    
                    | **Priority** | High |
                    | **指摘の概要** | パラメータ化されていないクエリ |
                    | **該当箇所** | UserDao.java |
                    
                    詳細な説明
                    """;
            String pass2 = """
                    ### 1. SQLインジェクション
                    
                    | **Priority** | High |
                    | **指摘の概要** | パラメータ化されていないクエリ |
                    | **該当箇所** | UserDao.java |
                    
                    詳細な説明（第2パス）
                    """;

            var results = List.of(
                    successResult(AGENT_A, pass1),
                    successResult(AGENT_A, pass2));

            var merged = ReviewResultMerger.mergeByAgent(results);

            Assertions.assertThat(merged).hasSize(1);
            Assertions.assertThat(merged.getFirst().success()).isTrue();
            // Duplicate findings should be deduplicated
            Assertions.assertThat(merged.getFirst().content()).contains("### 1.");
        }

        @Test
        @DisplayName("全パス失敗の場合は最後の結果を返す")
        void allFailedReturnsLast() {
            var results = List.of(
                    failedResult(AGENT_A, "error1"),
                    failedResult(AGENT_A, "error2"));

            var merged = ReviewResultMerger.mergeByAgent(results);

            Assertions.assertThat(merged).hasSize(1);
            Assertions.assertThat(merged.getFirst().success()).isFalse();
            Assertions.assertThat(merged.getFirst().errorMessage()).isEqualTo("error2");
        }

        @Test
        @DisplayName("異なるfindingsはそれぞれ保持される")
        void differentFindingsPreserved() {
            String pass1 = """
                    ### 1. SQLインジェクション
                    
                    | **Priority** | High |
                    | **指摘の概要** | パラメータ化されていないクエリ |
                    | **該当箇所** | UserDao.java |
                    
                    詳細な説明1
                    """;
            String pass2 = """
                    ### 1. XSS脆弱性
                    
                    | **Priority** | Medium |
                    | **指摘の概要** | 入力のエスケープ漏れ |
                    | **該当箇所** | HtmlRenderer.java |
                    
                    詳細な説明2
                    """;

            var results = List.of(
                    successResult(AGENT_A, pass1),
                    successResult(AGENT_A, pass2));

            var merged = ReviewResultMerger.mergeByAgent(results);

            Assertions.assertThat(merged).hasSize(1);
            Assertions.assertThat(merged.getFirst().content())
                    .contains("SQLインジェクション")
                    .contains("XSS脆弱性");
        }

        @Test
        @DisplayName("agentConfigがnullの結果は__unknown__にグループ化される")
        void nullAgentConfigGroupedAsUnknown() {
            var result = ReviewResult.builder()
                    .agentConfig(null)
                    .repository("owner/repo")
                    .content("content")
                    .success(true)
                    .build();

            var merged = ReviewResultMerger.mergeByAgent(List.of(result));
            Assertions.assertThat(merged).hasSize(1);
        }

        @Test
        @DisplayName("findingsのないコンテンツはfallbackとして処理される")
        void noFindingsHandledAsFallback() {
            var results = List.of(
                    successResult(AGENT_A, "問題は見つかりませんでした。"),
                    successResult(AGENT_A, "全体的に良好なコードです。"));

            var merged = ReviewResultMerger.mergeByAgent(results);

            Assertions.assertThat(merged).hasSize(1);
            Assertions.assertThat(merged.getFirst().success()).isTrue();
        }
    }

    // ========================================================================
    // normalizeText
    // ========================================================================
    @Nested
    @DisplayName("normalizeText")
    class NormalizeTextTest {

        @Test
        @DisplayName("nullの場合は空文字列を返す")
        void nullReturnsEmpty() {
            Assertions.assertThat(ReviewResultMerger.normalizeText(null)).isEmpty();
        }

        @Test
        @DisplayName("空白のみの場合は空文字列を返す")
        void blankReturnsEmpty() {
            Assertions.assertThat(ReviewResultMerger.normalizeText("   ")).isEmpty();
        }

        @Test
        @DisplayName("小文字に正規化される")
        void lowercased() {
            Assertions.assertThat(ReviewResultMerger.normalizeText("Hello WORLD"))
                    .isEqualTo("hello world");
        }

        @Test
        @DisplayName("Markdownのフォーマット文字が除去される")
        void markdownCharsRemoved() {
            Assertions.assertThat(ReviewResultMerger.normalizeText("`code` **bold** _italic_"))
                    .isEqualTo("code bold italic");
        }

        @Test
        @DisplayName("パイプとスラッシュがスペースに正規化される")
        void pipeAndSlashNormalized() {
            Assertions.assertThat(ReviewResultMerger.normalizeText("a|b/c"))
                    .isEqualTo("a b c");
        }

        @Test
        @DisplayName("中黒（・）がスペースに正規化される")
        void middleDotNormalized() {
            Assertions.assertThat(ReviewResultMerger.normalizeText("セキュリティ・レビュー"))
                    .isEqualTo("セキュリティ レビュー");
        }

        @Test
        @DisplayName("連続する空白が1つに圧縮される")
        void consecutiveWhitespaceCollapsed() {
            Assertions.assertThat(ReviewResultMerger.normalizeText("a   b\t\tc\n\nd"))
                    .isEqualTo("a b c d");
        }
    }

    // ========================================================================
    // FindingBlock record
    // ========================================================================
    @Nested
    @DisplayName("FindingBlock")
    class FindingBlockTest {

        @Test
        @DisplayName("titleとbodyが正しく保持される")
        void recordFields() {
            var block = new ReviewResultMerger.FindingBlock("title", "body");
            Assertions.assertThat(block.title()).isEqualTo("title");
            Assertions.assertThat(block.body()).isEqualTo("body");
        }
    }

    // ========================================================================
    // AggregatedFinding record
    // ========================================================================
    @Nested
    @DisplayName("AggregatedFinding")
    class AggregatedFindingTest {

        @Test
        @DisplayName("passNumbersが不変にコピーされる")
        void passNumbersImmutable() {
            var finding = new ReviewResultMerger.AggregatedFinding(
                    "title", "body", Set.of(1),
                    "title", "high", "summary", "location",
                    Set.of(1), Set.of(2), Set.of(3));

            Assertions.assertThat(finding.passNumbers()).containsExactly(1);
        }

        @Test
        @DisplayName("withPassで新しいパス番号が追加される")
        void withPassAddsNumber() {
            var finding = new ReviewResultMerger.AggregatedFinding(
                    "title", "body", Set.of(1),
                    "title", "high", "summary", "location",
                    Set.of(1), Set.of(2), Set.of(3));

            var updated = finding.withPass(2);

            Assertions.assertThat(updated.passNumbers()).containsExactlyInAnyOrder(1, 2);
            // Original unchanged
            Assertions.assertThat(finding.passNumbers()).containsExactly(1);
        }
    }
}
