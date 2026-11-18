package dev.logicojp.reviewer.infrastructure.copilot;

import com.microsoft.copilot.SystemMessageMode;
import com.microsoft.copilot.rpc.CreateSessionOptions;
import dev.logicojp.reviewer.application.port.outbound.McpServerSpec;
import dev.logicojp.reviewer.application.port.outbound.SessionRequest;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewSessionConfigFactory")
class ReviewSessionConfigFactoryTest {

    private static final String MODEL = "claude-sonnet-4";
    private static final String SYSTEM_PROMPT = "You are a security reviewer.";
    private static final AgentConfig AGENT_CONFIG = new AgentConfig(
        "test-agent", "テストエージェント", MODEL,
        SYSTEM_PROMPT, "instruction", null,
        List.of("security"), List.of()
    );

    private final ReviewSessionConfigFactory factory = new ReviewSessionConfigFactory();

    private static SessionRequest request(List<McpServerSpec> mcpServers, Map<String, String> parameters) {
        return new SessionRequest(AGENT_CONFIG, "review prompt", mcpServers, parameters);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("モデル名が設定される")
        void setsModelName() {
            CreateSessionOptions result = factory.create(
                request(List.of(), Map.of()), SYSTEM_PROMPT, "2026-03-05-12-34-56", 1, 1);

            assertThat(result.getModel()).isEqualTo(MODEL);
        }

        @Test
        @DisplayName("システムメッセージが設定される")
        void setsSystemMessage() {
            CreateSessionOptions result = factory.create(
                request(List.of(), Map.of()), SYSTEM_PROMPT, "2026-03-05-12-34-56", 1, 1);

            assertThat(result.getSystemMessage()).isNotNull();
            assertThat(result.getSystemMessage().getContent()).isEqualTo(SYSTEM_PROMPT);
            assertThat(result.getSystemMessage().getMode()).isEqualTo(SystemMessageMode.APPEND);
            assertThat(result.getPermissions().getOnRequest()).isSameAs(CopilotPermissionHandlers.DENY_ALL);
        }

        @Test
        @DisplayName("MCPサーバーがnullの場合はsetMcpServersを呼ばない")
        void handlesNullMcpServers() {
            CreateSessionOptions result = factory.create(
                request(null, Map.of()), SYSTEM_PROMPT, "2026-03-05-12-34-56", 1, 1);

            assertThat(result).isNotNull();
            assertThat(result.getMcp().getServers()).isNullOrEmpty();
        }

        @Test
        @DisplayName("MCPサーバーが指定された場合は設定される")
        void setsMcpServers() {
            var mcpServers = List.of(McpServerSpec.of("github", "https://api.example.com"));

            CreateSessionOptions result = factory.create(
                request(mcpServers, Map.of()), SYSTEM_PROMPT, "2026-03-05-12-34-56", 2, 3);

            assertThat(result.getSessionId()).isEqualTo("test-agent_2of3_2026-03-05-12-34-56");
            assertThat(result.getMcp().getServers()).containsKey("github");
        }
    }
}
