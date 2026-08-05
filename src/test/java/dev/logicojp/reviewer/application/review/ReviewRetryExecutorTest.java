package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.resilience.SharedCircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewRetryExecutor")
class ReviewRetryExecutorTest {

    @Test
    @DisplayName("成功結果が返れば即座に終了する")
    void returnsOnFirstSuccess() {
        var attempts = new AtomicInteger();
        var executor = testExecutor(2, _ -> { });

        ReviewResult result = executor.execute(
            () -> {
                attempts.incrementAndGet();
                return successResult();
            },
            this::failureFromException
        );

        assertThat(result.success()).isTrue();
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("失敗後に再試行し成功結果を返す")
    void retriesAfterFailedResult() {
        var attempts = new AtomicInteger();
        var sleeps = new AtomicInteger();
        var executor = testExecutor(2, _ -> sleeps.incrementAndGet());

        ReviewResult result = executor.execute(
            () -> attempts.getAndIncrement() == 0 ? failureResult("empty") : successResult(),
            this::failureFromException
        );

        assertThat(result.success()).isTrue();
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(sleeps.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("例外発生時も再試行し最後の結果を返す")
    void retriesAfterExceptionAndReturnsLastFailure() {
        var attempts = new AtomicInteger();
        var sleeps = new AtomicInteger();
        var executor = testExecutor(1, _ -> sleeps.incrementAndGet());

        ReviewResult result = executor.execute(
            () -> {
                attempts.incrementAndGet();
                throw new TimeoutException("boom");
            },
            this::failureFromException
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("boom");
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(sleeps.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("非一時的な例外は再試行せずに失敗を返す")
    void doesNotRetryOnNonTransientException() {
        var attempts = new AtomicInteger();
        var sleeps = new AtomicInteger();
        var executor = testExecutor(2, _ -> sleeps.incrementAndGet());

        ReviewResult result = executor.execute(
            () -> {
                attempts.incrementAndGet();
                throw new IllegalArgumentException("invalid model");
            },
            this::failureFromException
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("invalid model");
        assertThat(attempts.get()).isEqualTo(1);
        assertThat(sleeps.get()).isZero();
    }

    private ReviewRetryExecutor testExecutor(int maxRetries, ReviewRetryExecutor.SleepStrategy sleepStrategy) {
        return new ReviewRetryExecutor("security", maxRetries, 1, 4,
            sleepStrategy, SharedCircuitBreaker.withDefaultConfig());
    }

    private ReviewResult successResult() {
        return ReviewResult.builder()
            .agentConfig(agent())
            .repository("owner/repo")
            .content("ok")
            .success(true)
            .timestamp(Instant.now())
            .build();
    }

    private ReviewResult failureResult(String message) {
        return ReviewResult.builder()
            .agentConfig(agent())
            .repository("owner/repo")
            .success(false)
            .errorMessage(message)
            .timestamp(Instant.now())
            .build();
    }

    private ReviewResult failureFromException(Exception e) {
        return failureResult(e.getMessage());
    }

    private AgentConfig agent() {
        return new AgentConfig("security", "Security", "model",
            "system", "instruction", null, List.of("area"), List.of());
    }
}
