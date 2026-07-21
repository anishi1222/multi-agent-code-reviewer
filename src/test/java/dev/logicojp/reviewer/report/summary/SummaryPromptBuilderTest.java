package dev.logicojp.reviewer.report.summary;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.finding.FindingsExtractor;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.PromptBudgetConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.service.TemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SummaryPromptBuilder")
class SummaryPromptBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("成功結果は最大サイズまで連結され超過分は切り詰められる")
    void truncatesAndLimitsPromptContent() throws IOException {
        TemplateService templateService = createTemplateService();
        var builder = new SummaryPromptBuilder(templateService, 20, 25, 8192, 4096);

        var result1 = successResult("A", "123456789012345678901234567890");
        var result2 = successResult("B", "abcdefghij");

        String prompt = builder.buildSummaryPrompt(List.of(result1, result2), "owner/repo");

        assertThat(prompt).contains("repo=owner/repo");
        assertThat(prompt).contains("A:12345678901234567890");
        assertThat(prompt).contains("... (truncated for summary)");
        assertThat(prompt).doesNotContain("B:abcdefghij");
    }

    @Test
    @DisplayName("失敗結果はエントリとして出力される")
    void includesErrorEntries() throws IOException {
        TemplateService templateService = createTemplateService();
        var builder = new SummaryPromptBuilder(templateService, 50, 200, 8192, 4096);

        var errorResult = new ReviewResult(
            agent("security", "Security"),
            "owner/repo",
            null,
            Instant.now(),
            false,
            "timeout"
        );

        String prompt = builder.buildSummaryPrompt(List.of(errorResult), "owner/repo");

        assertThat(prompt).contains("ERR:Security:timeout");
    }

    @Test
    @DisplayName("compact prompt有効時は構造化された指摘のみを要約入力へ含める")
    void buildsCompactPromptFromFindingBlocks() throws IOException {
        TemplateService templateService = createTemplateService();
        var budget = new PromptBudgetConfig(true, 100, 100, 100, 100, 500, 1000, 100);
        var builder = new SummaryPromptBuilder(templateService, 1000, 2000, 8192, 4096, budget);
        String content = """
            ### 1. SQL injection

            | 項目 | 内容 |
            |------|------|
            | **Priority** | High |
            | **指摘の概要** | Query concatenates user input |
            | **該当箇所** | src/App.java L10 |

            **推奨対応**

            Use parameterized queries.

            **効果**

            Safer queries.
            """;

        String prompt = builder.buildSummaryPrompt(List.of(successResult("A", content)), "owner/repo");

        assertThat(prompt).contains("SQL injection");
        assertThat(prompt).contains("Priority: High");
        assertThat(prompt).contains("Summary: Query concatenates user input");
        assertThat(prompt).doesNotContain("Safer queries.");
    }

    @Test
    @DisplayName("compact promptでもGood Pointsを保持する")
    void compactPromptPreservesGoodPoints() throws IOException {
        TemplateService templateService = createTemplateService();
        var budget = new PromptBudgetConfig(true, 100, 100, 100, 100, 600, 1200, 150);
        var builder = new SummaryPromptBuilder(templateService, 1000, 2000, 8192, 4096, budget);
        String content = """
            ### Good Points

            - **Parameterized queries**: `src/A.java` L10 で安全なプレースホルダーを使用。

            ### 改善点

            ### [1]. Validation issue

            | 項目 | 内容 |
            |------|------|
            | **Priority** | High |
            | **指摘の概要** | Missing validation |
            | **該当箇所** | src/B.java L20 |
            """;

        String prompt = builder.buildSummaryPrompt(
            List.of(successResult("Security", content)),
            "owner/repo"
        );

        assertThat(prompt).contains("### Good Points");
        assertThat(prompt).contains("Parameterized queries");
        assertThat(prompt).contains("Validation issue");
    }

    @Test
    @DisplayName("構造化フィールドのないfinding blockは本文の抜粋を保持する")
    void preservesFallbackBodyForUnstructuredFindingBlock() throws IOException {
        TemplateService templateService = createTemplateService();
        var budget = new PromptBudgetConfig(true, 100, 100, 100, 100, 500, 1000, 100);
        var builder = new SummaryPromptBuilder(templateService, 1000, 2000, 8192, 4096, budget);
        String content = """
            ### 1. レビュー結果

            指摘事項なし
            """;

        String prompt = builder.buildSummaryPrompt(List.of(successResult("A", content)), "owner/repo");

        assertThat(prompt).contains("レビュー結果");
        assertThat(prompt).contains("指摘事項なし");
    }

    @Test
    @DisplayName("複数エージェントの重複指摘を統合した一覧をプロンプトへ含める")
    void includesDeduplicatedFindingsAcrossAgents() throws IOException {
        TemplateService templateService = createTemplateService();
        var builder = new SummaryPromptBuilder(templateService, 1000, 4000, 8192, 4096);
        String content = """
            ### 1. Shared issue

            | 項目 | 内容 |
            |------|------|
            | **Priority** | High |
            """;

        String prompt = builder.buildSummaryPrompt(
            List.of(successResult("Security", content), successResult("Quality", content)),
            "owner/repo"
        );

        assertThat(prompt).contains("#### High (1)");
        assertThat(prompt).contains("指摘元: Security, Quality");
    }

    private TemplateService createTemplateService() throws IOException {
        Files.writeString(
            tempDir.resolve("summary-prompt.md"),
            "repo=${repository}\nfindings=${findingsSummary}\n${results}"
        );
        Files.writeString(tempDir.resolve("summary-result-entry.md"), "${displayName}:${content}\n");
        Files.writeString(tempDir.resolve("summary-result-error-entry.md"), "ERR:${displayName}:${errorMessage}\n");

        var config = new TemplateConfig(
            tempDir.toString(),
            "default-output-format.md",
            "report.md",
            "local-review-content.md",
            "output-constraints.md",
            null,
            "report-link-entry.md",
            new TemplateConfig.SummaryTemplates(
                "summary-system.md",
                "summary-prompt.md",
                "executive-summary.md",
                "summary-result-entry.md",
                "summary-result-error-entry.md"
            ),
            new TemplateConfig.FallbackTemplates(
                "fallback-summary.md",
                "fallback-agent-row.md",
                "fallback-agent-success.md",
                "fallback-agent-failure.md"
            )
        );
        return new TemplateService(config);
    }

    private ReviewResult successResult(String name, String content) {
        return new ReviewResult(
            agent(name, name),
            "owner/repo",
            content,
            Instant.now(),
            true,
            null
        );
    }

    private AgentConfig agent(String name, String displayName) {
        return AgentConfig.builder()
            .name(name)
            .displayName(displayName)
            .build();
    }
}