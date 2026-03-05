package dev.logicojp.reviewer.report.summary;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.finding.FindingsExtractor;

import dev.logicojp.reviewer.agent.AgentConfig;
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

    private TemplateService createTemplateService() throws IOException {
        Files.writeString(tempDir.resolve("summary-prompt.md"), "repo=${repository}\n${results}");
        Files.writeString(tempDir.resolve("summary-result-entry.md"), "${displayName}:${content}\n");
        Files.writeString(tempDir.resolve("summary-result-error-entry.md"), "ERR:${displayName}:${errorMessage}\n");

        var config = new TemplateConfig(
            tempDir.toString(),
            "default-output-format.md",
            "report.md",
            "local-review-content.md",
            "output-constraints.md",
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