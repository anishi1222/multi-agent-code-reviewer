package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewExecutionModeRunner")
class ReviewExecutionModeRunnerTest {

    private AgentConfig agent(String name) {
        return new AgentConfig(name, name, "model", "system", "instruction", null, List.of(), List.of());
    }

    @Test
    @DisplayName("asyncモードで結果を収集しmulti-pass時にマージする")
    void executesAsyncAndMerges() {
        ExecutionConfig config = ExecutionConfig.ofFlat(2, 2, 2, 1, 1, 1, 1, 1, 0, 0, 0, 0);
        var pipeline = new ReviewResultPipeline();
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

        try {
            var runner = new ReviewExecutionModeRunner(config, executorService, pipeline);
            var results = runner.executeAsync(
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

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().content()).contains("検出パス: 1, 2");
        } finally {
            executorService.close();
        }
    }

    @Test
    @DisplayName("structuredモードで結果を収集できる")
    void executesStructured() {
        ExecutionConfig config = ExecutionConfig.ofFlat(2, 1, 2, 1, 1, 1, 1, 1, 0, 0, 0, 0);
        var pipeline = new ReviewResultPipeline();
        var runner = new ReviewExecutionModeRunner(config, null, pipeline);

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
        assertThat(results.getFirst().success()).isTrue();
    }
}