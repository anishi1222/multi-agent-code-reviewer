package dev.logicojp.reviewer.infrastructure.copilot;

import com.github.copilot.rpc.SessionConfig;
import dev.logicojp.reviewer.application.port.outbound.McpServerSpec;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.infrastructure.auth.CopilotCliPathResolver;
import dev.logicojp.reviewer.infrastructure.config.CopilotConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SdkRubberDuckSessionFactory")
class SdkRubberDuckSessionFactoryTest {

    @Test
    @DisplayName("session config は ID をsanitizeし、MCPとreasoning effortを設定する")
    void buildsSessionConfigWithSanitizedIdMcpAndReasoningEffort() throws Exception {
        AgentConfig config = AgentConfig.builder()
            .name("agent/name")
            .displayName("Agent")
            .model("claude-opus-4.8")
            .systemPrompt("system")
            .instruction("instruction")
            .focusAreas(List.of())
            .skills(List.of())
            .build();
        var mcpServers = List.of(McpServerSpec.of("github", "https://example.com"));

        var sessionConfig = buildSessionConfig(config, "SYSTEM", mcpServers, "tag/1");

        assertThat(sessionConfig.getSessionId()).isEqualTo("agent-name_rubber-duck_tag-1_2026-06-24-14-00");
        assertThat(sessionConfig.getMcpServers()).containsKey("github");
        assertThat(sessionConfig.getReasoningEffort()).isEqualTo("high");
        assertThat(sessionConfig.getSystemMessage().getContent()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("blank tokenはunknownにsanitizeする")
    void sanitizesBlankTokenToUnknown() {
        assertThat(SdkRubberDuckSessionFactory.sanitize(null)).isEqualTo("unknown");
        assertThat(SdkRubberDuckSessionFactory.sanitize("  ")).isEqualTo("unknown");
    }

    @SuppressWarnings("unchecked")
    private static SessionConfig buildSessionConfig(AgentConfig config,
                                                    String systemPrompt,
                                                    List<McpServerSpec> mcpServers,
                                                    String sessionTag) throws Exception {
        var copilotConfig = new CopilotConfig(null, null, 60, 10, 15);
        var copilotService = new CopilotService(
            new CopilotCliPathResolver(copilotConfig),
            new CopilotHealthProbe(copilotConfig),
            copilotConfig,
            new CopilotStartupErrorFormatter(),
            new CopilotClientStarter()
        );
        var factory = new SdkRubberDuckSessionFactory(copilotService, 1, "2026/06/24 14:00");
        Method method = SdkRubberDuckSessionFactory.class.getDeclaredMethod(
            "buildSessionConfig", AgentConfig.class, String.class, List.class, String.class);
        method.setAccessible(true);
        return (SessionConfig) method.invoke(factory, config, systemPrompt, mcpServers, sessionTag);
    }
}
