package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.ExecutionCorrelation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentReviewExecutor")
class AgentReviewExecutorTest {

    private AgentConfig agentConfig() {
        return new AgentConfig(
            "security",
            "Security",
            "model",
            "system",
            "instruction",
            null,
            List.of(),
            List.of()
        );
    }

    private ReviewContext context() {
        return ReviewContext.builder()
            .client(new com.github.copilot.CopilotClient(new com.github.copilot.rpc.CopilotClientOptions()))
            .timeoutMinutes(1)
            .idleTimeoutMinutes(1)
            .maxRetries(0)
            .localFileConfig(new LocalFileConfig())
            .build();
    }

    @Test
    @DisplayName("正常系では単一レビュー結果を返す")
    void returnsSuccessResult() {
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        var context = context();
        var metrics = new OrchestratorMetrics();
        try {
            var executor = new AgentReviewExecutor(
                new Semaphore(1),
                executorService,
                (config, _) -> target -> success(config, target, "ok"),
                metrics
            );

            ReviewResult result = executor.executeAgentSafely(
                agentConfig(),
                ReviewTarget.gitHub("owner/repo"),
                context,
                1
            );

            assertThat(result.success()).isTrue();
            assertThat(result.content()).isEqualTo("ok");
            assertThat(metrics.records()).singleElement()
                .satisfies(record -> {
                    assertThat(record.outcome()).isEqualTo(OrchestratorMetrics.OutcomeType.SUCCESS);
                    assertThat(record.agentName()).isEqualTo("security");
                });
        } finally {
            executorService.close();
            context.client().close();
        }
    }

    @Test
    @DisplayName("実行例外は失敗結果に変換される")
    void mapsExecutionExceptionToFailureResult() {
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        var context = context();
        var metrics = new OrchestratorMetrics();
        try {
            var executor = new AgentReviewExecutor(
                new Semaphore(1),
                executorService,
                (_, _) -> _ -> {
                    throw new IllegalStateException("boom");
                },
                metrics
            );

            ReviewResult result = executor.executeAgentSafely(
                agentConfig(),
                ReviewTarget.gitHub("owner/repo"),
                context,
                1
            );

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("Review failed:");
            assertThat(metrics.records()).singleElement()
                .extracting(OrchestratorMetrics.AgentExecutionRecord::outcome)
                .isEqualTo(OrchestratorMetrics.OutcomeType.FAILURE);
        } finally {
            executorService.close();
            context.client().close();
        }
    }

    @Test
    @DisplayName("agent実行スレッドへexecution IDのMDCが伝播される")
    void propagatesExecutionIdToAgentExecutionThread() {
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        var context = context();
        AtomicReference<String> capturedExecutionId = new AtomicReference<>();
        try {
            var executor = new AgentReviewExecutor(
                new Semaphore(1),
                executorService,
                (config, _) -> target -> {
                    capturedExecutionId.set(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY));
                    return success(config, target, "ok");
                },
                new OrchestratorMetrics()
            );

            ExecutionCorrelation.putExecutionId("exec-agent");
            ReviewResult result = executor.executeAgentSafely(
                agentConfig(),
                ReviewTarget.gitHub("owner/repo"),
                context,
                1
            );

            assertThat(result.success()).isTrue();
            assertThat(capturedExecutionId.get()).isEqualTo("exec-agent");
        } finally {
            ExecutionCorrelation.clearExecutionId();
            executorService.close();
            context.client().close();
        }
    }

    @Test
    @DisplayName("待機中に割り込まれた場合はFutureをキャンセルして失敗結果を返す")
    void cancelsFutureWhenInterruptedDuringWait() {
        var interruptedExecutor = new InterruptedOnGetExecutorService();
        var context = context();
        try {
            var executor = new AgentReviewExecutor(
                new Semaphore(1),
                interruptedExecutor,
                (config, _) -> target -> success(config, target, "ignored"),
                new OrchestratorMetrics()
            );

            ReviewResult result = executor.executeAgentSafely(
                agentConfig(),
                ReviewTarget.gitHub("owner/repo"),
                context,
                1
            );

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("interrupted");
            assertThat(interruptedExecutor.future.cancelCalled).isTrue();
        } finally {
            interruptedExecutor.shutdownNow();
            context.client().close();
            Thread.interrupted();
        }
    }

    private static ReviewResult success(AgentConfig config, ReviewTarget target, String content) {
        return ReviewResult.builder()
            .agentConfig(config)
            .repository(target.displayName())
            .content(content)
            .success(true)
            .timestamp(Instant.now())
            .build();
    }

    private static final class InterruptedOnGetExecutorService extends AbstractExecutorService {
        private final CancelTrackingFuture future = new CancelTrackingFuture();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            @SuppressWarnings("unchecked")
            Future<T> cast = (Future<T>) future;
            return cast;
        }
    }

    private static final class CancelTrackingFuture implements Future<ReviewResult> {
        private boolean cancelCalled;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelCalled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelCalled;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public ReviewResult get() throws InterruptedException {
            throw new InterruptedException("forced interruption");
        }

        @Override
        public ReviewResult get(long timeout, TimeUnit unit) throws InterruptedException {
            throw new InterruptedException("forced interruption");
        }
    }
}
