package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReviewExecutionCoordinator")
class ReviewExecutionCoordinatorTest {

    @Test
    @DisplayName("agent設定が空の場合はSOFTWAREを返し初期化を呼ばない")
    void returnsSoftwareWhenNoAgentsFound() {
        AtomicBoolean initialized = new AtomicBoolean(false);
        AtomicBoolean executed = new AtomicBoolean(false);
        AtomicBoolean shutdown = new AtomicBoolean(false);

        var coordinator = new ReviewExecutionCoordinator(
            token -> initialized.set(true),
            (resolvedToken, request) -> {
                executed.set(true);
                return ExitCodes.OK;
            },
            () -> shutdown.set(true),
            new CliOutput(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()))
        );

        int exit = coordinator.execute(
            Map.of(),
            List.of(Path.of("agents")),
            "token",
            sampleRequest(Map.of())
        );

        assertThat(exit).isEqualTo(ExitCodes.SOFTWARE);
        assertThat(initialized.get()).isFalse();
        assertThat(executed.get()).isFalse();
        assertThat(shutdown.get()).isFalse();
    }

    @Test
    @DisplayName("実行後はshutdownが呼ばれ終了コードを返す")
    void executesAndShutsDown() {
        AtomicInteger initCount = new AtomicInteger();
        AtomicInteger shutdownCount = new AtomicInteger();

        var coordinator = new ReviewExecutionCoordinator(
            token -> initCount.incrementAndGet(),
            (resolvedToken, request) -> ExitCodes.OK,
            shutdownCount::incrementAndGet,
            new CliOutput(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()))
        );

        int exit = coordinator.execute(
            Map.of("a", agentConfig("a")),
            List.of(Path.of("agents")),
            "token",
            sampleRequest(Map.of("a", agentConfig("a")))
        );

        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(initCount.get()).isEqualTo(1);
        assertThat(shutdownCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("実行で例外発生時もshutdownされる")
    void shutsDownWhenExecutionFails() {
        AtomicInteger shutdownCount = new AtomicInteger();

        var coordinator = new ReviewExecutionCoordinator(
            token -> {
            },
            (resolvedToken, request) -> {
                throw new IllegalStateException("boom");
            },
            shutdownCount::incrementAndGet,
            new CliOutput(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()))
        );

        assertThatThrownBy(() -> coordinator.execute(
            Map.of("a", agentConfig("a")),
            List.of(Path.of("agents")),
            "token",
            sampleRequest(Map.of("a", agentConfig("a")))
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("boom");

        assertThat(shutdownCount.get()).isEqualTo(1);
    }

    private static ReviewRunExecutor.ReviewRunRequest sampleRequest(Map<String, AgentConfig> agentConfigs) {
        return new ReviewRunExecutor.ReviewRunRequest(
            ReviewTarget.gitHub("owner/repo"),
            "summary-model",
            "high",
            agentConfigs,
            2,
            false,
            Path.of("./reports/owner/repo")
        );
    }

    private static AgentConfig agentConfig(String name) {
        return new AgentConfig(name, name, "gpt-5", "prompt", "instruction", "", List.of(), List.of());
    }
}
