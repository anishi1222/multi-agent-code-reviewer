package dev.logicojp.reviewer.orchestrator;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.CopilotClientOptions;
import dev.logicojp.reviewer.agent.SharedCircuitBreaker;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.LocalFileConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewContextFactory")
class ReviewContextFactoryTest {

    @Test
    @DisplayName("設定値を反映したReviewContextを生成する")
    void createsContextWithConfiguredValues() {
        CopilotClient client = new CopilotClient(new CopilotClientOptions());
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            var executionConfig = ExecutionConfig.ofFlat(2, 1, 10, 5, 3, 5, 5, 10, 2, 0, 0, 0);
            Map<String, Object> cachedMcp = Map.of("github", Map.of("type", "http"));
            var localFileConfig = new LocalFileConfig();

            var factory = new ReviewContextFactory(
                client,
                executionConfig,
                "high",
                "constraints",
                cachedMcp,
                localFileConfig,
                scheduler,
                SharedCircuitBreaker.withDefaultConfig()
            );

            var context = factory.create(Optional.of("SOURCE_CONTENT"));

            assertThat(context.client()).isSameAs(client);
            assertThat(context.timeoutConfig().timeoutMinutes()).isEqualTo(5);
            assertThat(context.timeoutConfig().idleTimeoutMinutes()).isEqualTo(3);
            assertThat(context.timeoutConfig().maxRetries()).isEqualTo(2);
            assertThat(context.reasoningEffort()).isEqualTo("high");
            assertThat(context.outputConstraints()).isEqualTo("constraints");
            assertThat(context.cachedResources().mcpServers()).isEqualTo(cachedMcp);
            assertThat(context.cachedResources().sourceContent()).isEqualTo("SOURCE_CONTENT");
            assertThat(context.sharedScheduler()).isSameAs(scheduler);
        } finally {
            scheduler.close();
            client.close();
        }
    }
}