package dev.logicojp.reviewer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptBudgetConfig")
class PromptBudgetConfigTest {

    @Test
    @DisplayName("0以下の値はデフォルト値に正規化される")
    void invalidValuesFallbackToDefaults() {
        var config = new PromptBudgetConfig(true, 0, -1, 0, 0, 0, 0, 0);

        assertThat(config.compactPrompts()).isTrue();
        assertThat(config.peerContentMaxChars()).isEqualTo(PromptBudgetConfig.DEFAULT_PEER_CONTENT_MAX_CHARS);
        assertThat(config.synthesisTurnMaxChars()).isEqualTo(PromptBudgetConfig.DEFAULT_SYNTHESIS_TURN_MAX_CHARS);
        assertThat(config.synthesisHistoryMaxChars()).isEqualTo(PromptBudgetConfig.DEFAULT_SYNTHESIS_HISTORY_MAX_CHARS);
        assertThat(config.localSourceMaxChars()).isEqualTo(PromptBudgetConfig.DEFAULT_LOCAL_SOURCE_MAX_CHARS);
        assertThat(config.summaryContentPerAgentMaxChars())
            .isEqualTo(PromptBudgetConfig.DEFAULT_SUMMARY_CONTENT_PER_AGENT_MAX_CHARS);
        assertThat(config.summaryTotalMaxChars()).isEqualTo(PromptBudgetConfig.DEFAULT_SUMMARY_TOTAL_MAX_CHARS);
        assertThat(config.summaryFallbackMaxChars()).isEqualTo(PromptBudgetConfig.DEFAULT_SUMMARY_FALLBACK_MAX_CHARS);
    }

    @Test
    @DisplayName("compactPromptsだけを差し替えられる")
    void canEnableCompactPrompts() {
        var config = new PromptBudgetConfig().withCompactPrompts(true);

        assertThat(config.compactPrompts()).isTrue();
        assertThat(config.peerContentMaxChars()).isEqualTo(PromptBudgetConfig.DEFAULT_PEER_CONTENT_MAX_CHARS);
    }
}
