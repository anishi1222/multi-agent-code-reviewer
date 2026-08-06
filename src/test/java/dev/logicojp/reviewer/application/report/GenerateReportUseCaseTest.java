package dev.logicojp.reviewer.application.report;

import dev.logicojp.reviewer.application.port.inbound.ReportOptions;
import dev.logicojp.reviewer.application.port.inbound.ReportOutput;
import dev.logicojp.reviewer.application.port.outbound.LoadTemplatePort;
import dev.logicojp.reviewer.application.port.outbound.WriteReportPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import dev.logicojp.reviewer.shared.PromptBudget;

@DisplayName("GenerateReportUseCase")
class GenerateReportUseCaseTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("generate は writer port に出力ディレクトリとレポート内容を渡す")
    void generateReportsUsesWriterPort() {
        RecordingWriter writer = new RecordingWriter(tempDir.resolve("reports"));
        GenerateReportUseCase useCase = newUseCase(writer, _ -> Optional.of("summary"));

        ReportOutput output = useCase.generate(
            List.of(result("security", "Security", "review body")),
            new ReportOptions(tempDir, "markdown", true)
        );

        assertThat(writer.capturedBaseDir).hasValue(tempDir);
        assertThat(writer.writes).hasSize(1);
        assertThat(writer.writes.getFirst().filename()).isEqualTo("security-report.md");
        assertThat(writer.writes.getFirst().outputDir()).isEqualTo(tempDir.resolve("reports"));
        assertThat(writer.writes.getFirst().content()).contains("Security", "review body");
        assertThat(output.reportPaths()).containsExactly(tempDir.resolve("reports/security-report.md"));
        assertThat(output.hasSummary()).isFalse();
    }

    @Test
    @DisplayName("generateSummary は AI 要約本文を返す")
    void generateSummaryUsesAiSummaryPort() {
        RecordingWriter writer = new RecordingWriter(tempDir.resolve("reports"));
        GenerateReportUseCase useCase = newUseCase(writer, prompt -> {
            assertThat(prompt).contains("owner/repo", "review body");
            return Optional.of("summary");
        });

        Optional<String> summary = useCase.generateSummary(
            List.of(result("security", "Security", "review body")),
            ReportOptions.defaults(tempDir)
        );

        assertThat(summary).contains("summary");
        assertThat(writer.writes).isEmpty();
    }

    // not ported: timeout/model factory wiring moved out of GenerateReportUseCase; it receives an already configured AI-summary port.

    private GenerateReportUseCase newUseCase(WriteReportPort writer,
                                             dev.logicojp.reviewer.application.port.outbound.GenerateAiSummaryPort aiSummary) {
        return new GenerateReportUseCase(
            writer,
            new InMemoryTemplates(),
            aiSummary,
            new SummaryGenerator.SummaryGenerationConfig(10_000, 20_000, 200, 1_000, 100, 2, new PromptBudget()),
            Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private static ReviewResult result(String name, String displayName, String content) {
        return ReviewResult.builder(Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC))
            .agentConfig(new AgentConfig(name, displayName, "model", "system", "instruction", null, List.of("SQL"), List.of()))
            .repository("owner/repo")
            .content(content)
            .success(true)
            .build();
    }

    private static final class RecordingWriter implements WriteReportPort {
        private final Path outputDir;
        private final AtomicReference<Path> capturedBaseDir = new AtomicReference<>();
        private final List<WriteCall> writes = new ArrayList<>();

        private RecordingWriter(Path outputDir) {
            this.outputDir = outputDir;
        }

        @Override
        public Path write(String content, String filename, Path outputDir) {
            writes.add(new WriteCall(content, filename, outputDir));
            return outputDir.resolve(filename);
        }

        @Override
        public Path createOutputDirectory(Path baseDir) {
            capturedBaseDir.set(baseDir);
            return outputDir;
        }
    }

    private record WriteCall(String content, String filename, Path outputDir) {}

    private static final class InMemoryTemplates implements LoadTemplatePort {
        private final Map<String, String> templates = Map.of(
            "report", "# ${displayName}\n${content}\n${focusAreas}",
            "summary-prompt.md", "Repository: ${repository}\n${results}",
            "summary-result-entry.md", "${displayName}: ${content}\n",
            "summary-result-error-entry.md", "${displayName}: ${errorMessage}\n",
            "fallback-summary.md", "${tableRows}\n${agentSummaries}",
            "fallback-agent-row.md", "| ${displayName} | ${content} |\n",
            "fallback-agent-success.md", "${displayName}: ${content}\n",
            "fallback-agent-failure.md", "${displayName}: ${errorMessage}\n",
            "executive-summary.md", "# Executive Summary\n${date}\n${repository}\n${summaryContent}\n${findingsSummary}\n${reportLinks}",
            "report-link-entry.md", "- [${displayName}](${filename})\n"
        );

        @Override
        public String render(String templateKey, Map<String, String> placeholders) {
            String rendered = loadRaw(templateKey);
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                rendered = rendered.replace("${" + entry.getKey() + "}", entry.getValue());
            }
            return rendered;
        }

        @Override
        public String loadRaw(String templateKey) {
            return templates.get(templateKey);
        }
    }
}
