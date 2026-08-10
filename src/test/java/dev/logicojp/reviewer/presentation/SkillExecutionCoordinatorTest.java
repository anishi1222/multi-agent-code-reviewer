package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.ExecuteSkillPort;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import dev.logicojp.reviewer.domain.skill.SkillResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SkillExecutionCoordinator")
class SkillExecutionCoordinatorTest {

    @Test
    @DisplayName("成功時はOKを返す")
    void returnsOkOnSuccess() {
        AtomicReference<Map<String, String>> receivedParameters = new AtomicReference<>();
        var coordinator = new SkillExecutionCoordinator(
            new StubExecuteSkillPort((skillId, parameters) -> {
                receivedParameters.set(parameters);
                return SkillResult.success(skillId, "done");
            }),
            cliOutput()
        );

        int exit = coordinator.execute("scan", Map.of("env", "prod"), "token", "gpt-5");

        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(receivedParameters.get()).containsEntry("env", "prod");
    }

    @Test
    @DisplayName("失敗結果時はSOFTWAREを返す")
    void returnsSoftwareOnFailureResult() {
        var coordinator = new SkillExecutionCoordinator(
            new StubExecuteSkillPort((skillId, _) -> SkillResult.failure(skillId, "failure")),
            cliOutput()
        );

        int exit = coordinator.execute("scan", Map.of(), "token", "gpt-5");

        assertThat(exit).isEqualTo(ExitCodes.SOFTWARE);
    }

    @Test
    @DisplayName("実行ポートの例外は呼び出し元へ伝播する")
    void propagatesPortRuntimeException() {
        var coordinator = new SkillExecutionCoordinator(
            new StubExecuteSkillPort((_, _) -> {
                throw new IllegalStateException("boom");
            }),
            cliOutput()
        );

        assertThatThrownBy(() -> coordinator.execute("scan", Map.of(), "token", "gpt-5"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("boom");
    }

    // removed: returnsOkOnSuccess init/shutdown assertions - coordinator-level Copilot client initialization/shutdown was moved behind application/infrastructure ports.
    // removed: callsShutdownOnException - coordinator-level shutdown-on-exception no longer exists; lifecycle is handled outside this presentation class.

    private static CliOutput cliOutput() {
        return new CliOutput(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));
    }

    private record StubExecuteSkillPort(SkillRunner runner) implements ExecuteSkillPort {
        @Override
        public SkillResult execute(String skillId, Map<String, String> parameters) {
            return runner.run(skillId, parameters);
        }

        @Override
        public List<SkillDefinition> listSkills() {
            return List.of();
        }
    }

    @FunctionalInterface
    private interface SkillRunner {
        SkillResult run(String skillId, Map<String, String> parameters);
    }
}
