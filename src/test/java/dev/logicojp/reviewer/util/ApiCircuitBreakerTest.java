package dev.logicojp.reviewer.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;


@DisplayName("ApiCircuitBreaker")
class ApiCircuitBreakerTest {

    private static final class MutableClock extends Clock {
        private final AtomicLong epochMillis;

        private MutableClock(long epochMillis) {
            this.epochMillis = new AtomicLong(epochMillis);
        }

        void advanceMillis(long millis) {
            epochMillis.addAndGet(millis);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(epochMillis.get());
        }

        @Override
        public long millis() {
            return epochMillis.get();
        }
    }

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("forReviewは毎回新しいインスタンスを返す")
        void forReviewReturnsNewInstanceEachTime() {
            var first = ApiCircuitBreaker.forReview();
            var second = ApiCircuitBreaker.forReview();

            Assertions.assertThat(first).isNotSameAs(second);

            first.recordFailure();
            first.recordFailure();
            first.recordFailure();
            first.recordFailure();
            first.recordFailure();

            Assertions.assertThat(second.isRequestAllowed()).isTrue();
        }

        @Test
        @DisplayName("forSummaryとforSkillも毎回新しいインスタンスを返す")
        void summaryAndSkillFactoriesReturnNewInstances() {
            Assertions.assertThat(ApiCircuitBreaker.forSummary()).isNotSameAs(ApiCircuitBreaker.forSummary());
            Assertions.assertThat(ApiCircuitBreaker.forSkill()).isNotSameAs(ApiCircuitBreaker.forSkill());
        }
    }

    @Nested
    @DisplayName("isRequestAllowed")
    class IsRequestAllowed {

        @Test
        @DisplayName("オープン期間中はリクエストを拒否する")
        void deniesWhileOpen() {
            var clock = new MutableClock(Instant.parse("2026-02-20T00:00:00Z").toEpochMilli());
            var breaker = new ApiCircuitBreaker(1, 10_000, clock);

            breaker.recordFailure();

            Assertions.assertThat(breaker.isRequestAllowed()).isFalse();
        }

        @Test
        @DisplayName("オープン期間後はハーフオープンの単一プローブのみ許可する")
        void allowsSingleProbeAfterOpenDuration() {
            var clock = new MutableClock(Instant.parse("2026-02-20T00:00:00Z").toEpochMilli());
            var breaker = new ApiCircuitBreaker(1, 1_000, clock);
            breaker.recordFailure();
            clock.advanceMillis(2_000);

            Assertions.assertThat(breaker.isRequestAllowed()).isTrue();
            Assertions.assertThat(breaker.isRequestAllowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("half-open")
    class HalfOpen {

        @Test
        @DisplayName("ハーフオープン中の失敗で再度オープンする")
        void reopensOnHalfOpenFailure() {
            var clock = new MutableClock(Instant.parse("2026-02-20T00:00:00Z").toEpochMilli());
            var breaker = new ApiCircuitBreaker(1, 1, clock);
            breaker.recordFailure();

            Assertions.assertThat(breaker.isRequestAllowed()).isFalse();
            clock.advanceMillis(10);

            Assertions.assertThat(breaker.isRequestAllowed()).isTrue();
            breaker.recordFailure();
            Assertions.assertThat(breaker.isRequestAllowed()).isFalse();
        }

        @Test
        @DisplayName("ハーフオープン失敗が続くとオープン期間が延長される")
        void openDurationIncreasesOnRepeatedProbeFailures() {
            var clock = new MutableClock(Instant.parse("2026-02-20T00:00:00Z").toEpochMilli());
            var breaker = new ApiCircuitBreaker(1, 1000, clock);

            breaker.recordFailure();
            clock.advanceMillis(1000);
            Assertions.assertThat(breaker.isRequestAllowed()).isTrue();
            breaker.recordFailure();

            clock.advanceMillis(1000);
            Assertions.assertThat(breaker.isRequestAllowed()).isTrue();
            breaker.recordFailure();

            clock.advanceMillis(1000);
            Assertions.assertThat(breaker.isRequestAllowed()).isFalse();

            clock.advanceMillis(1000);
            Assertions.assertThat(breaker.isRequestAllowed()).isTrue();
        }
    }
}
