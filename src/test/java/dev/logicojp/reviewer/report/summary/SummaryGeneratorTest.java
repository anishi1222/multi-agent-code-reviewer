package dev.logicojp.reviewer.report.summary;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.finding.FindingsExtractor;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.SummaryConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.service.TemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SummaryGenerator")
class SummaryGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("AI要約ビルダーの結果でサマリーファイルを生成できる")
    void generatesSummaryUsingInjectedAiBuilder() throws IOException {
        prepareTemplateFiles(tempDir);
        TemplateService templateService = new TemplateService(createConfig(tempDir));

        SummaryGenerator generator = SummaryGenerator.builder(
                tempDir,
                null,
                "summary-model",
                templateService
            )
            .reasoningEffort("high")
            .timeoutMinutes(1)
            .summaryConfig(new SummaryConfig(0, 0, 0, 0, 0, 0))
            .collaborators(new SummaryGenerator.SummaryCollaborators(
                null,
                null,
                null,
                (results, repository) -> "AI summary content"
            ))
            .clock(Clock.fixed(Instant.parse("2026-02-19T20:30:44Z"), ZoneId.of("UTC")))
            .build();

        List<ReviewResult> results = List.of(
            ReviewResult.builder()
                .agentConfig(new AgentConfig("security", "Security", "model", "system", "instruction", null, List.of(), List.of()))
                .repository("owner/repo")
                .content("### 1. 指摘\n| Priority | High |")
                .success(true)
                .build()
        );

        Path summaryPath = generator.generateSummary(results, "owner/repo");

        assertThat(Files.exists(summaryPath)).isTrue();
        assertThat(summaryPath.getFileName().toString()).isEqualTo("executive_summary_2026-02-19-20-30-44.md");
        String content = Files.readString(summaryPath);
        assertThat(content).contains("AI summary content");
        assertThat(content).contains("owner/repo");
    }

    private static TemplateConfig createConfig(Path baseDir) {
        return new TemplateConfig(
            baseDir.toString(),
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
    }

    private static void prepareTemplateFiles(Path baseDir) throws IOException {
        Files.writeString(baseDir.resolve("executive-summary.md"),
            "# Executive Summary\n\n" +
                "- Date: {{date}}\n" +
                "- Repository: {{repository}}\n\n" +
                "{{summaryContent}}\n\n" +
                "{{reportLinks}}\n"
        );
        Files.writeString(baseDir.resolve("report-link-entry.md"),
            "- [{{displayName}}]({{filename}})\n"
        );
    }
}
