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
import java.util.ArrayList;
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
    @DisplayName("reviewPasses > 1 のstructuredモードでパス結果を収集する")
    void executesStructuredAndCollectsRawPassResults() {
        ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(2, 2, 2, 1, 1, 1, 1, 1, 0, 0, 0, 0);
        var pipeline = new ReviewResultPipeline();
        var runner = new ReviewExecutionModeRunner(config, pipeline);
        var results = runner.executeStructured(
            Map.of("security", agent("security")),
            ReviewTarget.gitHub("owner/repo"),
            null,
            (agentConfig, target, context, reviewPasses, perAgentTimeoutMinutes) -> {
                var passResults = new ArrayList<ReviewResult>(reviewPasses);
                for (int pass = 0; pass < reviewPasses; pass++) {
                    passResults.add(ReviewResult.builder()
                        .agentConfig(agentConfig)
                        .repository(target.displayName())
                        .content("""
                            ### 1. SQLインジェクション

                            | 項目 | 内容 |
                            |------|------|
                            | **Priority** | High |
                            | **指摘の概要** | プレースホルダ未使用 |
                            | **該当箇所** | src/A.java L10 |
                            """)
                        .success(true)
                        .timestamp(Instant.now())
                        .build());
                }
                return passResults;
            }
        );

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(ReviewResult::success);
    }

    @Test
    @DisplayName("structuredモードで結果を収集できる")
    void executesStructured() {
        ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(2, 1, 2, 1, 1, 1, 1, 1, 0, 0, 0, 0);
        var pipeline = new ReviewResultPipeline();
        var runner = new ReviewExecutionModeRunner(config, pipeline);

        var results = runner.executeStructured(
            Map.of("security", agent("security")),
            ReviewTarget.gitHub("owner/repo"),
            null,
            (agentConfig, target, context, reviewPasses, perAgentTimeoutMinutes) -> {
                var passResults = new ArrayList<ReviewResult>(reviewPasses);
                for (int pass = 0; pass < reviewPasses; pass++) {
                    passResults.add(ReviewResult.builder()
                        .agentConfig(agentConfig)
                        .repository(target.displayName())
                        .content("ok")
                        .success(true)
                        .timestamp(Instant.now())
                        .build());
                }
                return passResults;
            }
        );

        assertThat(results).hasSize(1);
        assertThat(results).allMatch(ReviewResult::success);
    }

    @Test
    @DisplayName("structuredタスクへexecution IDのMDCが伝播される")
    void propagatesExecutionIdToStructuredTasks() {
        ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(2, 1, 2, 1, 1, 1, 1, 1, 0, 0, 0, 0);
        var pipeline = new ReviewResultPipeline();
        var runner = new ReviewExecutionModeRunner(config, pipeline);
        AtomicReference<String> capturedExecutionId = new AtomicReference<>();

        try {
            ExecutionCorrelation.putExecutionId("exec-structured");
            var results = runner.executeStructured(
                Map.of("security", agent("security")),
                ReviewTarget.gitHub("owner/repo"),
                null,
                (agentConfig, target, context, reviewPasses, perAgentTimeoutMinutes) -> {
                    capturedExecutionId.set(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY));
                    return List.of(ReviewResult.builder()
                        .agentConfig(agentConfig)
                        .repository(target.displayName())
                        .content("ok")
                        .success(true)
                        .timestamp(Instant.now())
                        .build());
                }
            );

            assertThat(results).hasSize(1);
            assertThat(capturedExecutionId.get()).isEqualTo("exec-structured");
            assertThat(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY)).isEqualTo("exec-structured");
        } finally {
            ExecutionCorrelation.clearExecutionId();
        }
    }
}
