package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.domain.agent.DialogueRound;
import dev.logicojp.reviewer.application.port.outbound.LoadTemplatePort;
import dev.logicojp.reviewer.application.port.outbound.PropagateCorrelationPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.report.ReviewResultFactory;
import dev.logicojp.reviewer.domain.review.ReviewContext;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import dev.logicojp.reviewer.infrastructure.logging.MdcCorrelationAdapter;
import dev.logicojp.reviewer.shared.ExecutionCorrelation;
import org.slf4j.MDC;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentReviewExecutor")
class AgentReviewExecutorTest {

    /// The real MDC-backed adapter, so the propagation test asserts against the actual
    /// logging context rather than a stub that could pass while production loses the ID.
    private static final PropagateCorrelationPort CORRELATION = new MdcCorrelationAdapter();

    private AgentConfig agentConfig() {
        return new AgentConfig("security", "Security", "model", "system", "instruction", null, List.of(), List.of());
    }

    private ReviewContext context() {
        return ReviewContext.builder()
            .maxRetries(0)
            .build();
    }

    private AgentReviewExecutor executor(Semaphore semaphore,
                                         java.util.concurrent.ExecutorService executorService,
                                         ReviewPassRunner reviewPassRunner,
                                         OrchestratorMetrics metrics) {
        return new AgentReviewExecutor(semaphore, executorService, reviewPassRunner,
            unusedRubberDuckRunner(), metrics, CORRELATION);
    }

    private ReviewPassRunner reviewPassRunnerReturning(String content) {
        return new ReviewPassRunner(_ -> content, new ReviewResultFactory());
    }

    private RubberDuckDialogueRunner unusedRubberDuckRunner() {
        return new RubberDuckDialogueRunner(_ -> List.of(), new LoadTemplatePort() {
            @Override
            public String render(String templateKey, Map<String, String> placeholders) {
                return "";
            }

            @Override
            public String loadRaw(String templateKey) {
                return "";
            }
        }, new ReviewResultFactory());
    }

    @Test
    @DisplayName("正常系ではレビュー結果を返す")
    void returnsSuccessResult() {
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        var ctx = context();
        var metrics = new OrchestratorMetrics();
        try {
            var executor = executor(
                new Semaphore(1),
                executorService,
                reviewPassRunnerReturning("ok"),
                metrics
            );

            var results = executor.executeAgentPassesSafely(
                agentConfig(),
                ReviewTarget.gitHub("owner/repo"),
                ctx,
                2,
                1,
                List.of(),
                0
            );

            assertThat(results).hasSize(2);
            assertThat(results).allSatisfy(result -> {
                assertThat(result.success()).isTrue();
                assertThat(result.content()).isEqualTo("ok");
            });

            var snapshot = metrics.snapshot();
            assertThat(snapshot.agentCount()).isEqualTo(1);
            assertThat(snapshot.successCount()).isEqualTo(1);
            assertThat(snapshot.failureCount()).isZero();
        } finally {
            executorService.close();
        }
    }

    @Test
    @DisplayName("実行例外は失敗結果に変換される")
    void mapsExecutionExceptionToFailureResult() {
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        var ctx = context();
        var metrics = new OrchestratorMetrics();
        try {
            var executor = executor(
                new Semaphore(1),
                executorService,
                new ReviewPassRunner(_ -> {
                    throw new IllegalStateException("boom");
                }, new ReviewResultFactory()),
                metrics
            );

            var results = executor.executeAgentPassesSafely(
                agentConfig(),
                ReviewTarget.gitHub("owner/repo"),
                ctx,
                2,
                1,
                List.of(),
                0
            );

            assertThat(results).hasSize(2);
            assertThat(results).allSatisfy(result -> {
                assertThat(result.success()).isFalse();
                assertThat(result.errorMessage()).contains("boom");
            });

            var snapshot = metrics.snapshot();
            assertThat(snapshot.agentCount()).isEqualTo(1);
            assertThat(snapshot.successCount()).isZero();
            assertThat(snapshot.failureCount()).isEqualTo(1);
        } finally {
            executorService.close();
        }
    }

    // removed: reusesSingleReviewerInstanceForAllPasses — AgentReviewer instances no longer exist; AgentReviewExecutor now delegates all passes to a pre-constructed ReviewPassRunner.

    // removed: propagatesExecutionIdToAgentExecutionThread — shared-layer ExecutionCorrelation intentionally omits SLF4J MDC propagation; logging correlation moved out of AgentReviewExecutor.

