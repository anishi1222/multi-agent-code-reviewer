package dev.logicojp.reviewer.domain.agent;

import dev.logicojp.reviewer.domain.review.ReviewContext;
import dev.logicojp.reviewer.shared.PromptBudget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    /// Guards the *invocation wiring* between [RubberDuckPromptBuilder] and
    /// `PromptContentCompactor`. `PromptContentCompactorTest` proves the compactor
    /// itself works; it proves nothing about whether this builder ever calls it.
    ///
    /// Every case below is one half of a matched pair that differs in exactly one
    /// dimension, so removing the call or inverting the guard turns one half red.
    @Nested
    @DisplayName("peer content の圧縮ワイヤリング")
    class PeerContentCompaction {

        private static final String PEER_CONTENT = "abcdefghijklmnopqrstuvwxyz";
        private static final int PEER_BUDGET = 12;

        /// `compactKeepingTail` drops the omission marker entirely when the budget is
        /// smaller than the marker itself (~43 chars), leaving a bare tail slice.
        private static final String EXPECTED_TAIL =
            PEER_CONTENT.substring(PEER_CONTENT.length() - PEER_BUDGET);

        @Test
        @DisplayName("compact-prompts が無効なら peer content を原文のまま渡す(ネガティブコントロール)")
        void keepsPeerContentIntactWhenCompactionDisabled() {
            String result = peerPrompt(budget(false, PEER_BUDGET));

            assertThat(result).isEqualTo("PEER:" + PEER_CONTENT);
        }

        @Test
        @DisplayName("compact-prompts が有効なら peer content を末尾保持で圧縮して渡す")
        void compactsPeerContentWhenEnabled() {
            String result = peerPrompt(budget(true, PEER_BUDGET));

            assertThat(result).startsWith("PEER:");
            assertThat(result).hasSizeLessThan(("PEER:" + PEER_CONTENT).length());
            // isEqualTo pins tail-keeping: head-keeping compact() would yield "PEER:abcdefghijkl".
            assertThat(result).isEqualTo("PEER:" + EXPECTED_TAIL);
        }

        @Test
        @DisplayName("counter prompt にも compact-prompts の設定が適用される")
        void compactsCounterContentWhenEnabled() {
            String enabled = counterPrompt(budget(true, PEER_BUDGET));
            String disabled = counterPrompt(budget(false, PEER_BUDGET));

            assertThat(enabled).isEqualTo("COUNTER:" + EXPECTED_TAIL);
            assertThat(disabled).isEqualTo("COUNTER:" + PEER_CONTENT);
        }

        @Test
        @DisplayName("圧縮幅は既定値ではなく設定された peerContentMaxChars を使う")
        void usesConfiguredPeerBudgetInsteadOfDefault() {
            // Same compactPrompts=true as compactsPeerContentWhenEnabled; only the
            // budget differs, so a hardcoded width would turn one of the two red.
            String result = peerPrompt(budget(true, PromptBudget.DEFAULT_PEER_CONTENT_MAX_CHARS));

            assertThat(result).isEqualTo("PEER:" + PEER_CONTENT);
        }

        private String peerPrompt(PromptBudget promptBudget) {
            return new RubberDuckPromptBuilder(agent("ja"), context(promptBudget))
                .buildPeerReviewPrompt(PEER_CONTENT, "PEER:${peerReviewContent}");
        }

        private String counterPrompt(PromptBudget promptBudget) {
            return new RubberDuckPromptBuilder(agent("ja"), context(promptBudget))
                .buildCounterPrompt(PEER_CONTENT, "COUNTER:${peerReviewContent}");
        }

        private PromptBudget budget(boolean compactPrompts, int peerContentMaxChars) {
            return new PromptBudget(
                compactPrompts,
                peerContentMaxChars,
                PromptBudget.DEFAULT_SYNTHESIS_TURN_MAX_CHARS,
                PromptBudget.DEFAULT_SYNTHESIS_HISTORY_MAX_CHARS,
                PromptBudget.DEFAULT_LOCAL_SOURCE_MAX_CHARS,
                PromptBudget.DEFAULT_SUMMARY_CONTENT_PER_AGENT_MAX_CHARS,
                PromptBudget.DEFAULT_SUMMARY_TOTAL_MAX_CHARS,
                PromptBudget.DEFAULT_SUMMARY_FALLBACK_MAX_CHARS);
        }
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
