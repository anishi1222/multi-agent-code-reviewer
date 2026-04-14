package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrchestratorMetrics")
class OrchestratorMetricsTest {

    @Test
    @DisplayName("エージェント実行を記録しスナップショットで集計できる")
    void recordsAgentExecutionsAndProducesSnapshot() {
        var metrics = new OrchestratorMetrics();
        metrics.markRunStart();

        metrics.recordAgentExecution("security", 1200, 50,
            OrchestratorMetrics.OutcomeType.SUCCESS);
        metrics.recordAgentExecution("performance", 800, 10,
            OrchestratorMetrics.OutcomeType.SUCCESS);
        metrics.recordAgentExecution("code-quality", 0, 0,
            OrchestratorMetrics.OutcomeType.TIMEOUT);

        metrics.markRunEnd();

        var snap = metrics.snapshot();
        assertThat(snap.agentCount()).isEqualTo(3);
        assertThat(snap.successCount()).isEqualTo(2);
        assertThat(snap.timeoutCount()).isEqualTo(1);
        assertThat(snap.failureCount()).isZero();
        assertThat(snap.interruptedCount()).isZero();
        assertThat(snap.maxDurationMs()).isEqualTo(1200);
        assertThat(snap.avgDurationMs()).isEqualTo(666); // (1200+800+0)/3
        assertThat(snap.maxPermitWaitMs()).isEqualTo(50);
        assertThat(snap.runDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("記録なしの場合はゼロ値のスナップショットを返す")
    void emptyMetricsProducesZeroSnapshot() {
        var metrics = new OrchestratorMetrics();
        metrics.markRunStart();
        metrics.markRunEnd();

        var snap = metrics.snapshot();
        assertThat(snap.agentCount()).isZero();
        assertThat(snap.successCount()).isZero();
        assertThat(snap.avgDurationMs()).isZero();
        assertThat(snap.maxDurationMs()).isZero();
    }

    @Test
    @DisplayName("logSummaryは例外を投げない")
    void logSummaryDoesNotThrow() {
        var metrics = new OrchestratorMetrics();
        metrics.markRunStart();
        metrics.recordAgentExecution("agent-a", 500, 0,
            OrchestratorMetrics.OutcomeType.SUCCESS);
        metrics.markRunEnd();

        // Must not throw
        metrics.logSummary();
    }

    @Test
    @DisplayName("records()は不変コピーを返す")
    void recordsReturnsImmutableCopy() {
        var metrics = new OrchestratorMetrics();
        metrics.recordAgentExecution("a", 100, 0,
            OrchestratorMetrics.OutcomeType.SUCCESS);

        var copy = metrics.records();
        assertThat(copy).hasSize(1);
        assertThat(copy.getFirst().agentName()).isEqualTo("a");
    }

    // ---- Outcome classification tests ----

    private static AgentConfig agent(String name) {
        return new AgentConfig(name, name, "model", "system", "instruction", null, List.of(), List.of());
    }

    @Test
    @DisplayName("全成功のリストはSUCCESSに分類される")
    void classifiesAllSuccessAsSuccess() {
        var results = List.of(
            ReviewResult.builder().agentConfig(agent("a")).repository("r")
                .content("ok").success(true).timestamp(Instant.now()).build()
        );
        assertThat(OrchestratorMetrics.classifyOutcome(results))
            .isEqualTo(OrchestratorMetrics.OutcomeType.SUCCESS);
    }

    @Test
    @DisplayName("タイムアウトメッセージを含む失敗はTIMEOUTに分類される")
    void classifiesTimeoutFailureAsTimeout() {
        var results = List.of(
            ReviewResult.builder().agentConfig(agent("a")).repository("r")
                .success(false).errorMessage("Review timed out after 5 minutes")
                .timestamp(Instant.now()).build()
        );
        assertThat(OrchestratorMetrics.classifyOutcome(results))
            .isEqualTo(OrchestratorMetrics.OutcomeType.TIMEOUT);
    }

    @Test
    @DisplayName("割り込みメッセージを含む失敗はINTERRUPTEDに分類される")
    void classifiesInterruptedFailureAsInterrupted() {
        var results = List.of(
            ReviewResult.builder().agentConfig(agent("a")).repository("r")
                .success(false).errorMessage("Review interrupted during execution")
                .timestamp(Instant.now()).build()
        );
        assertThat(OrchestratorMetrics.classifyOutcome(results))
            .isEqualTo(OrchestratorMetrics.OutcomeType.INTERRUPTED);
    }

    @Test
    @DisplayName("その他の失敗はFAILUREに分類される")
    void classifiesGenericFailureAsFailure() {
        var results = List.of(
            ReviewResult.builder().agentConfig(agent("a")).repository("r")
                .success(false).errorMessage("Review failed: some error")
                .timestamp(Instant.now()).build()
        );
        assertThat(OrchestratorMetrics.classifyOutcome(results))
            .isEqualTo(OrchestratorMetrics.OutcomeType.FAILURE);
    }

    @Test
    @DisplayName("空リストはFAILUREに分類される")
    void classifiesEmptyListAsFailure() {
        assertThat(OrchestratorMetrics.classifyOutcome(List.of()))
            .isEqualTo(OrchestratorMetrics.OutcomeType.FAILURE);
    }

    @Test
    @DisplayName("INTERRUPTED記録を含むスナップショットの集計が正しい")
    void snapshotCountsInterrupted() {
        var metrics = new OrchestratorMetrics();
        metrics.markRunStart();
        metrics.recordAgentExecution("a", 0, 20,
            OrchestratorMetrics.OutcomeType.INTERRUPTED);
        metrics.recordAgentExecution("b", 300, 0,
            OrchestratorMetrics.OutcomeType.FAILURE);
        metrics.markRunEnd();

        var snap = metrics.snapshot();
        assertThat(snap.interruptedCount()).isEqualTo(1);
        assertThat(snap.failureCount()).isEqualTo(1);
        assertThat(snap.successCount()).isZero();
    }
}
