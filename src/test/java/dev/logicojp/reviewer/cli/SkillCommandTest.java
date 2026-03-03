package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.config.SkillConfig;
import dev.logicojp.reviewer.service.CopilotCliHealthChecker;
import dev.logicojp.reviewer.service.CopilotCliPathResolver;
import dev.logicojp.reviewer.service.CopilotClientStarter;
import dev.logicojp.reviewer.service.CopilotService;
import dev.logicojp.reviewer.service.CopilotStartupErrorFormatter;
import dev.logicojp.reviewer.service.CopilotTimeoutResolver;
import dev.logicojp.reviewer.service.SkillService;
import dev.logicojp.reviewer.skill.SkillRegistry;
import dev.logicojp.reviewer.util.FeatureFlags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SkillCommand")
class SkillCommandTest {

    @FunctionalInterface
    private interface PreparationFn {
        SkillExecutionPreparation.PreparationResult prepare(SkillCommand.ParsedOptions options);
    }

    @FunctionalInterface
    private interface ExecuteFn {
        int execute(String skillId, Map<String, String> parameters, String resolvedToken, String model, long timeoutMinutes);
    }

    private static final ExecutionConfig EXECUTION_CONFIG =
        ExecutionConfig.ofFlat(2, 1, 5, 5, 5, 5, 5, 5, 0, 1024, 256, 32);

    @Test
    @DisplayName("正常フローで終了コード0を返す")
    void returnsOkOnSuccessfulExecution() {
        SkillCommand command = createCommand(
            options -> new SkillExecutionPreparation.PreparationResult(false, "token", Map.of("k", "v")),
            (skillId, parameters, resolvedToken, model, timeoutMinutes) -> ExitCodes.OK
        );

        int exit = command.execute(new String[]{"secret-scan"});

        assertThat(exit).isEqualTo(ExitCodes.OK);
    }

    @Test
    @DisplayName("ヘルプ指定時は終了コード0を返す")
    void returnsOkWhenHelpRequested() {
        SkillCommand command = createCommand(
            options -> new SkillExecutionPreparation.PreparationResult(true, null, Map.of()),
            (skillId, parameters, resolvedToken, model, timeoutMinutes) -> ExitCodes.OK
        );

        int exit = command.execute(new String[]{"--help"});

        assertThat(exit).isEqualTo(ExitCodes.OK);
    }

    @Test
    @DisplayName("実行中の予期しない例外はSOFTWAREを返す")
    void returnsSoftwareWhenCollaboratorThrowsUnexpectedError() {
        SkillCommand command = createCommand(
            options -> {
                throw new IllegalStateException("boom");
            },
            (skillId, parameters, resolvedToken, model, timeoutMinutes) -> ExitCodes.OK
        );

        int exit = command.execute(new String[]{"secret-scan"});

        assertThat(exit).isEqualTo(ExitCodes.SOFTWARE);
    }

    private SkillCommand createCommand(PreparationFn preparationFunction,
                                       ExecuteFn executorFunction) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CliOutput output = new CliOutput(new PrintStream(out), new PrintStream(err));

        SkillService skillService = new SkillService(
            new SkillRegistry(),
            new CopilotService(
                new CopilotCliPathResolver(),
                new CopilotCliHealthChecker(new CopilotTimeoutResolver()),
                new CopilotTimeoutResolver(),
                new CopilotStartupErrorFormatter(),
                new CopilotClientStarter()
            ),
            new GithubMcpConfig(null, null, null, null, null, null),
            EXECUTION_CONFIG,
            SkillConfig.defaults(),
            new FeatureFlags(false, false)
        );

        SkillExecutionPreparation preparation = new SkillExecutionPreparation(
            additionalAgentDirs -> Map.of(),
            agents -> {
            },
            githubToken -> java.util.Optional.of("token"),
            skillId -> true
        ) {
            @Override
            public PreparationResult prepare(SkillCommand.ParsedOptions options) {
                return preparationFunction.prepare(options);
            }
        };

        SkillExecutionCoordinator coordinator = new SkillExecutionCoordinator(
            resolvedToken -> {
            },
            (skillId, parameters, resolvedToken, model, timeoutMinutes) -> {
                return null;
            },
            () -> {
            },
            output
        ) {
            @Override
            public int execute(String skillId,
                               Map<String, String> parameters,
                               String resolvedToken,
                               String model,
                               long timeoutMinutes) {
                return executorFunction.execute(skillId, parameters, resolvedToken, model, timeoutMinutes);
            }
        };

        return new SkillCommand(
            skillService,
            EXECUTION_CONFIG,
            preparation,
            coordinator,
            new SkillOptionsParser(),
            new SkillOutputFormatter(output),
            output
        );
    }
}