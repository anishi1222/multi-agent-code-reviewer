package dev.logicojp.reviewer.domain.report;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewResult")
class ReviewResultTest {

    private AgentConfig createAgent(String name) {
        return new AgentConfig(name, name, "model",
            "system", "instruction", null,
            List.of("area"), List.of());
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("成功結果のビルド")
        void buildSuccessResult() {
            AgentConfig config = createAgent("test-agent");
            ReviewResult result = ReviewResult.builder()
                .agentConfig(config)
                .repository("owner/repo")
                .content("review content")
                .success(true)
                .build();

            assertThat(result.agentConfig()).isEqualTo(config);
            assertThat(result.repository()).isEqualTo("owner/repo");
            assertThat(result.content()).isEqualTo("review content");
            assertThat(result.success()).isTrue();
            assertThat(result.errorMessage()).isNull();
            assertThat(result.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("失敗結果のビルド")
        void buildFailureResult() {
            AgentConfig config = createAgent("test-agent");
            ReviewResult result = ReviewResult.builder()
                .agentConfig(config)
                .repository("owner/repo")
                .success(false)
                .errorMessage("timeout occurred")
                .build();

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("timeout occurred");
            assertThat(result.content()).isNull();
        }

        @Test
        @DisplayName("timestampをカスタム値で設定できる")
        void customTimestamp() {
            Instant ts = Instant.parse("2025-06-15T12:00:00Z");
            ReviewResult result = ReviewResult.builder()
                .agentConfig(createAgent("a"))
                .repository("r")
                .timestamp(ts)
                .build();

            assertThat(result.timestamp()).isEqualTo(ts);
        }
    }

    @Nested
    @DisplayName("コンストラクタ")
    class Constructor {

        @Test
        @DisplayName("指定したtimestampが保持される")
        void keepsProvidedTimestamp() {
            Instant ts = Instant.parse("2025-07-01T00:00:00Z");
            ReviewResult result = new ReviewResult(
                createAgent("a"), "repo", "content", ts, true, null, 0);
            assertThat(result.timestamp()).isEqualTo(ts);
        }
    }

    @Nested
    @DisplayName("isSuccess")
    class IsSuccessTests {

        @Test
        @DisplayName("success=trueのときisSuccessはtrueを返す")
        void successReturnsTrue() {
            ReviewResult result = ReviewResult.builder()
                .agentConfig(createAgent("a"))
                .repository("r")
                .success(true)
                .build();
            assertThat(result.success()).isTrue();
        }

        @Test
        @DisplayName("success=falseのときisSuccessはfalseを返す")
        void failureReturnsFalse() {
            ReviewResult result = ReviewResult.builder()
                .agentConfig(createAgent("a"))
                .repository("r")
                .success(false)
                .build();
            assertThat(result.success()).isFalse();
        }
    }
}
