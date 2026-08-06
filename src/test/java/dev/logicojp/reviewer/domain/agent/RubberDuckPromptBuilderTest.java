package dev.logicojp.reviewer.domain.agent;

import dev.logicojp.reviewer.domain.review.ReviewContext;
import dev.logicojp.reviewer.shared.PromptBudget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RubberDuckPromptBuilder")
class RubberDuckPromptBuilderTest {

    @Test
    @DisplayName("initial prompt はテンプレート、instruction、local sourceを連結する")
    void buildsInitialPromptWithLocalSource() {
        RubberDuckPromptBuilder builder = builder(agent("ja"));

        String result = builder.buildInitialPrompt("INSTRUCTION", "SOURCE", "INITIAL");

        assertThat(result).contains("INITIAL").contains("INSTRUCTION").contains("SOURCE");
    }

    @Test
    @DisplayName("peer/counter prompt はpeerReviewContentを置換する")
    void replacesPeerContent() {
        RubberDuckPromptBuilder builder = builder(agent("ja"));

        assertThat(builder.buildPeerReviewPrompt("A result", "PEER:${peerReviewContent}"))
            .isEqualTo("PEER:A result");
        assertThat(builder.buildCounterPrompt("B result", "COUNTER:${peerReviewContent}"))
            .isEqualTo("COUNTER:B result");
    }

    @Test
    @DisplayName("template が無い場合はpeer contentをそのまま返す")
    void fallsBackToPeerContentWhenTemplateMissing() {
        RubberDuckPromptBuilder builder = builder(agent("en"));

        assertThat(builder.buildPeerReviewPrompt("content", null)).isEqualTo("content");
    }

    @Test
    @DisplayName("system prompt はrole descriptionとoutput constraintsを含む")
    void buildsSystemPromptWithOutputConstraints() {
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
        return new RubberDuckPromptBuilder(config, context());
    }

    private ReviewContext context() {
        return context(new PromptBudget());
    }

    private ReviewContext context(PromptBudget promptBudget) {
        return ReviewContext.builder()
            .promptBudget(promptBudget)
            .invocationTimestamp("2026-06-24-14-00-00")
            .maxRetries(0)
            .outputConstraints("OUTPUT_CONSTRAINTS")
            .build();
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
}
