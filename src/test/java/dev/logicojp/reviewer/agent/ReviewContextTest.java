package dev.logicojp.reviewer.agent;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.CopilotClientOptions;
import dev.logicojp.reviewer.config.LocalFileConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.List;

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
            var client = new com.github.copilot.sdk.CopilotClient(new com.github.copilot.sdk.json.CopilotClientOptions());
            var scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            try {
                var context = new ReviewContext(
                    client,
                    new ReviewContext.TimeoutConfig(5, 3, 2),
                    List.of(),
                    null,
                    null,
                    new ReviewContext.CachedResources(null, null),
                    new LocalFileConfig(),
                    scheduler,
                    null,
                    null);

                String result = context.toString();

                assertThat(result).contains("ReviewContext");
                assertThat(result).contains("timeoutMinutes=5");
            } finally {
                scheduler.shutdownNow();
                client.close();
            }
        }
    }

    @Nested
    @DisplayName("不変性")
    class Immutability {

        @Test
        @DisplayName("customInstructionsがnullの場合は空リストになる")
        void nullCustomInstructionsBecomesEmptyList() {
            var client = new com.github.copilot.sdk.CopilotClient(new com.github.copilot.sdk.json.CopilotClientOptions());
            var scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            try {
                var context = new ReviewContext(
                    client,
                    new ReviewContext.TimeoutConfig(5, 3, 2),
                    null,
                    null,
                    null,
                    new ReviewContext.CachedResources(null, null),
                    new LocalFileConfig(),
                    scheduler,
                    null,
                    null);

                assertThat(context.customInstructions()).isEmpty();
            } finally {
                scheduler.shutdownNow();
                client.close();
            }
        }

        @Test
        @DisplayName("BuilderでReviewContextを構築できる")
        void buildWithBuilder() {
            var scheduler = Executors.newSingleThreadScheduledExecutor();
            var client = new CopilotClient(new CopilotClientOptions());
            var context = ReviewContext.builder()
                .client(client)
                .timeoutMinutes(5)
                .idleTimeoutMinutes(3)
                .maxRetries(2)
                .sharedScheduler(scheduler)
                .build();

            try {
                assertThat(context.timeoutConfig().timeoutMinutes()).isEqualTo(5);
                assertThat(context.timeoutConfig().idleTimeoutMinutes()).isEqualTo(3);
                assertThat(context.timeoutConfig().maxRetries()).isEqualTo(2);
                assertThat(context.customInstructions()).isEmpty();
                assertThat(context.localFileConfig()).isNotNull();
            } finally {
                scheduler.shutdownNow();
                client.close();
            }
        }

        @Test
        @DisplayName("Builderでclient未設定の場合は例外を投げる")
        void builderWithoutClientThrows() {
            var scheduler = Executors.newSingleThreadScheduledExecutor();
            try {
                assertThatThrownBy(() -> ReviewContext.builder()
                    .timeoutMinutes(5)
                    .idleTimeoutMinutes(3)
                    .sharedScheduler(scheduler)
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("client");
            } finally {
                scheduler.shutdownNow();
            }
        }

        @Test
        @DisplayName("BuilderでsharedScheduler未設定の場合は例外を投げる")
        void builderWithoutSchedulerThrows() {
            var client = new CopilotClient(new CopilotClientOptions());
            try {
                assertThatThrownBy(() -> ReviewContext.builder()
                    .client(client)
                    .timeoutMinutes(5)
                    .idleTimeoutMinutes(3)
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("sharedScheduler");
            } finally {
                client.close();
            }
        }
    }
}
