package dev.logicojp.reviewer.domain.agent;

/// Formats the review system prompt that informs Copilot
/// about its expected behavior as a reviewer agent.
/// Pure string formatting — no framework or SDK dependencies.
public final class ReviewSystemPromptFormatter {

    private static final String DEFAULT_IDENTITY_PROMPT =
        """
        あなたはコードレビュアーAIです。
        GitHub Copilot CLIとして動作し、指定されたリポジトリのコードをレビューします。
        """;

    private final String identityPrompt;

    public ReviewSystemPromptFormatter() {
        this(DEFAULT_IDENTITY_PROMPT);
    }

    public ReviewSystemPromptFormatter(String identityPrompt) {
        this.identityPrompt = identityPrompt != null ? identityPrompt : DEFAULT_IDENTITY_PROMPT;
    }

    /// Formats the complete system prompt for a review session using the given agent config.
    public String format(AgentConfig config) {
        return format(config, AgentPromptBuilder.DEFAULT_FOCUS_AREAS_GUIDANCE);
    }

    /// Formats the complete system prompt with custom focus-areas guidance text.
    public String format(AgentConfig config, String focusAreasGuidance) {
        var sb = new StringBuilder();
        sb.append(identityPrompt.trim()).append("\n\n");
        sb.append(AgentPromptBuilder.buildFullSystemPrompt(config, focusAreasGuidance));
        return sb.toString().trim();
    }

    public String defaultIdentityPrompt() {
        return identityPrompt;
    }
}
