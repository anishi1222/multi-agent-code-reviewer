package dev.logicojp.reviewer.agent;

import com.github.copilot.rpc.McpHttpServerConfig;
import com.github.copilot.rpc.McpServerConfig;
import dev.logicojp.reviewer.config.LocalFileConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewSessionExecutor")
class ReviewSessionExecutorTest {

    @Test
    @DisplayName("session configはpass情報、MCP、system promptを反映する")
    void createsSessionConfigFromRequest() {
        AgentConfig config = new AgentConfig(
            "security", "Security", "model-a", "SYSTEM", "instruction", null, List.of("auth"), List.of());
        ReviewContext ctx = ReviewContext.builder()
            .client(new com.github.copilot.CopilotClient(new com.github.copilot.rpc.CopilotClientOptions()))
            .timeoutMinutes(1)
            .idleTimeoutMinutes(1)
            .invocationTimestamp("2026-06-24-14-00-00")
            .maxRetries(0)
            .outputConstraints("OUTPUT_CONSTRAINTS")
            .localFileConfig(new LocalFileConfig())
            .build();
        ReviewSessionExecutor executor = new ReviewSessionExecutor(
            config,
            ctx,
            new ReviewSystemPromptFormatter(),
            new ReviewSessionMessageSender(config.name()),
            new ReviewSessionConfigFactory(),
            new ReviewResultFactory(),
            "FOCUS_GUIDANCE",
            "LOCAL_HEADER",
            "LOCAL_REQUEST"
        );
        Map<String, McpServerConfig> mcpServers = Map.of(
            "github",
            new McpHttpServerConfig().setUrl("https://example.com")
        );

        var sessionConfig = executor.createSessionConfig(new ReviewSessionExecutor.Request(
            "owner/repo",
            "instruction",
            "source",
            mcpServers,
            2,
            3
        ));

        assertThat(sessionConfig.getModel()).isEqualTo("model-a");
        assertThat(sessionConfig.getSessionId()).isEqualTo("security_2of3_2026-06-24-14-00-00");
        assertThat(sessionConfig.getMcpServers()).containsKey("github");
        assertThat(sessionConfig.getSystemMessage().getContent())
            .contains("SYSTEM")
            .contains("FOCUS_GUIDANCE")
            .contains("OUTPUT_CONSTRAINTS");
    }
}
