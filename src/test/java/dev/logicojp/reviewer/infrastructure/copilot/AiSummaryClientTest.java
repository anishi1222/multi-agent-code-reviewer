package dev.logicojp.reviewer.infrastructure.copilot;

import com.github.copilot.rpc.SessionConfig;
import dev.logicojp.reviewer.infrastructure.auth.CopilotCliPathResolver;
import dev.logicojp.reviewer.infrastructure.config.CopilotConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AiSummaryClient")
class AiSummaryClientTest {

    // removed: timeout helper methods no longer exist; AiSummaryClient now uses the configured agent timeout directly.

    @Test
    @DisplayName("summary session configにmodel/system promptを設定する")
    void createsSummarySessionConfig() throws Exception {
        AiSummaryClient client = new AiSummaryClient(
            copilotService(),
            "claude-opus-4.8",
            "SYSTEM SUMMARY",
            5
        );

        SessionConfig config = buildSessionConfig(client);

        assertThat(config.getModel()).isEqualTo("claude-opus-4.8");
        assertThat(config.getSystemMessage().getContent()).isEqualTo("SYSTEM SUMMARY");
        assertThat(config.getOnPermissionRequest()).isNotNull();
    }

    @Test
    @DisplayName("blank modelはデフォルトモデルへフォールバックする")
    void blankModelFallsBackToDefault() throws Exception {
        AiSummaryClient client = new AiSummaryClient(copilotService(), " ", null, 5);

        SessionConfig config = buildSessionConfig(client);

        assertThat(config.getModel()).isEqualTo("claude-sonnet-4.5");
        assertThat(config.getSystemMessage()).isNull();
    }

    private static SessionConfig buildSessionConfig(AiSummaryClient client) throws Exception {
        Method method = AiSummaryClient.class.getDeclaredMethod("buildSessionConfig");
        method.setAccessible(true);
        return (SessionConfig) method.invoke(client);
    }

    private static CopilotService copilotService() {
        CopilotConfig config = new CopilotConfig(null, null, 60, 10, 15);
        return new CopilotService(
            new CopilotCliPathResolver(config),
            new CopilotHealthProbe(config),
            config,
            new CopilotStartupErrorFormatter(),
            new CopilotClientStarter()
        );
    }
}
