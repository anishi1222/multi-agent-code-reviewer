package dev.logicojp.reviewer.config;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


@DisplayName("CopilotConfig")
class CopilotConfigTest {

    @Test
    @DisplayName("空文字・0以下値はデフォルトに補正される")
    void defaultsApplied() {
        var config = new CopilotConfig(" ", " ", 0, -1, 0);

        Assertions.assertThat(config.cliPath()).isEmpty();
        Assertions.assertThat(config.githubToken()).isEmpty();
        Assertions.assertThat(config.healthcheckSeconds()).isEqualTo(CopilotConfig.DEFAULT_HEALTHCHECK_SECONDS);
        Assertions.assertThat(config.authcheckSeconds()).isEqualTo(CopilotConfig.DEFAULT_AUTHCHECK_SECONDS);
        Assertions.assertThat(config.startTimeoutSeconds()).isEqualTo(CopilotConfig.DEFAULT_START_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("設定値はtrimされて保持される")
    void explicitValuesPreserved() {
        var config = new CopilotConfig(" /usr/local/bin/copilot ", " token ", 12, 20, 90);

        Assertions.assertThat(config.cliPath()).isEqualTo("/usr/local/bin/copilot");
        Assertions.assertThat(config.githubToken()).isEqualTo("token");
        Assertions.assertThat(config.healthcheckSeconds()).isEqualTo(12);
        Assertions.assertThat(config.authcheckSeconds()).isEqualTo(20);
        Assertions.assertThat(config.startTimeoutSeconds()).isEqualTo(90);
    }
}
