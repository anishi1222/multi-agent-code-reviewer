package dev.logicojp.reviewer.domain.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewSystemPromptFormatter")
class ReviewSystemPromptFormatterTest {

    @Test
    @DisplayName("システムプロンプトと出力形式を順序通りに連結する")
    void formatsSystemPromptWithOutputFormat() {
        var formatter = new ReviewSystemPromptFormatter("IDENTITY_PROMPT");
        var config = AgentConfig.builder()
            .name("base")
            .systemPrompt("BASE_PROMPT")
            .outputFormat("OUTPUT_CONSTRAINTS")
            .build();

        String prompt = formatter.format(config);

        assertThat(prompt).contains("IDENTITY_PROMPT");
        assertThat(prompt).contains("BASE_PROMPT");
        assertThat(prompt).contains("OUTPUT_CONSTRAINTS");
        assertThat(prompt).containsSubsequence("IDENTITY_PROMPT", "BASE_PROMPT", "OUTPUT_CONSTRAINTS");
    }

    @Test
    @DisplayName("出力形式がnullの場合は追加しない")
    void formatsSystemPromptWithNullOutputFormat() {
        var formatter = new ReviewSystemPromptFormatter("IDENTITY_PROMPT");
        var config = AgentConfig.builder()
            .name("base")
            .systemPrompt("BASE_PROMPT")
            .outputFormat(null)
            .build();

        String prompt = formatter.format(config);

        assertThat(prompt).isEqualTo("IDENTITY_PROMPT\n\nBASE_PROMPT");
    }
}
