package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.report.core.ReviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/// Thread-safe metrics collector for a single orchestration run.
///
/// Captures per-agent execution latency, concurrency permit wait time,
/// and outcome classification (success / failure / timeout / interrupted).
///
/// Metric name constants are defined here so that any future telemetry
/// backend (Micrometer, OpenTelemetry, etc.) can reference them without
/// changing call sites.
///
/// All recording methods are safe to call from concurrent virtual threads.
final class OrchestratorMetrics {

    // ---- Metric name constants (define before choosing backends) ----

    static final String METRIC_AGENT_DURATION_MS = "orchestrator.agent.duration_ms";
    static final String METRIC_AGENT_PERMIT_WAIT_MS = "orchestrator.agent.permit_wait_ms";
    static final String METRIC_AGENT_OUTCOME = "orchestrator.agent.outcome";
    static final String METRIC_RUN_DURATION_MS = "orchestrator.run.duration_ms";
    static final String METRIC_RUN_AGENT_COUNT = "orchestrator.run.agent_count";

    // ---- Outcome classification ----

    enum OutcomeType {
        SUCCESS, FAILURE, TIMEOUT, INTERRUPTED
    }

    // ---- Per-agent execution record ----

    record AgentExecutionRecord(
        String agentName,
        long durationMs,
        long permitWaitMs,
        OutcomeType outcome
    ) {}

    // ---- Aggregate snapshot ----

    record Snapshot(
        long runDurationMs,
        int agentCount,
        int successCount,
        int failureCount,
        int timeoutCount,
        int interruptedCount,
        long avgDurationMs,
        long maxDurationMs,
        long avgPermitWaitMs,
        long maxPermitWaitMs
    ) {}

    // ---- Internal state ----

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorMetrics.class);
    /// Threshold above which a permit wait is considered "backpressure" and logged at INFO.
    private static final long PERMIT_WAIT_LOG_THRESHOLD_MS = 50;

    private final ConcurrentLinkedQueue<AgentExecutionRecord> records = new ConcurrentLinkedQueue<>();
    private volatile long runStartNanos;
    private volatile long runEndNanos;

    // ---- Run lifecycle ----

    void markRunStart() {
        this.runStartNanos = System.nanoTime();
    }

    void markRunEnd() {
        this.runEndNanos = System.nanoTime();
    }

    // ---- Per-agent recording ----

    void recordAgentExecution(String agentName, long durationMs, long permitWaitMs, OutcomeType outcome) {
        records.add(new AgentExecutionRecord(agentName, durationMs, permitWaitMs, outcome));
        logger.info("[{}={}] [{}={}] [{}={}] agent={}",
            METRIC_AGENT_DURATION_MS, durationMs,
            METRIC_AGENT_PERMIT_WAIT_MS, permitWaitMs,
            METRIC_AGENT_OUTCOME, outcome,
            agentName);
    }

    /// Logs a backpressure event if the permit wait exceeds the threshold.
    void logPermitWaitIfSignificant(String agentName, long permitWaitMs) {
        if (permitWaitMs >= PERMIT_WAIT_LOG_THRESHOLD_MS) {
            logger.info("Backpressure: agent {} waited {}ms for concurrency permit",
                agentName, permitWaitMs);
        }
    }

    // ---- Outcome classification ----

    static OutcomeType classifyOutcome(List<ReviewResult> results) {
        if (results.isEmpty()) {
            return OutcomeType.FAILURE;
        }
        boolean allSuccess = true;
        boolean hasTimeout = false;
        boolean hasInterrupted = false;
        for (ReviewResult r : results) {
            if (!r.success()) {
                allSuccess = false;
                String msg = r.errorMessage();
                if (msg != null) {
                    if (msg.contains("timed out")) hasTimeout = true;
                    if (msg.contains("interrupted")) hasInterrupted = true;
                }
            }
        }
        if (allSuccess) {
            return OutcomeType.SUCCESS;
        }
        if (hasTimeout) {
            return OutcomeType.TIMEOUT;
        }
        return hasInterrupted ? OutcomeType.INTERRUPTED : OutcomeType.FAILURE;
    }

    // ---- Snapshot / summary ----

    Snapshot snapshot() {
        long runDuration = nanosToMillis(runEndNanos - runStartNanos);
        int count = 0;
        int success = 0;
        int failure = 0;
        int timeout = 0;
        int interrupted = 0;
        long totalDuration = 0;
        long maxDuration = 0;
        long totalWait = 0;
        long maxWait = 0;

        for (var r : records) {
            count++;
            switch (r.outcome()) {
                case SUCCESS -> success++;
                case FAILURE -> failure++;
                case TIMEOUT -> timeout++;
                case INTERRUPTED -> interrupted++;
            }
            totalDuration += r.durationMs();
            maxDuration = Math.max(maxDuration, r.durationMs());
            totalWait += r.permitWaitMs();
            maxWait = Math.max(maxWait, r.permitWaitMs());
        }

        return new Snapshot(
            runDuration,
            count,
            success, failure, timeout, interrupted,
            count > 0 ? totalDuration / count : 0,
            maxDuration,
            count > 0 ? totalWait / count : 0,
            maxWait
        );
    }

    void logSummary() {
        var s = snapshot();
        logger.info("[{}={}] [{}={}] "
                + "outcomes=[success={}, failure={}, timeout={}, interrupted={}] "
                + "duration_ms=[avg={}, max={}] permit_wait_ms=[avg={}, max={}]",
            METRIC_RUN_DURATION_MS, s.runDurationMs(),
            METRIC_RUN_AGENT_COUNT, s.agentCount(),
            s.successCount(), s.failureCount(), s.timeoutCount(), s.interruptedCount(),
            s.avgDurationMs(), s.maxDurationMs(),
            s.avgPermitWaitMs(), s.maxPermitWaitMs());
    }

    // ---- Accessors for testing ----

    List<AgentExecutionRecord> records() {
        return List.copyOf(new ArrayList<>(records));
    }

    // ---- Helpers ----

    static long nanosToMillis(long nanos) {
        return nanos / 1_000_000;
    }
}
