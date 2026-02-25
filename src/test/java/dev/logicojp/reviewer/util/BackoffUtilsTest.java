package dev.logicojp.reviewer.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;


@DisplayName("BackoffUtils")
class BackoffUtilsTest {

    @Test
    @DisplayName("quiet版は割り込み時に例外を送出せず割り込みフラグを復元する")
    void quietlyRestoresInterruptedFlag() {
        Thread.currentThread().interrupt();
        try {
            Assertions.assertThatCode(() -> BackoffUtils.sleepWithJitterQuietly(1, 1, 1))
                .doesNotThrowAnyException();
            Assertions.assertThatCode(() -> {
                if (!Thread.currentThread().isInterrupted()) {
                    throw new IllegalStateException("interrupt flag should be restored");
                }
            }).doesNotThrowAnyException();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("attemptが0以下でもsleepWithJitterは実行できる")
    void sleepWithJitterAcceptsNonPositiveAttempt() {
        Assertions.assertThatCode(() -> BackoffUtils.sleepWithJitter(0, 1, 1))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Equal Jitterで最低待機時間を保証する")
    void sleepWithJitterHasMinimumDelay() throws InterruptedException {
        long start = System.nanoTime();
        BackoffUtils.sleepWithJitter(3, 8, 32);
        long elapsedNs = System.nanoTime() - start;

        Assertions.assertThat(elapsedNs).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(14));
    }

    @Test
    @DisplayName("retryableメッセージの代表パターンを検出する")
    void detectsRetryableMessages() {
        Assertions.assertThat(BackoffUtils.isRetryableMessage("network timeout while calling API")).isTrue();
        Assertions.assertThat(BackoffUtils.isRetryableMessage("HTTP 429 Too Many Requests")).isTrue();
        Assertions.assertThat(BackoffUtils.isRetryableMessage("Service temporarily unavailable")).isTrue();
        Assertions.assertThat(BackoffUtils.isRetryableMessage("CONNECTION RESET")).isTrue();
    }

    @Test
    @DisplayName("retryableでないメッセージはfalseを返す")
    void returnsFalseForNonRetryableMessages() {
        Assertions.assertThat(BackoffUtils.isRetryableMessage(null)).isFalse();
        Assertions.assertThat(BackoffUtils.isRetryableMessage("validation error: missing required field")).isFalse();
        Assertions.assertThat(BackoffUtils.isRetryableMessage("permission denied")).isFalse();
    }
}
