package dev.logicojp.reviewer.agent;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.CopilotClientOptions;
import dev.logicojp.reviewer.config.LocalFileConfig;
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
            var client = new CopilotClient(new CopilotClientOptions());
            try {
                var context = new ReviewContext(
                    client,
                    new ReviewContext.TimeoutConfig(5, 3, 2),
                    "2026-03-05-12-00-00",
                    true,
                    null,
                    null,
                    new ReviewContext.CachedResources(null, null),
                    new LocalFileConfig(),
                    null,
                    null);

                String result = context.toString();

                assertThat(result).contains("ReviewContext");
                assertThat(result).contains("timeoutMinutes=5");
            } finally {
                client.close();
            }
        }
    }

    @Nested
    @DisplayName("不変性")
    class Immutability {

        @Test
        @DisplayName("nullフィールドはデフォルト値で正規化される")
        void nullFieldsNormalizedToDefaults() {
            var client = new CopilotClient(new CopilotClientOptions());
            try {
                var context = new ReviewContext(
                    client,
                    null,
                    "2026-03-05-12-00-00",
                    true,
                    null,
                    null,
                    null,
                    new LocalFileConfig(),
                    null,
                    null);

                assertThat(context.timeoutConfig()).isNotNull();
                assertThat(context.cachedResources()).isNotNull();
            } finally {
                client.close();
            }
        }

        @Test
        @DisplayName("BuilderでReviewContextを構築できる")
        void buildWithBuilder() {
            var client = new CopilotClient(new CopilotClientOptions());
            var context = ReviewContext.builder()
                .client(client)
                .timeoutMinutes(5)
                .idleTimeoutMinutes(3)
                .invocationTimestamp("2026-03-05-12-00-00")
                .maxRetries(2)
                .build();

            try {
                assertThat(context.timeoutConfig().timeoutMinutes()).isEqualTo(5);
                assertThat(context.timeoutConfig().idleTimeoutMinutes()).isEqualTo(3);
                assertThat(context.timeoutConfig().maxRetries()).isEqualTo(2);
                assertThat(context.localFileConfig()).isNotNull();
            } finally {
                client.close();
            }
        }

        @Test
        @DisplayName("Builderでclient未設定の場合は例外を投げる")
        void builderWithoutClientThrows() {
            assertThatThrownBy(() -> ReviewContext.builder()
                .timeoutMinutes(5)
                .idleTimeoutMinutes(3)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("client");
        }
    }
}
