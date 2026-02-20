package dev.logicojp.reviewer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CopilotConfig")
class CopilotConfigTest {

    @Test
    @DisplayName("空文字・0以下値はデフォルトに補正される")
    void defaultsApplied() {
        var config = new CopilotConfig(" ", 0, -1, 0);

        assertThat(config.cliPath()).isEmpty();
        assertThat(config.healthcheckSeconds()).isEqualTo(CopilotConfig.DEFAULT_HEALTHCHECK_SECONDS);
        assertThat(config.authcheckSeconds()).isEqualTo(CopilotConfig.DEFAULT_AUTHCHECK_SECONDS);
        assertThat(config.startTimeoutSeconds()).isEqualTo(CopilotConfig.DEFAULT_START_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("設定値はtrimされて保持される")
    void explicitValuesPreserved() {
        var config = new CopilotConfig(" /usr/local/bin/copilot ", 12, 20, 90);

        assertThat(config.cliPath()).isEqualTo("/usr/local/bin/copilot");
        assertThat(config.healthcheckSeconds()).isEqualTo(12);
        assertThat(config.authcheckSeconds()).isEqualTo(20);
        assertThat(config.startTimeoutSeconds()).isEqualTo(90);
    }
}
