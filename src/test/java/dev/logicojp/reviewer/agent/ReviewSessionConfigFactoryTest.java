package dev.logicojp.reviewer.agent;

import com.github.copilot.sdk.SystemMessageMode;
import com.github.copilot.sdk.json.CopilotClientOptions;
import com.github.copilot.sdk.json.McpHttpServerConfig;
import com.github.copilot.sdk.json.McpServerConfig;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.CopilotClient;
import dev.logicojp.reviewer.config.LocalFileConfig;
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

    private ReviewContext createContext(String reasoningEffort) {
        return ReviewContext.builder()
            .client(new CopilotClient(new CopilotClientOptions()))
            .timeoutMinutes(1)
            .idleTimeoutMinutes(1)
            .invocationTimestamp("2026-03-05-12-34-56")
            .maxRetries(0)
            .reasoningEffort(reasoningEffort)
            .localFileConfig(new LocalFileConfig())
            .build();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("モデル名が設定される")
        void setsModelName() {
            ReviewContext ctx = createContext(null);

            SessionConfig result = factory.create(
                AGENT_CONFIG, ctx, SYSTEM_PROMPT, null, 1, 1);

            assertThat(result.getModel()).isEqualTo(MODEL);
        }

        @Test
        @DisplayName("システムメッセージが設定される")
        void setsSystemMessage() {
            ReviewContext ctx = createContext(null);

            SessionConfig result = factory.create(
                AGENT_CONFIG, ctx, SYSTEM_PROMPT, null, 1, 1);

            assertThat(result.getSystemMessage()).isNotNull();
            assertThat(result.getSystemMessage().getContent()).isEqualTo(SYSTEM_PROMPT);
            assertThat(result.getSystemMessage().getMode()).isEqualTo(SystemMessageMode.APPEND);
        }

        @Test
        @DisplayName("MCPサーバーがnullの場合はsetMcpServersを呼ばない")
        void handlesNullMcpServers() {
            ReviewContext ctx = createContext(null);

            SessionConfig result = factory.create(
                AGENT_CONFIG, ctx, SYSTEM_PROMPT, null, 1, 1);

            // Should not throw
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("MCPサーバーが指定された場合は設定される")
        void setsMcpServers() {
            ReviewContext ctx = createContext(null);
            Map<String, McpServerConfig> mcpServers = Map.of(
                "github",
                new McpHttpServerConfig().setUrl("https://api.example.com")
            );

            SessionConfig result = factory.create(
                AGENT_CONFIG, ctx, SYSTEM_PROMPT, mcpServers, 2, 3);

            assertThat(result).isNotNull();
            assertThat(result.getSessionId()).isEqualTo("test-agent_2of3_2026-03-05-12-34-56");
        }
    }
}
