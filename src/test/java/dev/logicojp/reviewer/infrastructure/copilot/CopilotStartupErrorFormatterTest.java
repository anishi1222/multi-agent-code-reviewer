package dev.logicojp.reviewer.infrastructure.copilot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import dev.logicojp.reviewer.infrastructure.auth.CopilotCliPathResolver;

@DisplayName("CopilotStartupErrorFormatter")
class CopilotStartupErrorFormatterTest {

    private final CopilotStartupErrorFormatter formatter = new CopilotStartupErrorFormatter();

    @Test
    @DisplayName("client timeout メッセージに秒数と環境変数名を含む")
    void buildsClientTimeoutMessage() {
        String message = formatter.buildClientTimeoutMessage(60);

        assertThat(message).contains("timed out after 60s");
        assertThat(message).contains("COPILOT_START_TIMEOUT_SECONDS");
    }

    @Test
    @DisplayName("protocol timeout メッセージにCLI_PATH_ENVを含む")
    void buildsProtocolTimeoutMessage() {
        String message = formatter.buildProtocolTimeoutMessage();

        assertThat(message).contains("Copilot CLI ping timed out");
        assertThat(message).contains(CopilotCliPathResolver.CLI_PATH_ENV);
    }
}
