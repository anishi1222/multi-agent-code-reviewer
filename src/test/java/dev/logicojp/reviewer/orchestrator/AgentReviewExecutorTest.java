package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentReviewExecutor")
class AgentReviewExecutorTest {

    private AgentConfig agentConfig() {
        return new AgentConfig("security", "Security", "model", "system", "instruction", null, List.of(), List.of());
    }

    private ReviewContext context() {
        return ReviewContext.builder()
            .client(new com.github.copilot.sdk.CopilotClient(new com.github.copilot.sdk.json.CopilotClientOptions()))
            .timeoutMinutes(1)
            .idleTimeoutMinutes(1)
            .maxRetries(0)
            .localFileConfig(new LocalFileConfig())
            .sharedScheduler(Executors.newSingleThreadScheduledExecutor())
            .build();
    }

    @Test
    @DisplayName("正常系ではレビュー結果を返す")
    void returnsSuccessResult() {
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        var ctx = context();
        try {
            var executor = new AgentReviewExecutor(
                new Semaphore(1),
                executorService,
                (config, context) -> new AgentReviewer() {
                    @Override
                    public ReviewResult review(ReviewTarget target) {
                        return ReviewResult.builder()
                            .agentConfig(config)
                            .repository(target.displayName())
                            .content("ok")
                            .success(true)
                            .timestamp(Instant.now())
                            .build();
                    }

                    @Override
                    public List<ReviewResult> reviewPasses(ReviewTarget target, int reviewPasses) {
                        var results = new ArrayList<ReviewResult>(reviewPasses);
                        for (int pass = 0; pass < reviewPasses; pass++) {
                            results.add(review(target));
                        }
                        return results;
                    }
                }
            );

            var results = executor.executeAgentPassesSafely(
                agentConfig(),
                ReviewTarget.gitHub("owner/repo"),
                ctx,
                2,
                1
            );

            assertThat(results).hasSize(2);
            assertThat(results).allSatisfy(result -> {
                assertThat(result.success()).isTrue();
                assertThat(result.content()).isEqualTo("ok");
            });
        } finally {
            executorService.close();
            ctx.client().close();
            ctx.sharedScheduler().close();
        }
    }

    @Test
    @DisplayName("実行例外は失敗結果に変換される")
    void mapsExecutionExceptionToFailureResult() {
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        var ctx = context();
        try {
            var executor = new AgentReviewExecutor(
                new Semaphore(1),
                executorService,
                (config, context) -> new AgentReviewer() {
                    @Override
                    public ReviewResult review(ReviewTarget target) {
                        throw new IllegalStateException("boom");
                    }
                }
            );

            var results = executor.executeAgentPassesSafely(
                agentConfig(),
                ReviewTarget.gitHub("owner/repo"),
                ctx,
                2,
                1
            );

            assertThat(results).hasSize(2);
            assertThat(results).allSatisfy(result -> {
                assertThat(result.success()).isFalse();
                assertThat(result.errorMessage()).contains("Review failed:");
            });
        } finally {
            executorService.close();
            ctx.client().close();
            ctx.sharedScheduler().close();
        }
    }

    @Test
    @DisplayName("multi-pass実行では同一reviewerインスタンスが再利用される")
    void reusesSingleReviewerInstanceForAllPasses() {
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        var ctx = context();
        var createdReviewers = new AtomicInteger();
        var reviewPassesCalls = new AtomicInteger();
        try {
            var executor = new AgentReviewExecutor(
                new Semaphore(1),
                executorService,
                (config, context) -> {
                    createdReviewers.incrementAndGet();
                    return new AgentReviewer() {
                        @Override
                        public ReviewResult review(ReviewTarget target) {
                            return ReviewResult.builder()
                                .agentConfig(config)
                                .repository(target.displayName())
                                .content("ok")
                                .success(true)
                                .timestamp(Instant.now())
                                .build();
                        }

                        @Override
                        public List<ReviewResult> reviewPasses(ReviewTarget target, int reviewPasses) {
                            reviewPassesCalls.incrementAndGet();
                            var results = new ArrayList<ReviewResult>(reviewPasses);
                            for (int pass = 0; pass < reviewPasses; pass++) {
                                results.add(review(target));
                            }
                            return results;
                        }
                    };
                }
            );

            var results = executor.executeAgentPassesSafely(
                agentConfig(),
                ReviewTarget.gitHub("owner/repo"),
                ctx,
                3,
                1
            );

            assertThat(results).hasSize(3);
            assertThat(createdReviewers.get()).isEqualTo(1);
            assertThat(reviewPassesCalls.get()).isEqualTo(1);
        } finally {
            executorService.close();
            ctx.client().close();
            ctx.sharedScheduler().close();
        }
    }

    @Test
    @DisplayName("待機中に割り込まれた場合は実行Futureをキャンセルして失敗結果を返す")
    void cancelsFutureWhenInterruptedDuringWait() {
        var interruptedExecutor = new InterruptedOnGetExecutorService();
        var ctx = context();
        try {
            var executor = new AgentReviewExecutor(
                new Semaphore(1),
                interruptedExecutor,
                (config, context) -> target -> ReviewResult.builder()
                    .agentConfig(config)
                    .repository(target.displayName())
                    .content("ignored")
                    .success(true)
                    .timestamp(Instant.now())
                    .build()
            );

            var results = executor.executeAgentPassesSafely(
                agentConfig(),
                ReviewTarget.gitHub("owner/repo"),
                ctx,
                1,
                1
            );

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().success()).isFalse();
            assertThat(results.getFirst().errorMessage()).contains("interrupted");
            assertThat(interruptedExecutor.future.cancelCalled).isTrue();
        } finally {
            interruptedExecutor.shutdownNow();
            ctx.client().close();
            ctx.sharedScheduler().close();
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
}