    @Test
    @DisplayName("待機中に割り込まれた場合は実行Futureをキャンセルして失敗結果を返す")
    void cancelsFutureWhenInterruptedDuringWait() {
        var interruptedExecutor = new InterruptedOnGetExecutorService();
        var ctx = context();
        var metrics = new OrchestratorMetrics();
        try {
            var executor = executor(
                new Semaphore(1),
                interruptedExecutor,
                reviewPassRunnerReturning("ignored"),
                metrics
            );

            var results = executor.executeAgentPassesSafely(
                agentConfig(),
                ReviewTarget.gitHub("owner/repo"),
                ctx,
                1,
                1,
                List.of(),
                0
            );

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().success()).isFalse();
            assertThat(results.getFirst().errorMessage()).contains("interrupted");
            assertThat(interruptedExecutor.future.cancelCalled).isTrue();
        } finally {
            interruptedExecutor.shutdownNow();
            Thread.interrupted();
        }
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

    private static final class CancelTrackingFuture implements Future<List<ReviewResult>> {
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
        public List<ReviewResult> get() throws InterruptedException {
            throw new InterruptedException("forced interruption");
        }

        @Override
        public List<ReviewResult> get(long timeout, TimeUnit unit) throws InterruptedException {
            throw new InterruptedException("forced interruption");
        }
    }

    @Test
    @DisplayName("agent実行スレッドへexecution IDのMDCが伝播される")
    void propagatesExecutionIdToAgentExecutionThread() {
        // Regression guard: the submitted task runs on a *different* virtual thread, whose MDC
        // starts empty. Without PropagateCorrelationPort the captured value is null and every
        // agent log line loses the execution ID that ties it to the CLI invocation.
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        AtomicReference<String> capturedExecutionId = new AtomicReference<>();
        AtomicReference<String> callerThread = new AtomicReference<>();
        AtomicReference<String> workerThread = new AtomicReference<>();
        try {
            var executor = executor(
                new Semaphore(1),
                executorService,
                new ReviewPassRunner(_ -> {
                    workerThread.set(Thread.currentThread().getName());
                    capturedExecutionId.set(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY));
                    return "ok";
                }, new ReviewResultFactory()),
                new OrchestratorMetrics()
            );

            CORRELATION.bindExecutionId("exec-agent");
            callerThread.set(Thread.currentThread().getName());

            var results = executor.executeAgentPassesSafely(
                agentConfig(),
                ReviewTarget.gitHub("owner/repo"),
                context(),
                1,
                1,
                List.of(),
                0
            );

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().success()).isTrue();
            assertThat(capturedExecutionId.get())
                .as("execution ID must be visible inside the agent execution thread")
                .isEqualTo("exec-agent");
            assertThat(workerThread.get())
                .as("the assertion above is only meaningful if the work really crossed a thread boundary")
                .isNotEqualTo(callerThread.get());
            assertThat(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY))
                .as("the caller's own context must be untouched")
                .isEqualTo("exec-agent");
        } finally {
            CORRELATION.clearExecutionId();
            executorService.close();
        }
    }

    @Test
    @DisplayName("rubber-duck実行スレッドへもexecution IDのMDCが伝播される")
    void propagatesExecutionIdToRubberDuckThread() {
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        AtomicReference<String> capturedExecutionId = new AtomicReference<>();
        try {
            var executor = new AgentReviewExecutor(
                new Semaphore(1),
                executorService,
                reviewPassRunnerReturning("unused"),
                new RubberDuckDialogueRunner(_ -> {
                    capturedExecutionId.set(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY));
                    return List.of(new DialogueRound(1, "model-a", "a", "model-b", "b"));
                }, new LoadTemplatePort() {
                    @Override
                    public String render(String templateKey, Map<String, String> placeholders) {
                        return "";
                    }

                    @Override
                    public String loadRaw(String templateKey) {
                        return "";
                    }
                }, new ReviewResultFactory()),
                new OrchestratorMetrics(),
                CORRELATION
            );

            CORRELATION.bindExecutionId("exec-duck");
            var results = executor.executeRubberDuckSafely(
                agentConfig(),
                ReviewTarget.gitHub("owner/repo"),
                context(),
                1,
                1,
                List.of()
            );

            assertThat(results).isNotEmpty();
            assertThat(capturedExecutionId.get()).isEqualTo("exec-duck");
        } finally {
            CORRELATION.clearExecutionId();
            executorService.close();
        }
    }
}
