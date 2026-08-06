package dev.logicojp.reviewer.infrastructure.file;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.report.SummaryFinalReportFormatter;
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
        Path invocationDirectory = tempDir.resolve("2026-06-24-14-00-00");
        Files.createDirectories(invocationDirectory);

        SummaryReportWriter writer = new SummaryReportWriter(
            invocationDirectory,
            "2026-06-24-14-00-00",
            summaryFinalReportFormatter()
        );

        Path result = writer.write("summary body", "owner/repo", List.of(successResult()), "findings");

        assertThat(writer.resolveSummaryOutputDirectory()).isEqualTo(tempDir);
        assertThat(result).isEqualTo(tempDir.resolve("executive_summary_2026-06-24-14-00-00.md"));
        assertThat(Files.readString(result)).contains("summary body").contains("owner/repo").contains("findings");
    }

    @Test
    @DisplayName("通常のoutputDirectoryの場合はそのディレクトリへsummaryを書き出す")
    void writesSummaryToConfiguredOutputDirectory() throws IOException {
        Path reportDirectory = tempDir.resolve("reports");

        SummaryReportWriter writer = new SummaryReportWriter(
            reportDirectory,
            "2026-06-24-14-00-00",
            summaryFinalReportFormatter()
        );

        Path result = writer.write("summary body", "owner/repo", List.of(successResult()), "findings");

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

    private static SummaryFinalReportFormatter summaryFinalReportFormatter() {
        return new SummaryFinalReportFormatter(
            """
                # Executive Summary

                - Repository: ${repository}

                ${summaryContent}

                ${findingsSummary}

                ${reportLinks}
                """,
            "- [${displayName}](${filename})\n"
        );
    }
}
