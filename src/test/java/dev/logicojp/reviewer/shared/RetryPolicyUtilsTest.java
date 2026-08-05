package dev.logicojp.reviewer.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RetryPolicyUtils")
class RetryPolicyUtilsTest {

    @Test
    @DisplayName("TimeoutExceptionは一時的障害として判定される")
    void timeoutIsTransient() {
        assertThat(RetryPolicyUtils.isTransientException(new TimeoutException("timeout"))).isTrue();
    }

    @Test
    @DisplayName("ExecutionExceptionのcauseが一時的障害ならtrue")
    void unwrapsExecutionExceptionCause() {
        Exception wrapped = new ExecutionException(new IOException("network timeout"));

        assertThat(RetryPolicyUtils.isTransientException(wrapped)).isTrue();
    }

    @Test
    @DisplayName("認証系エラーメッセージは再試行不可")
    void authMessageIsNotRetryable() {
        assertThat(RetryPolicyUtils.isRetryableFailureMessage("401 unauthorized")).isFalse();
    }

    @Test
    @DisplayName("追加の非再試行キーワードが一致した場合は再試行不可")
    void additionalMarkerCanDisableRetry() {
        assertThat(RetryPolicyUtils.isRetryableFailureMessage(
            "Missing required parameter: name",
            "missing required parameter"
        )).isFalse();
    }

    @Test
    @DisplayName("バックオフ計算は最大値を超えない")
    void backoffWithJitterIsBounded() {
        long backoff = RetryPolicyUtils.computeBackoffWithJitter(1_000L, 2_000L, 10);

        assertThat(backoff).isLessThanOrEqualTo(2_000L);
        assertThat(backoff).isGreaterThanOrEqualTo(0L);
    }
}