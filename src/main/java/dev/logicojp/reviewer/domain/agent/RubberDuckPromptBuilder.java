package dev.logicojp.reviewer.domain.agent;

import dev.logicojp.reviewer.domain.review.ReviewContext;
import dev.logicojp.reviewer.shared.PlaceholderUtils;

import java.util.Map;
import java.util.Objects;

/// Builds prompt strings for rubber-duck dialogue sessions.
///
/// Purified from {@code agent.RubberDuckPromptBuilder}:
/// - Removed {@code TemplateService} (infrastructure). Templates are now loaded
///   by the application layer ({@code RubberDuckDialogueRunner}) via {@code LoadTemplatePort}
///   and passed in as pre-loaded {@code String} parameters.
/// - Removed SLF4J; no I/O in this class.
/// - Uses {@code java.util.logging} if logging ever becomes necessary.
public final class RubberDuckPromptBuilder {

    public static final String DEFAULT_ROLE_DESCRIPTION_A =
        "You are participating in a peer-discussion code review. "
            + "Engage constructively with the other reviewer's perspective. ";

    public static final String DEFAULT_ROLE_DESCRIPTION_B =
        "You are a peer reviewer providing an independent perspective. "
            + "Challenge assumptions and offer alternative viewpoints constructively. ";

    private final AgentConfig config;
    private final ReviewContext ctx;

    public RubberDuckPromptBuilder(AgentConfig config, ReviewContext ctx) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.ctx = Objects.requireNonNull(ctx, "ctx must not be null");
    }

    /// Builds the initial dialogue prompt.
    ///
    /// @param instruction       the review instruction (from the instruction section)
    /// @param localSourceContent embedded source code (may be null for GitHub reviews)
    /// @param initialTemplate   pre-loaded template content (from application layer / LoadTemplatePort)
    /// @return the assembled initial prompt
    public String buildInitialPrompt(String instruction, String localSourceContent, String initialTemplate) {
        var sb = new StringBuilder();
        if (initialTemplate != null && !initialTemplate.isBlank()) {
            sb.append(initialTemplate).append("\n\n");
        }
        sb.append(instruction);
        if (localSourceContent != null && !localSourceContent.isBlank()) {
            sb.append("\n\n").append(localSourceContent);
        }
        return sb.toString();
    }

    /// Builds the peer-review prompt (what agentB receives after agentA's first pass).
    ///
    /// @param peerContent         agentA's review content
    /// @param peerReviewTemplate  pre-loaded template (may contain {@code $peerReviewContent})
    /// @return rendered peer-review prompt
    public String buildPeerReviewPrompt(String peerContent, String peerReviewTemplate) {
        return replacePeerContent(peerReviewTemplate, peerContent);
    }

    /// Builds the counter prompt (what agentA receives after agentB's response).
    ///
    /// @param peerContent     agentB's review content
    /// @param counterTemplate pre-loaded template (may contain {@code $peerReviewContent})
    /// @return rendered counter prompt
    public String buildCounterPrompt(String peerContent, String counterTemplate) {
        return replacePeerContent(counterTemplate, peerContent);
    }

    /// Builds the system prompt for dialogue participant A.
    public String buildSystemPromptA() {
        return buildSystemPrompt(DEFAULT_ROLE_DESCRIPTION_A);
    }

    /// Builds the system prompt for dialogue participant B.
    public String buildSystemPromptB() {
        return buildSystemPrompt(DEFAULT_ROLE_DESCRIPTION_B);
    }

    private String replacePeerContent(String template, String peerContent) {
        if (template == null || template.isBlank()) return safeContent(peerContent);
        return PlaceholderUtils.replaceDollarPlaceholders(
            template, Map.of("peerReviewContent", safeContent(peerContent)));
    }

    private String buildSystemPrompt(String roleDescription) {
        var sb = new StringBuilder();
        if (config.systemPrompt() != null) {
            sb.append(config.systemPrompt());
        }
        sb.append("\n\n").append(roleDescription);
        if (ctx.outputConstraints() != null) {
            sb.append("\n\n").append(ctx.outputConstraints());
        }
        return sb.toString();
    }

    public static String safeContent(String content) {
        return content != null ? content : "";
    }
}
