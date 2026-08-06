package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.port.outbound.McpServerSpec;
import dev.logicojp.reviewer.application.port.outbound.SessionRequest;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.ReviewSystemPromptFormatter;
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
        var request = new SessionRequest(
            config,
            "review prompt",
            List.of(McpServerSpec.of("github", "https://example.com")),
            Map.of()
        );
        String systemPrompt = new ReviewSystemPromptFormatter().format(config);

        var sessionConfig = new ReviewSessionConfigFactory()
            .create(request, systemPrompt, "2026-06-24-14-00-00", 2, 3);

        assertThat(sessionConfig.getModel()).isEqualTo("model-a");
        assertThat(sessionConfig.getSessionId()).isEqualTo("security_2of3_2026-06-24-14-00-00");
        assertThat(sessionConfig.getMcpServers()).containsKey("github");
        assertThat(sessionConfig.getSystemMessage().getContent())
            .contains("SYSTEM")
            .contains("auth");
    }
}
