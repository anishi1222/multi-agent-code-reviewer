package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.GenerateReportPort;
import dev.logicojp.reviewer.application.port.inbound.ReportOutput;
import dev.logicojp.reviewer.application.port.inbound.ReviewRequest;
import dev.logicojp.reviewer.application.port.inbound.RunReviewPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import dev.logicojp.reviewer.presentation.ReviewAgentConfigResolver.AgentResolution;
import dev.logicojp.reviewer.presentation.formatter.ReviewOutputFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReviewExecutionCoordinator")
class ReviewExecutionCoordinatorTest {

    @Test
    @DisplayName("agent設定が空の場合はバリデーションエラーを返し実行しない")
    void returnsSoftwareWhenNoAgentsFound() {
        AtomicBoolean executed = new AtomicBoolean(false);
        var coordinator = new ReviewExecutionCoordinator(stubExecutor(request -> {
            executed.set(true);
            return ExitCodes.OK;
        }));

        assertThatThrownBy(() -> coordinator.execute(
            sampleRequest(List.of(agentConfig("a"))),
            new AgentResolution(List.of(Path.of("agents")), Map.of())
        ))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("No agents found");
        assertThat(executed.get()).isFalse();
    }

    @Test
    @DisplayName("実行後は終了コードを返す")
    void executesAndShutsDown() {
        AtomicBoolean executed = new AtomicBoolean(false);
        AgentConfig agent = agentConfig("a");
        var coordinator = new ReviewExecutionCoordinator(stubExecutor(request -> {
            executed.set(true);
            return ExitCodes.OK;
        }));

        int exit = coordinator.execute(
            sampleRequest(List.of(agent)),
            new AgentResolution(List.of(Path.of("agents")), Map.of("a", agent))
        );

        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(executed.get()).isTrue();
    }

    @Test
    @DisplayName("実行で例外発生時は例外を伝搬する")
    void shutsDownWhenExecutionFails() {
        AgentConfig agent = agentConfig("a");
        var coordinator = new ReviewExecutionCoordinator(stubExecutor(request -> {
            throw new IllegalStateException("boom");
        }));

        assertThatThrownBy(() -> coordinator.execute(
            sampleRequest(List.of(agent)),
            new AgentResolution(List.of(Path.of("agents")), Map.of("a", agent))
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("boom");
    }

    private static ReviewRunExecutor stubExecutor(ExecutorFn fn) {
        CliOutput output = new CliOutput(
            new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(new ByteArrayOutputStream())
        );
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
                return fn.execute(request);
            }
        };
    }

    private static ReviewRequest sampleRequest(List<AgentConfig> agents) {
        return new ReviewRequest(
            ReviewTarget.gitHub("owner/repo"),
            agents,
            2,
            Path.of("./reports/owner/repo"),
            List.of(),
            null,
            false,
            "token",
            "2026-03-05-12-34-56",
            "high",
            false,
            false
        );
    }

    private static AgentConfig agentConfig(String name) {
        return new AgentConfig(name, name, "gpt-5", "prompt", "instruction", "", List.of(), List.of());
    }

    @FunctionalInterface
    private interface ExecutorFn {
        int execute(ReviewRequest request);
    }
}
