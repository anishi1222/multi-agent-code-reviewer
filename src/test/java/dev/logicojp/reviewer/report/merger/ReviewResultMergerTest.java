package dev.logicojp.reviewer.report.merger;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.finding.AggregatedFinding;
import dev.logicojp.reviewer.report.finding.ReviewFindingParser;
import dev.logicojp.reviewer.report.finding.ReviewFindingSimilarity;
import dev.logicojp.reviewer.report.finding.FindingsExtractor;
import dev.logicojp.reviewer.report.sanitize.ContentSanitizer;
import dev.logicojp.reviewer.report.summary.SummaryGenerator;

import dev.logicojp.reviewer.agent.AgentConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewResultMerger")
class ReviewResultMergerTest {

    private static String finding(String number, String title, String priority, String summary, String impact, String location) {
        return """
            ### %s. %s

            | 項目 | 内容 |
            |------|------|
            | **Priority** | %s |
            | **指摘の概要** | %s |
            | **修正しない場合の影響** | %s |
            | **該当箇所** | %s |

            **推奨対応**

            具体的な修正内容

            **効果**

            改善効果
            """.formatted(number, title, priority, summary, impact, location);
    }

    private static AgentConfig createAgent(String name) {
        return new AgentConfig(name, name + " Review", "model",
            "system", "instruction", null,
            List.of("area"), List.of());
    }

    private static ReviewResult successResult(AgentConfig agent, String content) {
        return ReviewResult.builder()
            .agentConfig(agent)
            .repository("test/repo")
            .content(content)
            .success(true)
            .timestamp(Instant.now())
            .build();
    }

