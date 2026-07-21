package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.service.TemplateService;
import dev.logicojp.reviewer.util.PlaceholderUtils;
import dev.logicojp.reviewer.util.PromptContentCompactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

final class RubberDuckPromptBuilder {

    private static final Logger logger = LoggerFactory.getLogger(RubberDuckPromptBuilder.class);

    private static final String ROLE_DESCRIPTION_A =
        "You are participating in a peer-discussion code review. "
            + "Engage constructively with the other reviewer's perspective. ";

    private static final String ROLE_DESCRIPTION_B =
        "You are a peer reviewer providing an independent perspective. "
            + "Challenge assumptions and offer alternative viewpoints constructively. ";

    private final AgentConfig config;
    private final ReviewContext ctx;
    private final TemplateService templateService;
    private final boolean compactPrompts;
    private final int peerContentMaxChars;

    RubberDuckPromptBuilder(AgentConfig config,
                            ReviewContext ctx,
                            TemplateService templateService) {
        this.config = Objects.requireNonNull(config);
        this.ctx = Objects.requireNonNull(ctx);
        this.templateService = Objects.requireNonNull(templateService);
        this.compactPrompts = ctx.promptBudgetConfig().compactPrompts();
        this.peerContentMaxChars = ctx.promptBudgetConfig().peerContentMaxChars();
    }

    String buildInitialPrompt(String instruction, String localSourceContent) {
        String template = loadTemplate("rubber-duck-initial");
        var sb = new StringBuilder(template);
        sb.append("\n\n").append(instruction);
        if (localSourceContent != null && !localSourceContent.isBlank()) {
            sb.append("\n\n").append(localSourceContent);
        }
        return sb.toString();
    }

    String buildPeerReviewPrompt(String peerContent) {
        return replacePeerContent(loadTemplate("rubber-duck-peer-review"), peerContent);
    }

    String buildCounterPrompt(String peerContent) {
        return replacePeerContent(loadTemplate("rubber-duck-counter"), peerContent);
    }

    String buildSystemPromptA() {
        return buildSystemPrompt(ROLE_DESCRIPTION_A);
    }

    String buildSystemPromptB() {
        return buildSystemPrompt(ROLE_DESCRIPTION_B);
    }

    String synthesisTemplate() {
        return loadTemplate("rubber-duck-synthesis");
    }

    private String replacePeerContent(String template, String peerContent) {
        return PlaceholderUtils.replaceDollarPlaceholders(
            template, Map.of("peerReviewContent", peerContentForPrompt(peerContent)));
    }

    private String peerContentForPrompt(String peerContent) {
        String safeContent = safeContent(peerContent);
        if (!compactPrompts) {
            return safeContent;
        }
        return PromptContentCompactor.compactKeepingTail(safeContent, peerContentMaxChars);
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

    private String loadTemplate(String baseName) {
        String templateName = baseName + "-" + config.language() + ".md";
        try {
            return templateService.loadTemplateContent(templateName);
        } catch (Exception e) {
            logger.debug("Template '{}' not found, falling back to 'ja'", templateName);
            return templateService.loadTemplateContent(baseName + "-ja.md");
        }
    }

    static String safeContent(String content) {
        return content != null ? content : "";
    }
}
