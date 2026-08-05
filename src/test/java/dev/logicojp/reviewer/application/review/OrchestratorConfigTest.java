package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.domain.review.PromptTexts;
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
            0,
            0,
            0,
            -1,
            true,
            null,
            null,
            null,
            null,
            false,
            0
        );

        assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(OrchestratorConfig.DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES);
        assertThat(config.agentTimeoutMinutes()).isEqualTo(OrchestratorConfig.DEFAULT_AGENT_TIMEOUT_MINUTES);
        assertThat(config.reviewPasses()).isEqualTo(OrchestratorConfig.DEFAULT_REVIEW_PASSES);
        assertThat(config.maxRetries()).isEqualTo(OrchestratorConfig.DEFAULT_MAX_RETRIES);
        assertThat(config.rubberDuckRounds()).isEqualTo(OrchestratorConfig.DEFAULT_RUBBER_DUCK_ROUNDS);
        assertThat(config.invocationTimestamp()).isEqualTo("unknown-start-time");
        assertThat(config.promptTexts()).isEqualTo(new PromptTexts(null, null, null));
    }

    @Test
    @DisplayName("toStringにトークン生値を出力しない")
    void toStringDoesNotExposeToken() {
        var config = OrchestratorConfig.builder()
            .githubToken("secret-token")
            .invocationTimestamp("2026-03-05-12-34-56")
            .promptTexts(new PromptTexts(null, null, null))
            .build();

        assertThat(config.toString()).doesNotContain("secret-token");
        assertThat(config.toString()).contains("timeout=", "agentTimeout=", "passes=", "retries=");
    }
}
