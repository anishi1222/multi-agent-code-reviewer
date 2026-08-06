package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.domain.report.ReviewResult;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/// Thread-safe metrics collector for a single orchestration run.
///
/// Purified from {@code orchestrator.OrchestratorMetrics}:
/// - Replaced SLF4J with {@code java.util.logging}.
/// - Fixed {@code ReviewResult} import to {@code domain.report.ReviewResult}.
public final class OrchestratorMetrics {

    static final String METRIC_AGENT_DURATION_MS = "orchestrator.agent.duration_ms";
    static final String METRIC_AGENT_PERMIT_WAIT_MS = "orchestrator.agent.permit_wait_ms";
    static final String METRIC_AGENT_OUTCOME = "orchestrator.agent.outcome";
    static final String METRIC_RUN_DURATION_MS = "orchestrator.run.duration_ms";
    static final String METRIC_RUN_AGENT_COUNT = "orchestrator.run.agent_count";

    enum OutcomeType {
        SUCCESS, FAILURE, TIMEOUT, INTERRUPTED
    }

    record AgentExecutionRecord(
        String agentName,
        long durationMs,
        long permitWaitMs,
        OutcomeType outcome
    ) {}

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

    private static final Logger logger = Logger.getLogger(OrchestratorMetrics.class.getName());
    private static final long PERMIT_WAIT_LOG_THRESHOLD_MS = 50;

    private final ConcurrentLinkedQueue<AgentExecutionRecord> records = new ConcurrentLinkedQueue<>();
    private volatile long runStartNanos;
    private volatile long runEndNanos;

    public void markRunStart() {
        runStartNanos = System.nanoTime();
    }

    public void markRunEnd() {
        runEndNanos = System.nanoTime();
    }

    public void recordAgentExecution(String agentName, long durationMs, long permitWaitMs, OutcomeType outcome) {
        records.add(new AgentExecutionRecord(agentName, durationMs, permitWaitMs, outcome));
        if (permitWaitMs > PERMIT_WAIT_LOG_THRESHOLD_MS) {
            logger.info(() -> "Agent '" + agentName + "' waited " + permitWaitMs + "ms for concurrency permit");
        }
    }

    public void recordSuccess(String agentName, long durationMs, long permitWaitMs) {
        recordAgentExecution(agentName, durationMs, permitWaitMs, OutcomeType.SUCCESS);
    }

    public void recordFailure(String agentName, long durationMs, long permitWaitMs) {
        recordAgentExecution(agentName, durationMs, permitWaitMs, OutcomeType.FAILURE);
    }

    public void recordTimeout(String agentName, long durationMs, long permitWaitMs) {
        recordAgentExecution(agentName, durationMs, permitWaitMs, OutcomeType.TIMEOUT);
    }

    public void recordInterrupted(String agentName, long durationMs, long permitWaitMs) {
        recordAgentExecution(agentName, durationMs, permitWaitMs, OutcomeType.INTERRUPTED);
    }

    public Snapshot snapshot() {
        List<AgentExecutionRecord> all = List.copyOf(records);
        long runMs = nanosToMillis(runEndNanos - runStartNanos);
        int total = all.size();
        long successCount = all.stream().filter(r -> r.outcome() == OutcomeType.SUCCESS).count();
        long failureCount = all.stream().filter(r -> r.outcome() == OutcomeType.FAILURE).count();
        long timeoutCount = all.stream().filter(r -> r.outcome() == OutcomeType.TIMEOUT).count();
        long interruptedCount = all.stream().filter(r -> r.outcome() == OutcomeType.INTERRUPTED).count();
        long avgDuration = total == 0 ? 0 : all.stream().mapToLong(AgentExecutionRecord::durationMs).sum() / total;
        long maxDuration = total == 0 ? 0 : all.stream().mapToLong(AgentExecutionRecord::durationMs).max().orElse(0);
        long avgWait = total == 0 ? 0 : all.stream().mapToLong(AgentExecutionRecord::permitWaitMs).sum() / total;
        long maxWait = total == 0 ? 0 : all.stream().mapToLong(AgentExecutionRecord::permitWaitMs).max().orElse(0);
        return new Snapshot(runMs, total, (int) successCount, (int) failureCount,
            (int) timeoutCount, (int) interruptedCount, avgDuration, maxDuration, avgWait, maxWait);
    }

    public void logSummary() {
        Snapshot snap = snapshot();
        logger.info(() -> "Run summary: %d agents, %d success, %d failure, %d timeout, duration=%dms"
            .formatted(snap.agentCount(), snap.successCount(), snap.failureCount(),
                snap.timeoutCount(), snap.runDurationMs()));
    }

    public static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }
}
