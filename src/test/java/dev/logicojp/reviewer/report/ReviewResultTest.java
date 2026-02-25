package dev.logicojp.reviewer.report;

import org.assertj.core.api.Assertions;
import dev.logicojp.reviewer.agent.AgentConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;


@DisplayName("ReviewResult")
class ReviewResultTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("成功結果を構築する")
        void successResult() {
            var config = AgentConfig.builder().name("agent").build();
            var result = ReviewResult.builder()
                .agentConfig(config)
                .repository("owner/repo")
                .content("Review content")
                .success(true)
                .build();

            Assertions.assertThat(result.success()).isTrue();
            Assertions.assertThat(result.content()).isEqualTo("Review content");
            Assertions.assertThat(result.repository()).isEqualTo("owner/repo");
            Assertions.assertThat(result.agentConfig().name()).isEqualTo("agent");
            Assertions.assertThat(result.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("失敗結果を構築する")
        void failureResult() {
            var result = ReviewResult.builder()
                .success(false)
                .errorMessage("Timeout occurred")
                .build();

            Assertions.assertThat(result.success()).isFalse();
            Assertions.assertThat(result.errorMessage()).isEqualTo("Timeout occurred");
        }

        @Test
        @DisplayName("カスタムClockを使用してタイムスタンプを設定する")
        void customClock() {
            Instant fixed = Instant.parse("2025-01-01T00:00:00Z");
            Clock clock = Clock.fixed(fixed, ZoneOffset.UTC);

            var result = ReviewResult.builder(clock).build();
            Assertions.assertThat(result.timestamp()).isEqualTo(fixed);
        }
    }

    @Nested
    @DisplayName("failedResults")
    class FailedResults {

        @Test
        @DisplayName("指定回数分の失敗結果を生成する")
        void generatesMultipleFailures() {
            var config = AgentConfig.builder().name("agent").build();
            var results = ReviewResult.failedResults(config, "owner/repo", 3, "Error");

            Assertions.assertThat(results).hasSize(3);
            Assertions.assertThat(results).allSatisfy(r -> {
                Assertions.assertThat(r.success()).isFalse();
                Assertions.assertThat(r.errorMessage()).isEqualTo("Error");
            });
        }

        @Test
        @DisplayName("countが0の場合は空リストを返す")
        void emptyForZeroCount() {
            var results = ReviewResult.failedResults(null, null, 0, "err");
            Assertions.assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("負のcountの場合は空リストを返す")
        void emptyForNegativeCount() {
            var results = ReviewResult.failedResults(null, null, -1, "err");
            Assertions.assertThat(results).isEmpty();
        }
    }
}
