package dev.logicojp.reviewer.domain.review;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReviewContext")
class ReviewContextTest {

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("toStringは主要フィールドを含む")
        void toStringContainsContextSummary() {
            var context = new ReviewContext(
                "2026-03-05-12-00-00",
                "high",
                "constraints",
                "source",
                true,
                2,
                null);

            String result = context.toString();

            assertThat(result).contains("ReviewContext");
            assertThat(result).contains("timestamp='2026-03-05-12-00-00'");
            assertThat(result).contains("sharedSession=true");
            assertThat(result).contains("maxRetries=2");
            assertThat(result).contains("hasSource=true");
        }
    }

    @Nested
    @DisplayName("不変性")
    class Immutability {

        @Test
        @DisplayName("nullフィールドはデフォルト値で正規化される")
        void nullFieldsNormalizedToDefaults() {
            var context = new ReviewContext(
                null,
                null,
                null,
                null,
                true,
                0,
                null);

            assertThat(context.invocationTimestamp()).isEqualTo("unknown-start-time");
            assertThat(context.reviewCircuitBreaker()).isNotNull();
        }

        @Test
        @DisplayName("BuilderでReviewContextを構築できる")
        void buildWithBuilder() {
            var context = ReviewContext.builder()
                .invocationTimestamp("2026-03-05-12-00-00")
                .reasoningEffort("high")
                .outputConstraints("constraints")
                .cachedSourceContent("source")
                .sharedSessionEnabled(false)
                .maxRetries(2)
                .build();

            assertThat(context.invocationTimestamp()).isEqualTo("2026-03-05-12-00-00");
            assertThat(context.reasoningEffort()).isEqualTo("high");
            assertThat(context.outputConstraints()).isEqualTo("constraints");
            assertThat(context.cachedSourceContent()).isEqualTo("source");
            assertThat(context.sharedSessionEnabled()).isFalse();
            assertThat(context.maxRetries()).isEqualTo(2);
            assertThat(context.reviewCircuitBreaker()).isNotNull();
        }

        @Test
        @DisplayName("maxRetriesが負の場合は例外を投げる")
        void negativeMaxRetriesThrows() {
            assertThatThrownBy(() -> ReviewContext.builder()
                .maxRetries(-1)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRetries");
        }

        // removed: builderWithoutClientThrows because ReviewContext no longer owns a CopilotClient.
    }
}
