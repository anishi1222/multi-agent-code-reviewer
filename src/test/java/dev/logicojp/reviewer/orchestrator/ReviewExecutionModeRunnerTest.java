package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.ExecutionCorrelation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewExecutionModeRunner")
class ReviewExecutionModeRunnerTest {

    private AgentConfig agent(String name) {
        return new AgentConfig(name, name, "model", "system", "instruction", null, List.of(), List.of());
    }

    @Test
    @DisplayName("エージェントごとに単一結果を収集する")
    void executesStructuredReviews() {
        ExecutionConfig config =
            dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(2, 1, 2, 1, 1, 1, 1, 1, 0, 0, 0, 0);
        var runner = new ReviewExecutionModeRunner(
            config,
            new ReviewResultPipeline(),
            new OrchestratorMetrics()
        );

        List<ReviewResult> results = runner.executeStructured(
            Map.of("security", agent("security"), "quality", agent("quality")),
            ReviewTarget.gitHub("owner/repo"),
            null,
            (agentConfig, target, _, _) -> success(agentConfig, target)
        );

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(ReviewResult::success);
        assertThat(results).extracting(result -> result.agentConfig().name())
            .containsExactlyInAnyOrder("security", "quality");
    }

    @Test
    @DisplayName("structuredタスクへexecution IDのMDCが伝播される")
    void propagatesExecutionIdToStructuredTasks() {
        ExecutionConfig config =
            dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(2, 1, 2, 1, 1, 1, 1, 1, 0, 0, 0, 0);
        var runner = new ReviewExecutionModeRunner(
            config,
            new ReviewResultPipeline(),
            new OrchestratorMetrics()
        );
        AtomicReference<String> capturedExecutionId = new AtomicReference<>();

        try {
            ExecutionCorrelation.putExecutionId("exec-structured");
            List<ReviewResult> results = runner.executeStructured(
                Map.of("security", agent("security")),
                ReviewTarget.gitHub("owner/repo"),
                null,
                (agentConfig, target, _, _) -> {
                    capturedExecutionId.set(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY));
                    return success(agentConfig, target);
                }
            );

            assertThat(results).hasSize(1);
            assertThat(capturedExecutionId.get()).isEqualTo("exec-structured");
            assertThat(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY)).isEqualTo("exec-structured");
        } finally {
            ExecutionCorrelation.clearExecutionId();
        }
    }

    private ReviewResult success(AgentConfig config, ReviewTarget target) {
        return ReviewResult.builder()
            .agentConfig(config)
            .repository(target.displayName())
            .content("ok")
            .success(true)
            .timestamp(Instant.now())
            .build();
    }
}
