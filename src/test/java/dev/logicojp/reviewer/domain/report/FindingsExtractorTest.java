package dev.logicojp.reviewer.domain.report;


import dev.logicojp.reviewer.domain.agent.AgentConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FindingsExtractor")
class FindingsExtractorTest {

    private static AgentConfig testAgent(String name) {
        return new AgentConfig(name, name + " Review", "model",
            "prompt", null, null, List.of("area"), List.of());
    }

    @Nested
    @DisplayName("Priority抽出")
    class PriorityExtraction {

        @Test
        @DisplayName("Priority行からCritical/High/Medium/Lowを抽出する")
        void extractsPriorityLevels() {
            String content = """
                ### 1. SQLインジェクション
                
                | 項目 | 内容 |
                | **Priority** | Critical |
                
                ### 2. ログの問題
                
                | 項目 | 内容 |
                | **Priority** | Low |
                """;

            var findings = FindingsExtractor.extractFindings(content, "security");

            assertThat(findings).hasSize(2);
            assertThat(findings.get(0).priority()).isEqualTo("Critical");
            assertThat(findings.get(0).title()).isEqualTo("SQLインジェクション");
            assertThat(findings.get(0).category()).isEqualTo("security");
            assertThat(findings.get(1).priority()).isEqualTo("Low");
            assertThat(findings.get(1).title()).isEqualTo("ログの問題");
        }

        @Test
        @DisplayName("太字マークアップなしのPriority行も抽出する")
        void extractsWithoutBoldMarkup() {
            String content = """
                ### 1. Issue
                
                | Priority | High |
                """;

            var findings = FindingsExtractor.extractFindings(content, "agent");

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).priority()).isEqualTo("High");
        }
    }

    @Nested
    @DisplayName("Finding見出し抽出")
    class HeadingExtraction {

        @Test
        @DisplayName("### 番号. タイトル 形式を抽出する")
        void extractsNumberedHeadings() {
            String content = """
                ### 1. First Finding
                
                | **Priority** | Medium |
                
                ### 2. Second Finding
                
                | **Priority** | Low |
                """;

            var findings = FindingsExtractor.extractFindings(content, "agent");

            assertThat(findings).hasSize(2);
            assertThat(findings.get(0).title()).isEqualTo("First Finding");
            assertThat(findings.get(1).title()).isEqualTo("Second Finding");
        }

        @Test
        @DisplayName("### [番号] タイトル 形式を抽出する")
        void extractsBracketedHeadings() {
            String content = """
                ### [1] Bracket Style
                
                | **Priority** | High |
                """;

            var findings = FindingsExtractor.extractFindings(content, "agent");

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).title()).isEqualTo("Bracket Style");
        }
    }

    @Nested
    @DisplayName("エッジケース")
    class EdgeCases {

        @Test
        @DisplayName("指摘事項なしの場合は空リストを返す")
        void returnsEmptyForNoFindings() {
            String content = "指摘事項なし\n\nレビュー完了。";

            var findings = FindingsExtractor.extractFindings(content, "agent");

            assertThat(findings).isEmpty();
        }

        @Test
        @DisplayName("見出しがなくPriorityのみの場合はGenericエントリを生成する")
        void generatesGenericEntriesForPriorityOnly() {
            String content = """
                | **Priority** | High |
                | **Priority** | Medium |
                """;

            var findings = FindingsExtractor.extractFindings(content, "agent");

            assertThat(findings).hasSize(2);
            assertThat(findings.get(0).title()).isEqualTo("Finding 1");
        }

        @Test
        @DisplayName("見出しがありPriorityがない場合はUnknown優先度を設定する")
        void setsUnknownPriorityWhenMissing() {
            String content = """
                ### 1. Some Issue
                
                Description without priority table.
                """;

            var findings = FindingsExtractor.extractFindings(content, "agent");

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).priority()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("null入力に対して空文字列を返す")
        void returnsEmptyForNullResults() {
            assertThat(FindingsSummaryFormatter.formatSummary(FindingsExtractor.extractAll(null))).isEmpty();
        }

        @Test
        @DisplayName("空リスト入力に対して空文字列を返す")
        void returnsEmptyForEmptyResults() {
            assertThat(FindingsSummaryFormatter.formatSummary(FindingsExtractor.extractAll(List.of()))).isEmpty();
        }
    }

    @Nested
    @DisplayName("サマリー生成")
    class SummaryGeneration {

        // removed: parser/formatter strategy injection overload was removed; extraction and formatting now compose via FindingsExtractor.extractAll and FindingsSummaryFormatter.formatSummary.

        @Test
        @DisplayName("複数エージェントの結果から優先度別サマリーを生成する")
        void generatesCombinedSummary() {
            String securityContent = """
                ### 1. SQL Injection
                
                | **Priority** | Critical |
                """;
            String qualityContent = """
                ### 1. Dead Code
                
                | **Priority** | Low |
                """;

            var results = List.of(
                ReviewResult.builder()
                    .agentConfig(testAgent("security")).repository("test")
                    .content(securityContent).success(true).build(),
                ReviewResult.builder()
                    .agentConfig(testAgent("quality")).repository("test")
                    .content(qualityContent).success(true).build()
            );

            String summary = FindingsSummaryFormatter.formatSummary(FindingsExtractor.extractAll(results));

            assertThat(summary).contains("Critical (1)");
            assertThat(summary).contains("Low (1)");
            assertThat(summary).contains("SQL Injection");
            assertThat(summary).contains("Dead Code");
            assertThat(summary).contains("カテゴリー:");
        }

        @Test
        @DisplayName("同一タイトル・優先度の指摘をエージェント横断で統合する")
        void mergesSameFindingAcrossAgents() {
            String content = """
                ### 1. 共通指摘

                | **Priority** | High |
                """;

            var results = List.of(
                ReviewResult.builder()
                    .agentConfig(testAgent("security")).repository("test")
                    .content(content).success(true).build(),
                ReviewResult.builder()
                    .agentConfig(testAgent("performance")).repository("test")
                    .content(content).success(true).build()
            );

            String summary = FindingsSummaryFormatter.formatSummary(FindingsExtractor.extractAll(results));

            assertThat(summary).contains("High (1)");
            assertThat(summary).contains("共通指摘");
            assertThat(summary).contains("指摘元: security Review, performance Review");
        }
    }
}
