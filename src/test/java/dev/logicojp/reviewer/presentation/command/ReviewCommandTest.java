package dev.logicojp.reviewer.presentation.command;

import dev.logicojp.reviewer.infrastructure.logging.MdcCorrelationAdapter;
import dev.logicojp.reviewer.application.port.inbound.GenerateReportPort;
import dev.logicojp.reviewer.application.port.inbound.LoadAgentPort;
import dev.logicojp.reviewer.application.port.inbound.ReportOutput;
import dev.logicojp.reviewer.application.port.inbound.ReviewRequest;
import dev.logicojp.reviewer.application.port.inbound.RunReviewPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.presentation.CliOutput;
import dev.logicojp.reviewer.presentation.ExitCodes;
import dev.logicojp.reviewer.presentation.ReviewAgentConfigResolver;
import dev.logicojp.reviewer.presentation.ReviewExecutionCoordinator;
import dev.logicojp.reviewer.presentation.ReviewModelConfigResolver;
import dev.logicojp.reviewer.presentation.ReviewPreparationService;
import dev.logicojp.reviewer.presentation.ReviewRunExecutor;
import dev.logicojp.reviewer.presentation.ReviewRunRequestFactory;
import dev.logicojp.reviewer.presentation.ReviewTargetResolver;
import dev.logicojp.reviewer.presentation.formatter.ReviewOutputFormatter;
import dev.logicojp.reviewer.presentation.parser.ReviewOptionsParser;
import dev.logicojp.reviewer.shared.ExecutionCorrelation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewCommand")
class ReviewCommandTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("正常フローで終了コード0を返す")
    void returnsOkOnSuccessfulExecution() {
        AtomicBoolean executeCalled = new AtomicBoolean(false);
        ReviewCommand command = createCommand(request -> {
            executeCalled.set(true);
            return ExitCodes.OK;
        });

        int exit = command.execute(new String[]{
            "--local", tempDir.toString(),
            "--all"
        });

        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(executeCalled.get()).isTrue();
    }

    @Test
    @DisplayName("ヘルプ指定時は終了コード0を返す")
    void returnsOkWhenHelpRequested() {
        ReviewCommand command = createCommand(request -> ExitCodes.OK);

        int exit = command.execute(new String[]{"--help"});

        assertThat(exit).isEqualTo(ExitCodes.OK);
    }

    @Test
    @DisplayName("実行中の予期しない例外はSOFTWAREを返す")
    void returnsSoftwareWhenCollaboratorThrowsUnexpectedError() {
        ReviewCommand command = createCommand(request -> {
            throw new IllegalStateException("boom");
        });

        int exit = command.execute(new String[]{
            "--local", tempDir.toString(),
            "--all"
        });

        assertThat(exit).isEqualTo(ExitCodes.SOFTWARE);
    }

    @Test
    @DisplayName("レビュー実行中のみexecution IDがMDCに設定され終了後にクリアされる")
    void setsExecutionIdOnlyWithinExecutionBoundary() {
        AtomicReference<String> executionId = new AtomicReference<>();
        ReviewCommand command = createCommand(request -> {
            executionId.set(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY));
            return ExitCodes.OK;
        });

        int exit = command.execute(new String[]{
            "--local", tempDir.toString(),
            "--all"
        });

        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(executionId.get()).isNotBlank();
        assertThat(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY)).isNull();
    }

    private ReviewCommand createCommand(ExecutionFn executionFn) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CliOutput output = new CliOutput(new PrintStream(out), new PrintStream(err));
        AgentConfig agent = new AgentConfig(
            "security", "Security", "model", "system", "instruction", null, List.of(), List.of());
        LoadAgentPort loadAgentPort = new LoadAgentPort() {
            @Override
            public List<AgentConfig> loadAll(List<Path> directories) {
                return List.of(agent);
            }

            @Override
            public Optional<AgentConfig> loadByName(String name, List<Path> directories) {
                return "security".equals(name) ? Optional.of(agent) : Optional.empty();
            }
        };

        ReviewModelConfigResolver modelConfigResolver = new ReviewModelConfigResolver(null, null, "summary-model", null);
        ReviewOptionsParser optionsParser = new ReviewOptionsParser(2);
        ReviewTargetResolver targetResolver = new ReviewTargetResolver(githubToken -> Optional.of("token"));
        ReviewAgentConfigResolver agentConfigResolver = new ReviewAgentConfigResolver(loadAgentPort);
        ReviewOutputFormatter outputFormatter = new ReviewOutputFormatter(output, 1);
        ReviewPreparationService preparationService = new ReviewPreparationService(outputFormatter);
        ReviewRunRequestFactory runRequestFactory = new ReviewRunRequestFactory();
        ReviewExecutionCoordinator executionCoordinator = new ReviewExecutionCoordinator(stubExecutor(executionFn, output));

        return new ReviewCommand(
            modelConfigResolver,
            optionsParser,
            targetResolver,
            agentConfigResolver,
            preparationService,
            runRequestFactory,
            executionCoordinator,
            output,
            new MdcCorrelationAdapter()
        );
    }

    private static ReviewRunExecutor stubExecutor(ExecutionFn executionFn, CliOutput output) {
        RunReviewPort runReviewPort = request -> List.of();
        GenerateReportPort generateReportPort = new GenerateReportPort() {
            @Override
            public ReportOutput generate(List<dev.logicojp.reviewer.domain.report.ReviewResult> results,
                                         dev.logicojp.reviewer.application.port.inbound.ReportOptions options) {
                return ReportOutput.of(List.of());
            }

            @Override
            public Optional<String> generateSummary(List<dev.logicojp.reviewer.domain.report.ReviewResult> results,
                                                    dev.logicojp.reviewer.application.port.inbound.ReportOptions options) {
                return Optional.empty();
            }
        };
        return new ReviewRunExecutor(runReviewPort, generateReportPort, new ReviewOutputFormatter(output, 1)) {
            @Override
            public int execute(ReviewRequest request) {
                return executionFn.execute(request);
            }
        };
    }

    @FunctionalInterface
    private interface ExecutionFn {
        int execute(ReviewRequest request);
    }
}
