package dev.logicojp.reviewer.cli;

import org.assertj.core.api.Assertions;
import com.github.copilot.sdk.CopilotClient;
import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.config.ResilienceConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.orchestrator.ReviewOrchestrator;
import dev.logicojp.reviewer.orchestrator.ReviewOrchestratorFactory;
import dev.logicojp.reviewer.report.ReportGenerator;
import dev.logicojp.reviewer.report.ReportService;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.report.SummaryGenerator;
import dev.logicojp.reviewer.service.TemplateService;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;


@DisplayName("ReviewPipelineExecutor")
class ReviewPipelineExecutorTest {

    @Test
    @DisplayName("noSummary指定時はサマリー生成をスキップして正常終了する")
    void executeSkipsSummaryAndReturnsOk(@TempDir Path tempDir) throws Exception {
        var outputCapture = createOutput();
        var outputDir = tempDir.resolve("reports");
        var checkpointDir = tempDir.resolve("checkpoints");

        var results = List.of(
            reviewResult("agent-a", true, null),
            reviewResult("agent-b", false, "boom")
        );

        try (var orchestrator = new StubReviewOrchestrator(results)) {
            var executor = new ReviewPipelineExecutor(
                executionConfig(checkpointDir),
                null,
                new StubReviewOrchestratorFactory(orchestrator),
                new StubReportService(false),
                outputCapture.output
            );

            int exitCode = executor.execute(
                ReviewTarget.gitHub("owner/repo"),
                new ModelConfig(),
                Map.of("agent-a", agentConfig("agent-a")),
                List.<CustomInstruction>of(),
                outputDir,
                "constraints",
                1,
                true,
                false,
                null
            );

            String stdout = outputCapture.out.toString(StandardCharsets.UTF_8);
            Assertions.assertThat(exitCode).isEqualTo(ExitCodes.OK);
            Assertions.assertThat(stdout).contains("Starting reviews...");
            Assertions.assertThat(stdout).contains("Generating reports...");
            Assertions.assertThat(stdout).contains("Review completed!");
            Assertions.assertThat(stdout).contains("Successful: 1");
            Assertions.assertThat(stdout).contains("Failed: 1");
            Assertions.assertThat(stdout).doesNotContain("Generating executive summary...");
        }
    }

    @Test
    @DisplayName("レポート生成失敗時は警告とチェックポイント案内を表示する")
    void executePrintsWarningWhenReportGenerationFails(@TempDir Path tempDir) throws Exception {
        var outputCapture = createOutput();
        var outputDir = tempDir.resolve("reports");
        var checkpointDir = tempDir.resolve("checkpoints");

        var results = List.of(reviewResult("agent-a", true, null));

        try (var orchestrator = new StubReviewOrchestrator(results)) {
            var executor = new ReviewPipelineExecutor(
                executionConfig(checkpointDir),
                null,
                new StubReviewOrchestratorFactory(orchestrator),
                new StubReportService(true),
                outputCapture.output
            );

            int exitCode = executor.execute(
                ReviewTarget.gitHub("owner/repo"),
                new ModelConfig(),
                Map.of("agent-a", agentConfig("agent-a")),
                List.<CustomInstruction>of(),
                outputDir,
                "constraints",
                1,
                true,
                false,
                null
            );

            String stderr = outputCapture.err.toString(StandardCharsets.UTF_8);
            Assertions.assertThat(exitCode).isEqualTo(ExitCodes.OK);
            Assertions.assertThat(stderr).contains("Warning: Report generation failed:");
            Assertions.assertThat(stderr).contains(checkpointDir.toString());
        }
    }

    private static AgentConfig agentConfig(String name) {
        return new AgentConfig(name, name, "gpt-5", "system", "instruction", "output", List.of("focus"), List.of());
    }

