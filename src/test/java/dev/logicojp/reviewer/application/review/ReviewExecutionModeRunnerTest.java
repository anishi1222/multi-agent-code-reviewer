package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.review.ReviewContext;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        var config = OrchestratorConfig.builder()
            .reviewPasses(2)
            .agentTimeoutMinutes(2)
            .maxRetries(1)
            .build();
        var pipeline = new ReviewResultPipeline();
        var metrics = new OrchestratorMetrics();
        var runner = new ReviewExecutionModeRunner(config, pipeline, metrics);
        var results = runner.executeStructured(
            Map.of("security", agent("security")),
            ReviewTarget.gitHub("owner/repo"),
            context(),
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
        var config = OrchestratorConfig.builder()
            .reviewPasses(1)
            .agentTimeoutMinutes(2)
            .maxRetries(1)
            .build();
        var pipeline = new ReviewResultPipeline();
        var metrics = new OrchestratorMetrics();
        var runner = new ReviewExecutionModeRunner(config, pipeline, metrics);

        var results = runner.executeStructured(
            Map.of("security", agent("security")),
            ReviewTarget.gitHub("owner/repo"),
            context(),
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
    @DisplayName("structuredタスクへ共有ReviewContextが渡される")
    void passesSharedContextToStructuredTasks() {
        var config = OrchestratorConfig.builder()
            .reviewPasses(1)
            .agentTimeoutMinutes(2)
            .maxRetries(1)
            .build();
        var pipeline = new ReviewResultPipeline();
        var metrics = new OrchestratorMetrics();
        var runner = new ReviewExecutionModeRunner(config, pipeline, metrics);
        var sharedContext = ReviewContext.builder()
            .reasoningEffort("high")
            .outputConstraints("strict output")
            .build();
        AtomicReference<ReviewContext> capturedContext = new AtomicReference<>();

        var results = runner.executeStructured(
            Map.of("security", agent("security")),
            ReviewTarget.gitHub("owner/repo"),
            sharedContext,
            (agentConfig, target, context, reviewPasses, perAgentTimeoutMinutes) -> {
                capturedContext.set(context);
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
        assertThat(capturedContext.get()).isSameAs(sharedContext);
    }

    private ReviewContext context() {
        return ReviewContext.builder().build();
    }

    // removed: propagatesExecutionIdToStructuredTasks because MDC execution-id propagation no longer exists; ReviewExecutionModeRunner explicitly removed MDC handling.
}
