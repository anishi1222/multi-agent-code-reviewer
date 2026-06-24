package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.util.FrontmatterParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentFrontmatterMapper")
class AgentFrontmatterMapperTest {

    private final AgentFrontmatterMapper mapper = new AgentFrontmatterMapper();

    @Test
    @DisplayName("frontmatter値をParsedAgentMetadataへ写像する")
    void mapsFrontmatterToMetadata() {
        var parsed = FrontmatterParser.parse("""
            ---
            name: security
            description: Security Agent
            model: claude-opus-4.8
            peer-model: gpt-5.5
            rubber-duck: true
            dialogue-rounds: 3
            language: en
            ---
            body text
            """);

        ParsedAgentMetadata result = mapper.map(parsed, "fallback.agent.md");

        assertThat(result.name()).isEqualTo("security");
        assertThat(result.displayName()).isEqualTo("Security Agent");
        assertThat(result.model()).isEqualTo("claude-opus-4.8");
        assertThat(result.peerModel()).isEqualTo("gpt-5.5");
        assertThat(result.rubberDuckEnabled()).isTrue();
        assertThat(result.dialogueRounds()).isEqualTo(3);
        assertThat(result.language()).isEqualTo("en");
        assertThat(result.body()).isEqualTo("body text\n");
    }

    @Test
    @DisplayName("省略値は既定値とファイル名から補完する")
    void fillsDefaultsFromFilename() {
        var parsed = FrontmatterParser.parse("""
            ---
            displayName: Display Name
            dialogue-rounds: not-a-number
            ---
            body
            """);

        ParsedAgentMetadata result = mapper.map(parsed, "code-quality.agent.md");

        assertThat(result.name()).isEqualTo("code-quality");
        assertThat(result.displayName()).isEqualTo("Display Name");
        assertThat(result.model()).isEqualTo(ModelConfig.DEFAULT_MODEL);
        assertThat(result.peerModel()).isNull();
        assertThat(result.rubberDuckEnabled()).isFalse();
        assertThat(result.dialogueRounds()).isEqualTo(AgentConfig.DEFAULT_DIALOGUE_ROUNDS);
        assertThat(result.language()).isEqualTo(AgentConfig.DEFAULT_LANGUAGE);
    }
}
