package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.outbound.PropagateCorrelationPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.review.ReviewContext;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import dev.logicojp.reviewer.infrastructure.logging.MdcCorrelationAdapter;
import dev.logicojp.reviewer.shared.ExecutionCorrelation;
import org.slf4j.MDC;
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

    /// The real MDC-backed adapter, so the propagation test asserts against the actual
    /// logging context rather than a stub that could pass while production loses the ID.
    private static final PropagateCorrelationPort CORRELATION = new MdcCorrelationAdapter();

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
        var runner = new ReviewExecutionModeRunner(config, pipeline, metrics, CORRELATION);
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
        var runner = new ReviewExecutionModeRunner(config, pipeline, metrics, CORRELATION);

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
        var runner = new ReviewExecutionModeRunner(config, pipeline, metrics, CORRELATION);
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

    @Test
    @DisplayName("structuredタスクへexecution IDのMDCが伝播される")
    void propagatesExecutionIdToStructuredTasks() {
        // Regression guard: StructuredTaskScope.fork() runs each subtask on a fresh virtual
        // thread with an empty MDC. Without PropagateCorrelationPort the captured value is null.
        var config = OrchestratorConfig.builder()
            .reviewPasses(1)
            .agentTimeoutMinutes(2)
            .maxRetries(1)
            .build();
        var pipeline = new ReviewResultPipeline();
        var metrics = new OrchestratorMetrics();
        var runner = new ReviewExecutionModeRunner(config, pipeline, metrics, CORRELATION);
        AtomicReference<String> capturedExecutionId = new AtomicReference<>();
        AtomicReference<String> forkThread = new AtomicReference<>();
        String callerThread = Thread.currentThread().getName();

        try {
            CORRELATION.bindExecutionId("exec-structured");

            var results = runner.executeStructured(
                Map.of("security", agent("security")),
                ReviewTarget.gitHub("owner/repo"),
                context(),
                (agentConfig, target, context, reviewPasses, perAgentTimeoutMinutes) -> {
                    forkThread.set(Thread.currentThread().getName());
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
            assertThat(capturedExecutionId.get())
                .as("execution ID must be visible inside the forked subtask")
                .isEqualTo("exec-structured");
            assertThat(forkThread.get())
                .as("the assertion above is only meaningful if the subtask really ran on another thread")
                .isNotEqualTo(callerThread);
            assertThat(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY))
                .as("the orchestrator thread's own context must survive the forks")
                .isEqualTo("exec-structured");
        } finally {
            CORRELATION.clearExecutionId();
        }
    }
}
