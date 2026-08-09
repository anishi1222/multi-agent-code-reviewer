package dev.logicojp.reviewer.presentation.command;

import dev.logicojp.reviewer.application.port.inbound.ExecuteSkillPort;
import dev.logicojp.reviewer.application.port.inbound.LoadAgentPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import dev.logicojp.reviewer.domain.skill.SkillResult;
import dev.logicojp.reviewer.presentation.CliOutput;
import dev.logicojp.reviewer.presentation.ExitCodes;
import dev.logicojp.reviewer.presentation.SkillExecutionCoordinator;
import dev.logicojp.reviewer.presentation.SkillExecutionPreparation;
import dev.logicojp.reviewer.presentation.SkillOptions;
import dev.logicojp.reviewer.presentation.formatter.SkillOutputFormatter;
import dev.logicojp.reviewer.presentation.parser.SkillOptionsParser;
import dev.logicojp.reviewer.domain.agent.AgentSourceDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SkillCommand")
class SkillCommandTest {

    @FunctionalInterface
    private interface PreparationFn {
        SkillExecutionPreparation.PreparationResult prepare(SkillOptions options);
    }

    @FunctionalInterface
    private interface ExecuteFn {
        int execute(String skillId, Map<String, String> parameters, String resolvedToken, String model);
    }

    @Test
    @DisplayName("正常フローで終了コード0を返す")
    void returnsOkOnSuccessfulExecution() {
        SkillCommand command = createCommand(
            options -> new SkillExecutionPreparation.PreparationResult(false, "token", Map.of("k", "v")),
            (skillId, parameters, resolvedToken, model) -> ExitCodes.OK
        );

        int exit = command.execute(new String[]{"secret-scan"});

        assertThat(exit).isEqualTo(ExitCodes.OK);
    }

    @Test
    @DisplayName("ヘルプ指定時は終了コード0を返す")
    void returnsOkWhenHelpRequested() {
        SkillCommand command = createCommand(
            options -> new SkillExecutionPreparation.PreparationResult(true, null, Map.of()),
            (skillId, parameters, resolvedToken, model) -> ExitCodes.OK
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
            (skillId, parameters, resolvedToken, model) -> ExitCodes.OK
        );

        int exit = command.execute(new String[]{"secret-scan"});

        assertThat(exit).isEqualTo(ExitCodes.SOFTWARE);
    }

    private SkillCommand createCommand(PreparationFn preparationFunction,
                                       ExecuteFn executorFunction) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CliOutput output = new CliOutput(new PrintStream(out), new PrintStream(err));
        ExecuteSkillPort executeSkillPort = new ExecuteSkillPort() {
            @Override
            public SkillResult execute(String skillId, Map<String, String> parameters) {
                return SkillResult.success(skillId, "ok");
            }

            @Override
            public List<SkillDefinition> listSkills() {
                return List.of(SkillDefinition.of("secret-scan", "Secret Scan", "scan", "prompt"));
            }
        };

        SkillExecutionPreparation preparation = new SkillExecutionPreparation(
            stubLoadAgentPort(),
            executeSkillPort,
            githubToken -> Optional.of("token")
        ) {
            @Override
            public PreparationResult prepare(SkillOptions options) {
                return preparationFunction.prepare(options);
            }
        };

        SkillExecutionCoordinator coordinator = new SkillExecutionCoordinator(executeSkillPort, output) {
            @Override
            public int execute(String skillId,
                               Map<String, String> parameters,
                               String resolvedToken,
                               String model) {
                return executorFunction.execute(skillId, parameters, resolvedToken, model);
            }
        };

        return new SkillCommand(
            executeSkillPort,
            preparation,
            coordinator,
            new SkillOptionsParser(),
            new SkillOutputFormatter(output),
            output
        );
    }

    private static LoadAgentPort stubLoadAgentPort() {
        return new LoadAgentPort() {
            @Override
            public List<AgentConfig> loadAll(List<AgentSourceDirectory> directories) {
                return List.of();
            }

            @Override
            public Optional<AgentConfig> loadByName(String name, List<AgentSourceDirectory> directories) {
                return Optional.empty();
            }
        };
    }
}
