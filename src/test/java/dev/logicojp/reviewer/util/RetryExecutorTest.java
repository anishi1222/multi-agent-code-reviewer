package dev.logicojp.reviewer.util;

import dev.logicojp.reviewer.agent.SharedCircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RetryExecutor")
class RetryExecutorTest {

    @Test
    @DisplayName("成功時はonSuccessが呼ばれ成功結果を返す")
    void returnsSuccessAndNotifiesObserver() {
        SharedCircuitBreaker circuitBreaker = new SharedCircuitBreaker(2, 1_000L);
        RetryExecutor<String> executor = new RetryExecutor<>(
            1,
            1,
            1,
            _ -> {
            },
            circuitBreaker
        );

        AtomicInteger successCalls = new AtomicInteger();

        String result = executor.execute(
            () -> "ok",
            exception -> "mapped",
            "ok"::equals,
            _ -> false,
            _ -> false,
            new RetryExecutor.RetryObserver<>() {
                @Override
                public void onSuccess(int attempt, int totalAttempts, String observedResult) {
                    successCalls.incrementAndGet();
                }
            }
        );

        assertThat(result).isEqualTo("ok");
        assertThat(successCalls.get()).isEqualTo(1);
        assertThat(circuitBreaker.allowRequest()).isTrue();
    }

    @Test
    @DisplayName("一時的例外はmaxRetriesまでリトライする")
    void retriesOnTransientExceptionUpToMaxRetries() {
        SharedCircuitBreaker circuitBreaker = new SharedCircuitBreaker(10, 1_000L);
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger sleepCalls = new AtomicInteger();

        RetryExecutor<String> executor = new RetryExecutor<>(
            2,
            1,
            1,
            _ -> sleepCalls.incrementAndGet(),
            circuitBreaker
        );

        String result = executor.execute(
            () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                    throw new IOException("temporary");
                }
                return "ok";
            },
            exception -> "mapped",
            "ok"::equals,
            _ -> false,
            exception -> exception instanceof IOException,
            new RetryExecutor.RetryObserver<>() {
            }
        );

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(3);
        assertThat(sleepCalls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("サーキットオープン時はexceptionMapper結果を返す")
    void returnsMappedResultWhenCircuitIsOpen() {
        SharedCircuitBreaker circuitBreaker = new SharedCircuitBreaker(1, 60_000L);
        circuitBreaker.onFailure();

        RetryExecutor<String> executor = new RetryExecutor<>(
            1,
            1,
            1,
            _ -> {
            },
            circuitBreaker
        );

        AtomicInteger circuitOpenCalls = new AtomicInteger();

        String result = executor.execute(
            () -> "should-not-run",
            exception -> "mapped-open",
            "ok"::equals,
            _ -> false,
            _ -> false,
            new RetryExecutor.RetryObserver<>() {
                @Override
                public void onCircuitOpen() {
                    circuitOpenCalls.incrementAndGet();
                }
            }
        );

        assertThat(result).isEqualTo("mapped-open");
        assertThat(circuitOpenCalls.get()).isEqualTo(1);
    }
}
