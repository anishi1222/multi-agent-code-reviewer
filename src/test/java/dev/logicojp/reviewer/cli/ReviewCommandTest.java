package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.util.GitHubTokenResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewCommand")
class ReviewCommandTest {

    private static final ExecutionConfig EXECUTION_CONFIG =
        ExecutionConfig.ofFlat(2, 1, 5, 5, 5, 5, 5, 5, 0, 1024, 256, 32);

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("正常フローで終了コード0を返す")
    void returnsOkOnSuccessfulExecution() {
        AtomicBoolean executeCalled = new AtomicBoolean(false);
        ReviewCommand command = createCommand((agentConfigs, agentDirs, resolvedToken, runRequest) -> {
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
        ReviewCommand command = createCommand((agentConfigs, agentDirs, resolvedToken, runRequest) -> ExitCodes.OK);

        int exit = command.execute(new String[]{"--help"});

        assertThat(exit).isEqualTo(ExitCodes.OK);
    }

    @Test
    @DisplayName("実行中の予期しない例外はSOFTWAREを返す")
    void returnsSoftwareWhenCollaboratorThrowsUnexpectedError() {
        ReviewCommand command = createCommand((agentConfigs, agentDirs, resolvedToken, runRequest) -> {
            throw new IllegalStateException("boom");
        });

        int exit = command.execute(new String[]{
            "--local", tempDir.toString(),
            "--all"
        });

        assertThat(exit).isEqualTo(ExitCodes.SOFTWARE);
    }

    private ReviewCommand createCommand(ExecutionFn executionFn) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CliOutput output = new CliOutput(new PrintStream(out), new PrintStream(err));

        ReviewModelConfigResolver modelConfigResolver = new ReviewModelConfigResolver();
        ReviewOptionsParser optionsParser = new ReviewOptionsParser(EXECUTION_CONFIG);
        ReviewTargetResolver targetResolver = new ReviewTargetResolver(new GitHubTokenResolver(EXECUTION_CONFIG));
        ReviewAgentConfigResolver agentConfigResolver = new ReviewAgentConfigResolver(
            additionalDirs -> List.of(),
            (selection, agentDirs) -> Map.of(
                "security",
                new AgentConfig("security", "Security", "model", "system", "instruction", null, List.of(), List.of())
            )
        );
        ReviewPreparationService preparationService = new ReviewPreparationService(
            (agentConfigs, agentDirs, modelConfig, target, outputDirectory, reviewModel) -> {
            },
            Clock.fixed(Instant.parse("2026-02-19T00:00:00Z"), ZoneOffset.UTC)
        );
        ReviewRunRequestFactory runRequestFactory = new ReviewRunRequestFactory();
        ReviewExecutionCoordinator executionCoordinator = new ReviewExecutionCoordinator(
            resolvedToken -> {
            },
            (resolvedToken, runRequest) -> executionFn.execute(
                runRequest.agentConfigs(),
                List.of(),
                resolvedToken,
                runRequest
            ),
            () -> {
            },
            output
        );

        return new ReviewCommand(
            new ModelConfig(),
            modelConfigResolver,
            optionsParser,
            targetResolver,
            agentConfigResolver,
            preparationService,
            runRequestFactory,
            executionCoordinator,
            output
        );
    }

    @FunctionalInterface
    private interface ExecutionFn {
        int execute(Map<String, AgentConfig> agentConfigs,
                    List<Path> agentDirs,
                    String resolvedToken,
                    ReviewRunExecutor.ReviewRunRequest runRequest);
    }
}