    private static ReviewResult reviewResult(String agentName, boolean success, String errorMessage) {
        return ReviewResult.builder()
            .agentConfig(agentConfig(agentName))
            .repository("owner/repo")
            .content(success ? "ok" : null)
            .success(success)
            .errorMessage(errorMessage)
            .timestamp(Instant.parse("2026-02-24T00:00:00Z"))
            .build();
    }

    private static ExecutionConfig executionConfig(Path checkpointDir) {
        return new ExecutionConfig(
            1, 1, 10, 1, 1, 1, 1, 1,
            1, 1_024, 512, 32, checkpointDir.toString(),
            new ExecutionConfig.SummarySettings(0, 0, 0, 0, 0, 0)
        );
    }

    private static OutputCapture createOutput() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var output = TestCliOutput.create(
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8)
        );
        return new OutputCapture(out, err, output);
    }

    private record OutputCapture(ByteArrayOutputStream out, ByteArrayOutputStream err, CliOutput output) {
    }

    private static final class StubReviewOrchestratorFactory extends ReviewOrchestratorFactory {
        private final ReviewOrchestrator orchestrator;

        StubReviewOrchestratorFactory(ReviewOrchestrator orchestrator) {
            super(null, null, null, null, null);
            this.orchestrator = orchestrator;
        }

        @Override
        public ReviewOrchestrator create(String githubToken,
                                         ExecutionConfig executionConfig,
                                         List<CustomInstruction> customInstructions,
                                         String reasoningEffort,
                                         String outputConstraints) {
            return orchestrator;
        }
    }

    private static final class StubReviewOrchestrator extends ReviewOrchestrator {
        private final List<ReviewResult> results;
        private final CopilotClient client;

        StubReviewOrchestrator(List<ReviewResult> results) {
            this(new CopilotClient(), results);
        }

        private StubReviewOrchestrator(CopilotClient client, List<ReviewResult> results) {
            super(client, new ReviewOrchestrator.Config(
                null,
                null,
                null,
                new ExecutionConfig(1, 1, 10, 1, 1, 1, 1, 1, 1, 1024, 512, 32, "reports/.checkpoints",
                    new ExecutionConfig.SummarySettings(0, 0, 0, 0, 0, 0)),
                List.of(),
                null,
                null,
                null,
                new ResilienceConfig(null, null, null)
            ));
            this.client = client;
            this.results = List.copyOf(results);
        }

        @Override
        public List<ReviewResult> executeReviews(Map<String, AgentConfig> agents, ReviewTarget target) {
            return results;
        }

        @Override
        public void close() {
            super.close();
            client.close();
        }
    }

    private static final class StubReportService extends ReportService {
        private final boolean failReportGeneration;
        private final TemplateService templateService;

        StubReportService(boolean failReportGeneration) {
            this(new TemplateService(new TemplateConfig(null, null, null, null, null, null, null, null)),
                failReportGeneration);
        }

        private StubReportService(TemplateService templateService, boolean failReportGeneration) {
            super(templateService,
                new ExecutionConfig(1, 1, 10, 1, 1, 1, 1, 1, 1, 1024, 512, 32, "reports/.checkpoints",
                    new ExecutionConfig.SummarySettings(0, 0, 0, 0, 0, 0)),
                new ResilienceConfig(null, null, null));
            this.templateService = templateService;
            this.failReportGeneration = failReportGeneration;
        }

        @Override
        public ReportGenerator createReportGenerator(Path outputDirectory) {
            if (!failReportGeneration) {
                return new ReportGenerator(outputDirectory, templateService);
            }
            return new ReportGenerator(outputDirectory, templateService) {
                @Override
                public List<Path> generateReports(List<ReviewResult> results) throws IOException {
                    throw new IOException("simulated report failure");
                }
            };
        }

        @Override
        public SummaryGenerator createSummaryGenerator(Path outputDirectory,
                                                       CopilotClient client,
                                                       String summaryModel,
                                                       String reasoningEffort,
                                                       long timeoutMinutes) {
            throw new UnsupportedOperationException("Summary should not be called in noSummary path");
        }
    }
}
