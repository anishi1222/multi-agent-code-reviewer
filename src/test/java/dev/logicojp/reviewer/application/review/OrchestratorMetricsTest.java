package dev.logicojp.reviewer.application.review;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("SUCCESS/FAILURE/TIMEOUT/INTERRUPTEDのショートカット記録を集計できる")
    void convenienceRecordersProduceExpectedSnapshotCounts() {
        var metrics = new OrchestratorMetrics();
        metrics.markRunStart();
        metrics.recordSuccess("success", 100, 0);
        metrics.recordFailure("failure", 200, 10);
        metrics.recordTimeout("timeout", 300, 20);
        metrics.recordInterrupted("interrupted", 400, 30);
        metrics.markRunEnd();

        var snap = metrics.snapshot();
        assertThat(snap.agentCount()).isEqualTo(4);
        assertThat(snap.successCount()).isEqualTo(1);
        assertThat(snap.failureCount()).isEqualTo(1);
        assertThat(snap.timeoutCount()).isEqualTo(1);
        assertThat(snap.interruptedCount()).isEqualTo(1);
        assertThat(snap.maxDurationMs()).isEqualTo(400);
        assertThat(snap.maxPermitWaitMs()).isEqualTo(30);
    }

    @Test
    @DisplayName("ナノ秒をミリ秒へ変換できる")
    void convertsNanosToMillis() {
        assertThat(OrchestratorMetrics.nanosToMillis(1_999_999L)).isEqualTo(1L);
        assertThat(OrchestratorMetrics.nanosToMillis(2_000_000L)).isEqualTo(2L);
    }

    // removed: recordsReturnsImmutableCopy because records() accessor no longer exists; metrics now exposes only aggregate Snapshot values.
    // removed: classifiesAllSuccessAsSuccess because classifyOutcome(...) no longer exists; outcome classification moved into AgentReviewExecutor's execution flow.
    // removed: classifiesTimeoutFailureAsTimeout because classifyOutcome(...) no longer exists; timeout results are now recorded by executor timeout handling.
    // removed: classifiesInterruptedFailureAsInterrupted because classifyOutcome(...) no longer exists; interrupted results are now recorded by executor interruption handling.
    // removed: classifiesGenericFailureAsFailure because classifyOutcome(...) no longer exists; failures are now recorded directly by executor handling.
    // removed: classifiesEmptyListAsFailure because classifyOutcome(...) no longer exists; empty-result classification is no longer exposed.
    // removed: snapshotCountsInterrupted because convenienceRecordersProduceExpectedSnapshotCounts covers interrupted snapshot aggregation with the current API.
}
