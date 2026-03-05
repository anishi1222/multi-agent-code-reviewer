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

@DisplayName("FallbackSummaryBuilder")
class FallbackSummaryBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("成功と失敗の結果をテンプレートに従って組み立てる")
    void buildsFallbackSummaryWithSuccessAndFailure() throws IOException {
        TemplateService templateService = createTemplateService();
        var builder = new FallbackSummaryBuilder(templateService, 10, 3);

        var success = new ReviewResult(
            agent("code", "Code"),
            "owner/repo",
            "line1\nline2   line3",
            Instant.now(),
            true,
            null
        );
        var failure = new ReviewResult(
            agent("security", "Security"),
            "owner/repo",
            null,
            Instant.now(),
            false,
            "api error"
        );

        String summary = builder.buildFallbackSummary(List.of(success, failure));

        assertThat(summary).contains("ROW:Code:line1 line...");
        assertThat(summary).contains("ROW:Security:N/A");
        assertThat(summary).contains("OK:Code:line1 line...");
        assertThat(summary).contains("NG:Security:api error");
    }

    private TemplateService createTemplateService() throws IOException {
        Files.writeString(tempDir.resolve("fallback-summary.md"), "TABLE\n${tableRows}\nSUM\n${agentSummaries}");
        Files.writeString(tempDir.resolve("fallback-agent-row.md"), "ROW:${displayName}:${content}\n");
        Files.writeString(tempDir.resolve("fallback-agent-success.md"), "OK:${displayName}:${content}\n");
        Files.writeString(tempDir.resolve("fallback-agent-failure.md"), "NG:${displayName}:${errorMessage}\n");

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

    private AgentConfig agent(String name, String displayName) {
        return AgentConfig.builder()
            .name(name)
            .displayName(displayName)
            .build();
    }
}