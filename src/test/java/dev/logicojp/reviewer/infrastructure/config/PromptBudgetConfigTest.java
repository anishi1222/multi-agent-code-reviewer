package dev.logicojp.reviewer.infrastructure.config;

import dev.logicojp.reviewer.shared.PromptBudget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Guards the Micronaut binder → pure carrier mapping introduced when main's single
/// `config.PromptBudgetConfig` (commit `38dcbc8`) was split for ADR-0006 Rule 1.
///
/// The mapping copies eight positional fields, so a transposition would compile
/// silently. Each field is given a distinct value to make that detectable.
@DisplayName("PromptBudgetConfig")
class PromptBudgetConfigTest {

    @Test
    @DisplayName("toPromptBudget() maps every field positionally without transposition")
    void mapsAllFieldsToPromptBudget() {
        var config = new PromptBudgetConfig(true, 11, 22, 33, 44, 55, 66, 77);

        PromptBudget budget = config.toPromptBudget();

        assertThat(budget.compactPrompts()).isTrue();
        assertThat(budget.peerContentMaxChars()).isEqualTo(11);
        assertThat(budget.synthesisTurnMaxChars()).isEqualTo(22);
        assertThat(budget.synthesisHistoryMaxChars()).isEqualTo(33);
        assertThat(budget.localSourceMaxChars()).isEqualTo(44);
        assertThat(budget.summaryContentPerAgentMaxChars()).isEqualTo(55);
        assertThat(budget.summaryTotalMaxChars()).isEqualTo(66);
        assertThat(budget.summaryFallbackMaxChars()).isEqualTo(77);
    }

    @Test
    @DisplayName("デフォルトコンストラクタはPromptBudgetの既定値と一致する")
    void defaultsMatchPromptBudgetDefaults() {
        // Cheap smoke test only. Since t27 both sides read PromptBudget's constants, so
        // this cannot detect a duplicated default; PromptBudgetConfigBindingTest carries
        // the real negative control for that.
        assertThat(new PromptBudgetConfig().toPromptBudget()).isEqualTo(new PromptBudget());
    }
}
