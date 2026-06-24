package dev.logicojp.reviewer.agent;

import com.github.copilot.rpc.McpHttpServerConfig;
import com.github.copilot.rpc.McpServerConfig;
import dev.logicojp.reviewer.config.LocalFileConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SdkRubberDuckSessionFactory")
class SdkRubberDuckSessionFactoryTest {

    @Test
    @DisplayName("session config は ID をsanitizeし、MCPとreasoning effortを設定する")
    void buildsSessionConfigWithSanitizedIdMcpAndReasoningEffort() {
        AgentConfig config = AgentConfig.builder()
            .name("agent/name")
            .displayName("Agent")
            .model("claude-opus-4.8")
            .systemPrompt("system")
            .instruction("instruction")
            .focusAreas(List.of())
            .skills(List.of())
            .build();
        ReviewContext ctx = ReviewContext.builder()
            .client(new com.github.copilot.CopilotClient(new com.github.copilot.rpc.CopilotClientOptions()))
            .timeoutMinutes(1)
            .idleTimeoutMinutes(1)
            .invocationTimestamp("2026/06/24 14:00")
            .maxRetries(0)
            .reasoningEffort("high")
            .localFileConfig(new LocalFileConfig())
            .build();
        Map<String, McpServerConfig> mcpServers = Map.of(
            "github",
            new McpHttpServerConfig().setUrl("https://example.com")
        );

        var sessionConfig = new SdkRubberDuckSessionFactory(config, ctx)
            .buildSessionConfig("claude-opus-4.8", "SYSTEM", mcpServers, "tag/1");

        assertThat(sessionConfig.getSessionId()).isEqualTo("agent-name_rubber-duck_tag-1_2026-06-24-14-00");
        assertThat(sessionConfig.getMcpServers()).containsKey("github");
        assertThat(sessionConfig.getReasoningEffort()).isEqualTo("high");
        assertThat(sessionConfig.getSystemMessage().getContent()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("blank tokenはunknownにsanitizeする")
    void sanitizesBlankTokenToUnknown() {
        assertThat(SdkRubberDuckSessionFactory.sanitizeToken(null)).isEqualTo("unknown");
        assertThat(SdkRubberDuckSessionFactory.sanitizeToken("  ")).isEqualTo("unknown");
    }
}
