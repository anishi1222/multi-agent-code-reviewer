package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.outbound.LoadTemplatePort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.report.ReviewResultFactory;
import dev.logicojp.reviewer.domain.review.ReviewContext;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import dev.logicojp.reviewer.infrastructure.logging.MdcCorrelationAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Executable coverage for PM behaviour ORC-05.
@DisplayName("agent review semaphore behavior")
class AgentReviewSemaphoreBehaviorTest {

    @Test
    @DisplayName("上限超過のエージェントを待機させ同時実行数をpermit数以下に保つ")
    void queuesExcessAgentAndNeverExceedsPermitCount() throws Exception {
        ObservingSemaphore semaphore = new ObservingSemaphore(1);
        CountDownLatch firstExecutionEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstExecution = new CountDownLatch(1);
        AtomicInteger activeExecutions = new AtomicInteger();
        AtomicInteger maximumActiveExecutions = new AtomicInteger();
        AtomicInteger enteredExecutions = new AtomicInteger();

        var sessionRunner = new ReviewPassRunner(request -> {
            int active = activeExecutions.incrementAndGet();
            maximumActiveExecutions.accumulateAndGet(active, Math::max);
            int entry = enteredExecutions.incrementAndGet();
            firstExecutionEntered.countDown();
            try {
                if (entry == 1) {
                    await(releaseFirstExecution, "first execution was not released");
                }
                return "ok-" + request.agentConfig().name();
            } finally {
                activeExecutions.decrementAndGet();
            }
        }, new ReviewResultFactory());

        try (var agentWorkers = Executors.newVirtualThreadPerTaskExecutor();
             var callers = Executors.newFixedThreadPool(2)) {
            AgentReviewExecutor executor = new AgentReviewExecutor(
                semaphore,
                agentWorkers,
                sessionRunner,
                unusedRubberDuckRunner(),
                new OrchestratorMetrics(),
                new MdcCorrelationAdapter()
            );

            var first = callers.submit(() -> execute(executor, agent("first")));
            assertThat(firstExecutionEntered.await(2, TimeUnit.SECONDS)).isTrue();
            var second = callers.submit(() -> execute(executor, agent("second")));

            try {
                assertThat(semaphore.secondAcquireAttempt.await(2, TimeUnit.SECONDS))
                    .as("the second caller must reach the semaphore before queueing is asserted")
                    .isTrue();
                assertThat(second.isDone()).isFalse();
                assertThat(enteredExecutions.get()).isEqualTo(1);
                assertThat(activeExecutions.get()).isEqualTo(1);
                assertThat(maximumActiveExecutions.get()).isEqualTo(1);
            } finally {
                releaseFirstExecution.countDown();
            }

            List<ReviewResult> results = List.of(
                    first.get(3, TimeUnit.SECONDS),
                    second.get(3, TimeUnit.SECONDS))
                .stream()
                .flatMap(List::stream)
                .toList();

            assertThat(results).hasSize(2).allSatisfy(result ->
                assertThat(result.success()).isTrue());
            assertThat(enteredExecutions.get()).isEqualTo(2);
            assertThat(maximumActiveExecutions.get()).isEqualTo(1);
            assertThat(semaphore.availablePermits()).isEqualTo(1);
        }
    }

    private static List<ReviewResult> execute(AgentReviewExecutor executor, AgentConfig agent) {
        return executor.executeAgentPassesSafely(
            agent,
            ReviewTarget.gitHub("owner/repo"),
            ReviewContext.builder().maxRetries(0).build(),
            1,
            1,
            List.of(),
            0
        );
    }

    private static AgentConfig agent(String name) {
        return new AgentConfig(name, name, "model", "system", "instruction", null, List.of(), List.of());
    }

    private static RubberDuckDialogueRunner unusedRubberDuckRunner() {
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

    private static void await(CountDownLatch latch, String timeoutMessage) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new AssertionError(timeoutMessage);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while coordinating semaphore test", e);
        }
    }

    private static final class ObservingSemaphore extends Semaphore {
        private final AtomicInteger acquireAttempts = new AtomicInteger();
        private final CountDownLatch secondAcquireAttempt = new CountDownLatch(1);

        private ObservingSemaphore(int permits) {
            super(permits);
        }

        @Override
        public void acquire() throws InterruptedException {
            if (acquireAttempts.incrementAndGet() == 2) {
                secondAcquireAttempt.countDown();
            }
            super.acquire();
        }
    }
}