    private static ReviewResult failureResult(AgentConfig agent, String error) {
        return ReviewResult.builder()
            .agentConfig(agent)
            .repository("test/repo")
            .success(false)
            .errorMessage(error)
            .timestamp(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("mergeByAgent - 空・null入力")
    class EmptyInput {

        @Test
        @DisplayName("nullの場合は空リストを返す")
        void nullReturnsEmptyList() {
            assertThat(ReviewResultMerger.mergeByAgent(null)).isEmpty();
        }

        @Test
        @DisplayName("空リストの場合は空リストを返す")
        void emptyListReturnsEmptyList() {
            assertThat(ReviewResultMerger.mergeByAgent(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("mergeByAgent - 単一パス")
    class SinglePass {

        @Test
        @DisplayName("エージェントごとに1つの成功結果でもフォーマット正規化を適用する")
        void singleResultPerAgentIsNormalized() {
            var agent1 = createAgent("security");
            var agent2 = createAgent("performance");
            var result1 = successResult(agent1, finding("1", "SQLインジェクション", "High",
                "プレースホルダ未使用", "情報漏洩", "src/A.java L10"));
            var result2 = successResult(agent2, finding("1", "N+1", "Medium",
                "ループ内クエリ", "遅延", "src/B.java L20"));

            List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(List.of(result1, result2));

            assertThat(merged).hasSize(2);
            assertThat(merged.get(0).content()).contains("### 1. SQLインジェクション");
            assertThat(merged.get(1).content()).contains("### 1. N+1");
        }

        @Test
        @DisplayName("単一パスの末尾に含まれた総評セクションは除去される")
        void trailingOverallSectionIsRemovedFromSinglePassFindingBody() {
            var agent = createAgent("security");
            var content = """
                ### 1. ログディレクトリの権限制御不足

                | 項目 | 内容 |
                |------|------|
                | **Priority** | Medium |
                | **指摘の概要** | ログディレクトリが共有環境で読み取り可能 |
                | **修正しない場合の影響** | 監査ログ漏えい |
                | **該当箇所** | src/main/resources/logback.xml L12-21 |

                **推奨対応**

                権限を制限する

                **効果**

                情報漏えい防止

                **総評**

                全体としては堅牢だが改善余地がある。
                """;
            var result = successResult(agent, content);

            List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(List.of(result));

            assertThat(merged).hasSize(1);
            assertThat(merged.getFirst().content()).contains("### 1. ログディレクトリの権限制御不足");
            assertThat(merged.getFirst().content()).doesNotContain("**総評**");
            assertThat(merged.getFirst().content()).doesNotContain("マージ後のレビュー結果として");
            assertThat(merged.getFirst().content()).doesNotContain("全体としては堅牢だが改善余地がある");
        }

        @Test
        @DisplayName("複数パスで同一総評がある場合も本文には総評を含めない")
        void duplicateOverallSummaryAcrossPassesIsCollapsed() {
            var agent = createAgent("security");
            var content1 = finding("1", "設定漏れ", "Medium", "設定漏れあり", "障害", "src/A.java L1")
                + "\n\n**総評**\n\n同じ改善方針を推奨。\n";
            var content2 = finding("2", "設定漏れ", "Medium", "設定漏れあり", "障害", "src/A.java L1")
                + "\n\n**総評**\n\n同じ改善方針を推奨。\n";

            List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(List.of(
                successResult(agent, content1),
                successResult(agent, content2)
            ));

            assertThat(merged).hasSize(1);
            assertThat(merged.getFirst().content()).doesNotContain("**総評**");
            assertThat(merged.getFirst().content()).doesNotContain("マージ後のレビュー結果として");
            assertThat(merged.getFirst().content()).doesNotContain("同じ改善方針を推奨。");
        }

        @Test
        @DisplayName("複数パスで総評が異なっていても本文には総評を含めない")
        void differentOverallSummariesAcrossPassesUseMergedSummaryOnly() {
            var agent = createAgent("security");
            var content1 = finding("1", "設定漏れ", "Medium", "設定漏れあり", "障害", "src/A.java L1")
                + "\n\n**総評**\n\n運用設定の見直しが必要。\n";
            var content2 = finding("2", "権限過多", "High", "過剰権限", "情報漏えい", "src/B.java L2")
                + "\n\n**総評**\n\n権限設計の見直しが必要。\n";

            List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(List.of(
                successResult(agent, content1),
                successResult(agent, content2)
            ));

            assertThat(merged).hasSize(1);
            assertThat(merged.getFirst().content()).doesNotContain("**総評**");
            assertThat(merged.getFirst().content()).doesNotContain("マージ後のレビュー結果として");
            assertThat(merged.getFirst().content()).doesNotContain("#### パス 1");
            assertThat(merged.getFirst().content()).doesNotContain("#### パス 2");
            assertThat(merged.getFirst().content()).doesNotContain("運用設定の見直しが必要。");
            assertThat(merged.getFirst().content()).doesNotContain("権限設計の見直しが必要。");
        }
    }

    @Nested
    @DisplayName("mergeByAgent - マルチパス")
    class MultiPass {

        @Test
        @DisplayName("差し替えた戦略を利用してマージ処理を実行できる")
        void usesInjectedStrategies() {
            var agent = createAgent("security");
            var result1 = successResult(agent, "raw-content-1");
            var result2 = successResult(agent, "raw-content-2");

            var extractorCalled = new AtomicBoolean(false);
            var keyResolverCalled = new AtomicBoolean(false);
            var formatterCalled = new AtomicBoolean(false);

            List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(
                List.of(result1, result2),
                content -> {
                    extractorCalled.set(true);
                    return List.of(new ReviewFindingParser.FindingBlock("Injected", "body"));
                },
                (block, normalized) -> {
                    keyResolverCalled.set(true);
                    return "injected-key";
                },
                (findings, total, failed) -> {
                    formatterCalled.set(true);
                    return "INJECTED";
                }
            );

            assertThat(merged).hasSize(1);
            assertThat(merged.getFirst().content()).startsWith("INJECTED");
            assertThat(merged.getFirst().content()).doesNotContain("**総評**");
            assertThat(extractorCalled).isTrue();
            assertThat(keyResolverCalled).isTrue();
            assertThat(formatterCalled).isTrue();
        }

        @Test
        @DisplayName("同一エージェントの複数成功結果がマージされる")
        void multipleSuccessResultsAreMerged() {
            var agent = createAgent("security");
            var result1 = successResult(agent, finding("1", "SQLインジェクション", "High", "プレースホルダ未使用", "情報漏洩", "src/A.java L10"));
            var result2 = successResult(agent, finding("1", "SQLインジェクション", "High", "プレースホルダ未使用", "情報漏洩", "src/A.java L10"));

            List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(List.of(result1, result2));

            assertThat(merged).hasSize(1);
            ReviewResult mergedResult = merged.getFirst();
            assertThat(mergedResult.success()).isTrue();
            assertThat(mergedResult.agentConfig().name()).isEqualTo("security");
            assertThat(mergedResult.content()).contains("### 1. SQLインジェクション");
            assertThat(mergedResult.content()).contains("検出パス: 1, 2");
            assertThat(mergedResult.content()).doesNotContain("### 2.");
        }

        @Test
        @DisplayName("3パスの結果が正しくマージされる")
        void threePassResultsAreMerged() {
            var agent = createAgent("quality");
            var result1 = successResult(agent, finding("1", "nullチェック不足", "Medium", "NPEリスク", "実行時例外", "src/B.java L20"));
            var result2 = successResult(agent, finding("2", "命名規則違反", "Low", "可読性低下", "保守性悪化", "src/C.java L30"));
            var result3 = successResult(agent, finding("3", "nullチェック不足", "Medium", "NPEリスク", "実行時例外", "src/B.java L20"));

            List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(
                List.of(result1, result2, result3));

            assertThat(merged).hasSize(1);
            ReviewResult mergedResult = merged.getFirst();
            assertThat(mergedResult.content()).contains("### 1. nullチェック不足");
            assertThat(mergedResult.content()).contains("### 2. 命名規則違反");
            assertThat(mergedResult.content()).contains("検出パス: 1, 3");
        }

        @Test
        @DisplayName("成功と失敗が混在する場合は成功のみマージし注記を追加する")
        void mixedSuccessAndFailureResultsMergeSuccessfulOnly() {
            var agent = createAgent("security");
            var success1 = successResult(agent, finding("1", "トークン露出", "High", "ログ出力に含まれる", "漏洩", "src/S.java L10"));
            var failure = failureResult(agent, "Timeout");
            var success2 = successResult(agent, finding("1", "トークン露出", "High", "ログ出力に含まれる", "漏洩", "src/S.java L10"));

            List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(
                List.of(success1, failure, success2));

            assertThat(merged).hasSize(1);
            ReviewResult mergedResult = merged.getFirst();
            assertThat(mergedResult.success()).isTrue();
            assertThat(mergedResult.content()).contains("### 1. トークン露出");
            assertThat(mergedResult.content()).contains("1 パスが失敗しました");
            assertThat(mergedResult.content()).contains("成功したパスの結果のみです");
        }

        @Test
        @DisplayName("全パス失敗の場合は最後の失敗結果を返す")
        void allFailuresReturnsLastFailure() {
            var agent = createAgent("security");
            var failure1 = failureResult(agent, "Error 1");
            var failure2 = failureResult(agent, "Error 2");

            List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(
                List.of(failure1, failure2));

            assertThat(merged).hasSize(1);
            ReviewResult mergedResult = merged.getFirst();
            assertThat(mergedResult.success()).isFalse();
            assertThat(mergedResult.errorMessage()).isEqualTo("Error 2");
        }

        @Test
        @DisplayName("複数エージェントのマルチパスが正しくグループ化される")
        void multipleAgentsMultiPassGroupedCorrectly() {
            var agent1 = createAgent("security");
            var agent2 = createAgent("performance");
            var sec1 = successResult(agent1, finding("1", "認証不備", "High", "権限チェック不足", "不正アクセス", "src/Auth.java L40"));
            var perf1 = successResult(agent2, finding("1", "N+1", "High", "ループ内クエリ", "遅延", "src/Repo.java L18"));
            var sec2 = successResult(agent1, finding("1", "認証不備", "High", "権限チェック不足", "不正アクセス", "src/Auth.java L40"));
            var perf2 = successResult(agent2, finding("2", "キャッシュ不足", "Medium", "再計算が多い", "CPU増加", "src/Calc.java L55"));

            // Results interleaved as they might arrive from parallel execution
            List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(
                List.of(sec1, perf1, sec2, perf2));

            assertThat(merged).hasSize(2);
            assertThat(merged.get(0).agentConfig().name()).isEqualTo("security");
            assertThat(merged.get(0).content()).contains("### 1. 認証不備");
            assertThat(merged.get(1).agentConfig().name()).isEqualTo("performance");
            assertThat(merged.get(1).content()).contains("### 1. N+1");
            assertThat(merged.get(1).content()).contains("### 2. キャッシュ不足");
        }

        @Test
        @DisplayName("指摘がないパスのみの場合は指摘事項なしを返す")
        void noFindingPassesReturnsNoFindings() {
            var agent = createAgent("security");
            var result1 = successResult(agent, "指摘事項なし");
            var result2 = successResult(agent, "指摘事項なし");

            List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(List.of(result1, result2));

            String content = merged.getFirst().content();
            assertThat(content).contains("指摘事項なし");
        }

        @Test
        @DisplayName("同一箇所でも表現が異なる指摘は厳密マージで重複排除しない")
        void similarWordingFindingsAreKeptSeparateInStrictMerge() {
            var agent = createAgent("quality");

            var pass1 = successResult(agent, finding(
                "1",
                "ReviewCommand クラスの責務過多",
                "High",
                "CLI引数解析とレビュー実行ロジックが同一クラスに混在している",
                "保守性低下",
                "src/main/java/dev/logicojp/reviewer/cli/ReviewCommand.java"
            ));

            var pass2 = successResult(agent, finding(
                "2",
                "ReviewCommand の過剰な責務",
                "High",
                "CLI解析・実行・表示処理が1クラスに集約されている",
                "変更影響範囲の拡大",
                "src/main/java/dev/logicojp/reviewer/cli/ReviewCommand.java"
            ));

            List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(List.of(pass1, pass2));

            assertThat(merged).hasSize(1);
            String content = merged.getFirst().content();
            assertThat(content).contains("### 1. ReviewCommand クラスの責務過多");
            assertThat(content).contains("### 2. ReviewCommand の過剰な責務");
        }

        @Test
        @DisplayName("同名でも該当箇所が異なる指摘は重複排除しない")
        void similarTitleDifferentLocationIsNotDeduplicated() {
            var agent = createAgent("quality");

            var pass1 = successResult(agent, finding(
                "1",
                "設定値のハードコーディング",
                "Medium",
                "設定値がコード内に固定されている",
                "柔軟性低下",
                "src/main/java/dev/logicojp/reviewer/service/AgentService.java"
            ));

            var pass2 = successResult(agent, finding(
                "2",
                "設定値のハードコーディング",
                "Medium",
                "設定値がコードに埋め込まれている",
                "保守性低下",
                "src/main/java/dev/logicojp/reviewer/target/LocalFileProvider.java"
            ));

            List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(List.of(pass1, pass2));

            assertThat(merged).hasSize(1);
            String content = merged.getFirst().content();
            assertThat(content).contains("### 1. 設定値のハードコーディング");
            assertThat(content).contains("### 2. 設定値のハードコーディング");
        }
    }
}
