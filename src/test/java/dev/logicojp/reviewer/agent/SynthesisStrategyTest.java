package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.PromptBudgetConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SynthesisStrategy")
class SynthesisStrategyTest {

    @Test
    @DisplayName("compact synthesisは履歴上限が小さくても最終ラウンドを保持する")
    void compactSynthesisKeepsFinalRound() {
        var strategy = new SynthesisStrategy.LastResponder("SYNTHESIZE");
        var rounds = List.of(
            new DialogueRound(1, "a", "A1".repeat(100), "b", "B1".repeat(100)),
            new DialogueRound(2, "a", "A2".repeat(100), "b", "B2".repeat(100)),
            new DialogueRound(3, "a", "FINAL_A", "b", "FINAL_B")
        );
        var budget = new PromptBudgetConfig(true, 100, 40, 140, 100, 100, 100, 20);
        AgentConfig config = AgentConfig.builder()
            .name("agent")
            .displayName("Agent")
            .model("model-a")
            .build();

        String prompt = strategy.buildSynthesisPrompt(rounds, config, budget);

        assertThat(prompt).contains("### Round 3");
        assertThat(prompt).contains("FINAL_A");
        assertThat(prompt).contains("FINAL_B");
        assertThat(prompt).doesNotContain("### Round 1");
    }
}
