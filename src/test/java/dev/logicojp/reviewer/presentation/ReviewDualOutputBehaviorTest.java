package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.ReviewRequest;
import dev.logicojp.reviewer.application.report.GenerateReportUseCase;
import dev.logicojp.reviewer.application.report.SummaryGenerator;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import dev.logicojp.reviewer.infrastructure.file.ReportFileWriter;
import dev.logicojp.reviewer.presentation.formatter.ReviewOutputFormatter;
import dev.logicojp.reviewer.shared.PromptBudget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Executable coverage for OUT-09 across presentation, application, and filesystem boundaries.
@DisplayName("review dual output behavior")
class ReviewDualOutputBehaviorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("レビュー完了時にstdout進捗とレポートファイルの両方を常に出力する")
    void emitsProgressToStdoutAndPersistsReportFile() throws IOException {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CliOutput cliOutput = new CliOutput(
            new PrintStream(stdout, true, StandardCharsets.UTF_8),
            new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );
        AgentConfig agent = agent("security");
        ReviewResult reviewResult = ReviewResult.builder()
            .agentConfig(agent)
            .repository("owner/repo")
            .content("dual-output-review-body")
            .success(true)
            .build();

        GenerateReportUseCase reportUseCase = new GenerateReportUseCase(
            new ReportFileWriter(),
            reportTemplate(),
            _ -> Optional.empty(),
            new SummaryGenerator.SummaryGenerationConfig(
                10_000, 20_000, 200, 1_000, 100, 2, new PromptBudget())
        );
        ReviewRunExecutor executor = new ReviewRunExecutor(
            _ -> List.of(reviewResult),
            reportUseCase,
            new ReviewOutputFormatter(cliOutput)
        );

        int exitCode = executor.execute(new ReviewRequest(
            ReviewTarget.gitHub("owner/repo"),
            List.of(agent),
            1,
            tempDir,
            List.of(),
            null,
            false,
            "token",
            "2026-08-09T00:00:00Z",
            null,
            false,
            true
        ));

        List<Path> reportFiles;
        try (Stream<Path> paths = Files.walk(tempDir)) {
            reportFiles = paths
                .filter(path -> path.getFileName().toString().equals("security-report.md"))
                .toList();
        }

        assertThat(exitCode).isEqualTo(ExitCodes.OK);
        assertThat(stdout.toString(StandardCharsets.UTF_8))
            .contains("Review completed!", "Total agents: 1", "Successful: 1", "Reports:");
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(reportFiles).hasSize(1);
        assertThat(Files.readString(reportFiles.getFirst()))
            .contains("security", "dual-output-review-body");
    }

    private static AgentConfig agent(String name) {
        return new AgentConfig(name, name, "model", "system", "instruction", null, List.of(), List.of());
    }

    private static dev.logicojp.reviewer.application.port.outbound.LoadTemplatePort reportTemplate() {
        return new dev.logicojp.reviewer.application.port.outbound.LoadTemplatePort() {
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
}
