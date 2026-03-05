package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.LocalFileConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrchestratorConfig")
class OrchestratorConfigTest {

    @Test
    @DisplayName("null入力時にデフォルト値で正規化される")
    void normalizesNullInputs() {
        var config = new OrchestratorConfig(
            null,
            null,
            null,
            ExecutionConfig.defaults(),
            null,
            null,
            null
        );

        assertThat(config.localFileConfig()).isNotNull();
        assertThat(config.promptTexts()).isEqualTo(new PromptTexts(null, null, null));
    }

    @Test
    @DisplayName("toStringにトークン生値を出力しない")
    void toStringMasksToken() {
        var config = new OrchestratorConfig(
            "secret-token",
            null,
            new LocalFileConfig(),
            ExecutionConfig.defaults(),
            null,
            null,
            new PromptTexts(null, null, null)
        );

        assertThat(config.toString()).contains("githubToken=***");
        assertThat(config.toString()).doesNotContain("secret-token");
    }
}
