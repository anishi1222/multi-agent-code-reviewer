package dev.logicojp.reviewer.orchestrator;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.McpHttpServerConfig;
import com.github.copilot.sdk.json.McpServerConfig;
import dev.logicojp.reviewer.agent.SharedCircuitBreaker;
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
        try {
            var executionConfig = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(2, 1, 10, 5, 3, 5, 5, 10, 2, 0, 0, 0);
            Map<String, McpServerConfig> cachedMcp = Map.of(
                "github",
                new McpHttpServerConfig().setUrl("https://api.githubcopilot.com/mcp/")
            );
            var localFileConfig = new LocalFileConfig();

            var factory = new ReviewContextFactory(
                client,
                executionConfig,
                "high",
                "constraints",
                "2026-03-05-12-34-56",
                cachedMcp,
                localFileConfig,
                SharedCircuitBreaker.withDefaultConfig()
            );

            var context = factory.create(Optional.of("SOURCE_CONTENT"));

            assertThat(context.client()).isSameAs(client);
            assertThat(context.timeoutConfig().timeoutMinutes()).isEqualTo(5);
            assertThat(context.timeoutConfig().idleTimeoutMinutes()).isEqualTo(3);
            assertThat(context.timeoutConfig().maxRetries()).isEqualTo(2);
            assertThat(context.reasoningEffort()).isEqualTo("high");
            assertThat(context.outputConstraints()).isEqualTo("constraints");
            assertThat(context.invocationTimestamp()).isEqualTo("2026-03-05-12-34-56");
            assertThat(context.sharedSessionEnabled()).isTrue();
            assertThat(context.cachedResources().mcpServers()).isEqualTo(cachedMcp);
            assertThat(context.cachedResources().sourceContent()).isEqualTo("SOURCE_CONTENT");
        } finally {
            client.close();
        }
    }
}