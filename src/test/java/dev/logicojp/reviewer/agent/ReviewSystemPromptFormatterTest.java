package dev.logicojp.reviewer.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewSystemPromptFormatter")
class ReviewSystemPromptFormatterTest {

    @Test
    @DisplayName("outputConstraintsを順序通りに連結する")
    void formatsSystemPromptWithConstraints() {
        var formatter = new ReviewSystemPromptFormatter();

        String prompt = formatter.format(
            "BASE_PROMPT",
            "OUTPUT_CONSTRAINTS"
        );

        assertThat(prompt).contains("BASE_PROMPT");
        assertThat(prompt).contains("OUTPUT_CONSTRAINTS");
    }

    @Test
    @DisplayName("outputConstraintsがnullの場合はベースプロンプトのみ")
    void formatsSystemPromptWithNullConstraints() {
        var formatter = new ReviewSystemPromptFormatter();

        String prompt = formatter.format(
            "BASE_PROMPT",
            null
        );

        assertThat(prompt).isEqualTo("BASE_PROMPT");
    }
}