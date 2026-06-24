package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.service.TemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RubberDuckPromptBuilder")
class RubberDuckPromptBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("initial prompt はテンプレート、instruction、local sourceを連結する")
    void buildsInitialPromptWithLocalSource() throws IOException {
        writeTemplate("rubber-duck-initial-ja.md", "INITIAL");
        RubberDuckPromptBuilder builder = builder(agent("ja"));

        String result = builder.buildInitialPrompt("INSTRUCTION", "SOURCE");

        assertThat(result).contains("INITIAL").contains("INSTRUCTION").contains("SOURCE");
    }

    @Test
    @DisplayName("peer/counter prompt はpeerReviewContentを置換する")
    void replacesPeerContent() throws IOException {
        writeTemplate("rubber-duck-peer-review-ja.md", "PEER:${peerReviewContent}");
        writeTemplate("rubber-duck-counter-ja.md", "COUNTER:${peerReviewContent}");
        RubberDuckPromptBuilder builder = builder(agent("ja"));

        assertThat(builder.buildPeerReviewPrompt("A result")).isEqualTo("PEER:A result");
        assertThat(builder.buildCounterPrompt("B result")).isEqualTo("COUNTER:B result");
    }

    @Test
    @DisplayName("language-specific template が無い場合は ja へfallbackする")
    void fallsBackToJapaneseTemplate() throws IOException {
        writeTemplate("rubber-duck-peer-review-ja.md", "JA:${peerReviewContent}");
        RubberDuckPromptBuilder builder = builder(agent("en"));

        assertThat(builder.buildPeerReviewPrompt("content")).isEqualTo("JA:content");
    }

    @Test
    @DisplayName("system prompt はrole descriptionとoutput constraintsを含む")
    void buildsSystemPromptWithOutputConstraints() throws IOException {
        RubberDuckPromptBuilder builder = builder(agent("ja"));

        assertThat(builder.buildSystemPromptA())
            .contains("SYSTEM")
            .contains("peer-discussion")
            .contains("OUTPUT_CONSTRAINTS");
        assertThat(builder.buildSystemPromptB())
            .contains("SYSTEM")
            .contains("independent perspective")
            .contains("OUTPUT_CONSTRAINTS");
    }

    private RubberDuckPromptBuilder builder(AgentConfig config) {
        return new RubberDuckPromptBuilder(config, context(), templateService());
    }

    private ReviewContext context() {
        return ReviewContext.builder()
            .client(new com.github.copilot.CopilotClient(new com.github.copilot.rpc.CopilotClientOptions()))
            .timeoutMinutes(1)
            .idleTimeoutMinutes(1)
            .invocationTimestamp("2026-06-24-14-00-00")
            .maxRetries(0)
            .outputConstraints("OUTPUT_CONSTRAINTS")
            .localFileConfig(new LocalFileConfig())
            .build();
    }

    private TemplateService templateService() {
        return new TemplateService(new TemplateConfig(tempDir.toString(), null, null, null, null, null, null, null, null));
    }

    private AgentConfig agent(String language) {
        return AgentConfig.builder()
            .name("agent")
            .displayName("Agent")
            .model("model-a")
            .systemPrompt("SYSTEM")
            .instruction("instruction")
            .focusAreas(List.of())
            .skills(List.of())
            .language(language)
            .build();
    }

    private void writeTemplate(String name, String content) throws IOException {
        Files.writeString(tempDir.resolve(name), content);
    }
}
