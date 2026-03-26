package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SharedCircuitBreaker")
class SharedCircuitBreakerTest {

    @Test
    @DisplayName("ドメイン別デフォルトブレーカーは独立している")
    void domainDefaultBreakersAreIsolated() {
        SharedCircuitBreaker review = SharedCircuitBreaker.forReviewDomain();
        SharedCircuitBreaker skill = SharedCircuitBreaker.forSkillDomain();
        SharedCircuitBreaker summary = SharedCircuitBreaker.forSummaryDomain();

        review.reset();
        skill.reset();
        summary.reset();
        try {
            for (int i = 0; i < CircuitBreakerConfig.DEFAULT_FAILURE_THRESHOLD; i++) {
                review.onFailure();
            }

            assertThat(review.allowRequest()).isFalse();
            assertThat(skill.allowRequest()).isTrue();
            assertThat(summary.allowRequest()).isTrue();
        } finally {
            review.reset();
            skill.reset();
            summary.reset();
        }
    }

    @Test
    @DisplayName("異なるインスタンス間で障害が分離される")
    void independentInstancesIsolateFailures() {
        SharedCircuitBreaker review = new SharedCircuitBreaker(2, 100L);
        SharedCircuitBreaker skill = new SharedCircuitBreaker(2, 100L);

        review.onFailure();
        review.onFailure();

        assertThat(review.allowRequest()).isFalse();
        assertThat(skill.allowRequest()).isTrue();
    }

    @Test
    @DisplayName("失敗閾値に達するとリクエストを遮断する")
    void blocksAfterFailureThreshold() {
        AtomicLong clock = new AtomicLong(0L);
        SharedCircuitBreaker breaker = new SharedCircuitBreaker(3, 10_000L, clock::get);

        assertThat(breaker.allowRequest()).isTrue();
        breaker.onFailure();
        breaker.onFailure();
        breaker.onFailure();

        assertThat(breaker.allowRequest()).isFalse();
    }

    @Test
    @DisplayName("リセット時間経過後は再度リクエストを許可する")
    void allowsAfterResetTimeout() {
        AtomicLong clock = new AtomicLong(0L);
        SharedCircuitBreaker breaker = new SharedCircuitBreaker(2, 100L, clock::get);

        breaker.onFailure();
        breaker.onFailure();
        assertThat(breaker.allowRequest()).isFalse();

        clock.set(101L);
        assertThat(breaker.allowRequest()).isTrue();
    }

    @Test
    @DisplayName("成功時に状態をリセットする")
    void resetsOnSuccess() {
        AtomicLong clock = new AtomicLong(0L);
        SharedCircuitBreaker breaker = new SharedCircuitBreaker(2, 100L, clock::get);

        breaker.onFailure();
        breaker.onFailure();
        assertThat(breaker.allowRequest()).isFalse();

        breaker.onSuccess();

        assertThat(breaker.allowRequest()).isTrue();
    }
}
