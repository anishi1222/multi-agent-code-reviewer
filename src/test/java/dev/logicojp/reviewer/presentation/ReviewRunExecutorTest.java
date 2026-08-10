package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.GenerateReportPort;
import dev.logicojp.reviewer.application.port.inbound.ReportOptions;
import dev.logicojp.reviewer.application.port.inbound.ReportOutput;
import dev.logicojp.reviewer.application.port.inbound.ReviewRequest;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import dev.logicojp.reviewer.presentation.formatter.ReviewOutputFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewRunExecutor")
class ReviewRunExecutorTest {

    @Test
    @DisplayName("noSummary=true の場合はレポート生成へサマリースキップを伝える")
    void passesNoSummaryToReportGeneration() {
        ReviewRunExecutor executor = new ReviewRunExecutor(
            request -> List.of(successResult("agent-a", request.target().displayName())),
            new StubGenerateReportPort((results, options) -> {
                assertThat(options.skipSummary()).isTrue();
                return ReportOutput.of(List.of(options.outputDir().resolve("agent-a-report.md")));
            }),
            formatter()
        );

        int exitCode = executor.execute(request(true, Path.of("reports")));

        assertThat(exitCode).isEqualTo(ExitCodes.OK);
    }

    @Test
    @DisplayName("全エージェント成功時はOKを返し完了サマリーを表示する")
    void returnsOkAndPrintsCompletionSummaryWhenAllAgentsSucceed() {
        AtomicBoolean reportCalled = new AtomicBoolean(false);
        AtomicReference<Path> reportOutputDirectory = new AtomicReference<>();
        ReviewRunExecutor executor = new ReviewRunExecutor(
            request -> List.of(successResult("agent-a", request.target().displayName())),
            new StubGenerateReportPort((results, options) -> {
                reportCalled.set(true);
                reportOutputDirectory.set(options.outputDir());
                return new ReportOutput(List.of(options.outputDir().resolve("agent-a-report.md")), "summary");
            }),
            formatter()
        );

        Path outputDirectory = Path.of("reports");
        int exitCode = executor.execute(request(false, outputDirectory));

        assertThat(exitCode).isEqualTo(ExitCodes.OK);
        assertThat(reportCalled.get()).isTrue();
        assertThat(reportOutputDirectory.get()).isEqualTo(outputDirectory);
    }

    // removed: cleansUpCheckpointsDirectoryOnExit - .checkpoints cleanup behavior no longer exists in the presentation layer after report output moved behind GenerateReportPort.

    private static ReviewRequest request(boolean noSummary, Path outputDirectory) {
        return new ReviewRequest(
            ReviewTarget.gitHub("owner/repo"),
            List.of(agent("agent-a")),
            1,
            outputDirectory,
            List.of(),
            null,
            false,
            "token",
            "2026-03-05-12-34-56",
            "high",
            false,
            noSummary
        );
    }

    private record StubGenerateReportPort(ReportGenerator generator) implements GenerateReportPort {
        @Override
        public ReportOutput generate(List<ReviewResult> results, ReportOptions options) {
            return generator.generate(results, options);
        }

        @Override
        public Optional<String> generateSummary(List<ReviewResult> results, ReportOptions options) {
            return Optional.empty();
        }
    }

    @FunctionalInterface
    private interface ReportGenerator {
        ReportOutput generate(List<ReviewResult> results, ReportOptions options);
    }

    private static ReviewOutputFormatter formatter() {
        CliOutput cliOutput = new CliOutput(
            new PrintStream(OutputStream.nullOutputStream()),
            new PrintStream(OutputStream.nullOutputStream())
        );
        return new ReviewOutputFormatter(cliOutput);
    }

    private static AgentConfig agent(String agentName) {
        return new AgentConfig(agentName, agentName, "model", "system", "instruction", null, List.of(), List.of());
    }

    private static ReviewResult successResult(String agentName, String repository) {
        return ReviewResult.builder()
            .agentConfig(agent(agentName))
            .repository(repository)
            .content("ok")
            .success(true)
            .build();
    }
}
