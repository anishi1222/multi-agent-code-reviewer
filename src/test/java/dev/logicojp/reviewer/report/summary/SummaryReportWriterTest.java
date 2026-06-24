package dev.logicojp.reviewer.report.summary;

import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.formatter.SummaryFinalReportFormatter;
import dev.logicojp.reviewer.service.TemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SummaryReportWriter")
class SummaryReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("outputDirectoryがinvocation timestampの場合は親ディレクトリへsummaryを書き出す")
    void writesSummaryToParentWhenOutputDirectoryIsInvocationTimestamp() throws IOException {
        prepareTemplateFiles(tempDir);
        Path invocationDirectory = tempDir.resolve("2026-06-24-14-00-00");
        Files.createDirectories(invocationDirectory);

        SummaryReportWriter writer = new SummaryReportWriter(
            invocationDirectory,
            "2026-06-24-14-00-00",
            new SummaryFinalReportFormatter(templateService(tempDir))
        );

        Path result = writer.write("summary body", "owner/repo", List.of(successResult()));

        assertThat(writer.resolveSummaryOutputDirectory()).isEqualTo(tempDir);
        assertThat(result).isEqualTo(tempDir.resolve("executive_summary_2026-06-24-14-00-00.md"));
        assertThat(Files.readString(result)).contains("summary body").contains("owner/repo");
    }

    @Test
    @DisplayName("通常のoutputDirectoryの場合はそのディレクトリへsummaryを書き出す")
    void writesSummaryToConfiguredOutputDirectory() throws IOException {
        prepareTemplateFiles(tempDir);
        Path reportDirectory = tempDir.resolve("reports");

        SummaryReportWriter writer = new SummaryReportWriter(
            reportDirectory,
            "2026-06-24-14-00-00",
            new SummaryFinalReportFormatter(templateService(tempDir))
        );

        Path result = writer.write("summary body", "owner/repo", List.of(successResult()));

        assertThat(writer.resolveSummaryOutputDirectory()).isEqualTo(reportDirectory);
        assertThat(result).isEqualTo(reportDirectory.resolve("executive_summary_2026-06-24-14-00-00.md"));
        assertThat(Files.exists(result)).isTrue();
    }

    private static ReviewResult successResult() {
        return ReviewResult.builder()
            .agentConfig(new AgentConfig("security", "Security", "model", "system", "instruction", null, List.of(), List.of()))
            .repository("owner/repo")
            .content("content")
            .success(true)
            .build();
    }

    private static TemplateService templateService(Path baseDir) {
        return new TemplateService(new TemplateConfig(
            baseDir.toString(),
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
        ));
    }

    private static void prepareTemplateFiles(Path baseDir) throws IOException {
        Files.writeString(baseDir.resolve("executive-summary.md"),
            "# Executive Summary\n\n" +
                "- Repository: ${repository}\n\n" +
                "${summaryContent}\n\n" +
                "${reportLinks}\n"
        );
        Files.writeString(baseDir.resolve("report-link-entry.md"),
            "- [${displayName}](${filename})\n"
        );
    }
}
