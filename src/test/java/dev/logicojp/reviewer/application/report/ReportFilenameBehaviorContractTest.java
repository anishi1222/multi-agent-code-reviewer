package dev.logicojp.reviewer.application.report;

import dev.logicojp.reviewer.application.port.inbound.ReportOptions;
import dev.logicojp.reviewer.application.port.outbound.LoadTemplatePort;
import dev.logicojp.reviewer.application.port.outbound.WriteReportPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.shared.PromptBudget;
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

import static org.assertj.core.api.Assertions.assertThat;

/// Executable coverage for OUT-03 with the OUT-02 single-pass form as a negative control.
@DisplayName("report filename behavior contract")
class ReportFilenameBehaviorContractTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("複数passは連番付きで単一passは従来名で出力する")
    void numbersMultiPassReportsAndKeepsSinglePassFilename() {
        RecordingWriter writer = new RecordingWriter();
        GenerateReportUseCase useCase = new GenerateReportUseCase(
            writer,
            reportTemplate(),
            _ -> Optional.empty(),
            new SummaryGenerator.SummaryGenerationConfig(
                10_000, 20_000, 200, 1_000, 100, 2, new PromptBudget()),
            Clock.fixed(Instant.parse("2026-08-09T00:00:00Z"), ZoneOffset.UTC)
        );

        useCase.generate(
            List.of(
                result("security", 1),
                result("security", 2),
                result("style", 0)),
            new ReportOptions(tempDir, "markdown", true)
        );

        assertThat(writer.filenames)
            .containsExactly(
                "security-pass-1-report.md",
                "security-pass-2-report.md",
                "style-report.md");
    }

    private static ReviewResult result(String agentName, int passNumber) {
        AgentConfig agent = new AgentConfig(
            agentName, agentName, "model", "system", "instruction", null, List.of(), List.of());
        return ReviewResult.builder()
            .agentConfig(agent)
            .repository("owner/repo")
            .content("review body")
            .success(true)
            .passNumber(passNumber)
            .build();
    }

    private static LoadTemplatePort reportTemplate() {
        return new LoadTemplatePort() {
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
                return "# ${displayName}\n${content}\n";
            }
        };
    }

    private final class RecordingWriter implements WriteReportPort {
        private final List<String> filenames = new ArrayList<>();

        @Override
        public Path write(String content, String filename, Path outputDir) {
            filenames.add(filename);
            return outputDir.resolve(filename);
        }

        @Override
        public Path createOutputDirectory(Path baseDir) {
            return baseDir;
        }
    }
}
