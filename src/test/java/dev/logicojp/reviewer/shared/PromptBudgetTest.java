package dev.logicojp.reviewer.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Ported from `origin/main`'s `config.PromptBudgetConfigTest` (commit `38dcbc8`).
/// Normalisation and `withCompactPrompts` moved to the pure carrier when the Micronaut
/// binding was split out for ADR-0006 Rule 1 (domain purity).
@DisplayName("PromptBudget")
class PromptBudgetTest {

    @Test
    @DisplayName("0以下の値はデフォルト値に正規化される")
    void invalidValuesFallbackToDefaults() {
        var budget = new PromptBudget(true, 0, -1, 0, 0, 0, 0, 0);

        assertThat(budget.compactPrompts()).isTrue();
        assertThat(budget.peerContentMaxChars()).isEqualTo(PromptBudget.DEFAULT_PEER_CONTENT_MAX_CHARS);
        assertThat(budget.synthesisTurnMaxChars()).isEqualTo(PromptBudget.DEFAULT_SYNTHESIS_TURN_MAX_CHARS);
        assertThat(budget.synthesisHistoryMaxChars()).isEqualTo(PromptBudget.DEFAULT_SYNTHESIS_HISTORY_MAX_CHARS);
        assertThat(budget.localSourceMaxChars()).isEqualTo(PromptBudget.DEFAULT_LOCAL_SOURCE_MAX_CHARS);
        assertThat(budget.summaryContentPerAgentMaxChars())
            .isEqualTo(PromptBudget.DEFAULT_SUMMARY_CONTENT_PER_AGENT_MAX_CHARS);
        assertThat(budget.summaryTotalMaxChars()).isEqualTo(PromptBudget.DEFAULT_SUMMARY_TOTAL_MAX_CHARS);
        assertThat(budget.summaryFallbackMaxChars()).isEqualTo(PromptBudget.DEFAULT_SUMMARY_FALLBACK_MAX_CHARS);
    }

    @Test
    @DisplayName("compactPromptsだけを差し替えられる")
    void canEnableCompactPrompts() {
        var budget = new PromptBudget().withCompactPrompts(true);

        assertThat(budget.compactPrompts()).isTrue();
        assertThat(budget.peerContentMaxChars()).isEqualTo(PromptBudget.DEFAULT_PEER_CONTENT_MAX_CHARS);
    }
}
