package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentPromptBuilder;
import dev.logicojp.reviewer.agent.ReviewAgent;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.config.ResilienceConfig;
import dev.logicojp.reviewer.config.ReviewerConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.service.CopilotService;
import dev.logicojp.reviewer.service.TemplateService;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/// Factory for creating {@link ReviewOrchestrator} instances.
///
/// Injects shared services via Micronaut DI and builds the orchestrator
/// configuration from runtime parameters (token, execution config, etc.).
@Singleton
public class ReviewOrchestratorFactory {

    private static final Logger logger = LoggerFactory.getLogger(ReviewOrchestratorFactory.class);

    private final CopilotService copilotService;
    private final GithubMcpConfig githubMcpConfig;
    private final ReviewerConfig reviewerConfig;
    private final ResilienceConfig resilienceConfig;
    private final TemplateService templateService;

    @Inject
    public ReviewOrchestratorFactory(CopilotService copilotService,
                                     GithubMcpConfig githubMcpConfig,
                                     ReviewerConfig reviewerConfig,
                                     ResilienceConfig resilienceConfig,
                                     TemplateService templateService) {
        this.copilotService = copilotService;
        this.githubMcpConfig = githubMcpConfig;
        this.reviewerConfig = reviewerConfig;
        this.resilienceConfig = resilienceConfig;
        this.templateService = templateService;
    }

    /// Creates a new ReviewOrchestrator with the given runtime parameters.
    public ReviewOrchestrator create(@Nullable String githubToken,
                                     ExecutionConfig executionConfig,
                                     List<CustomInstruction> customInstructions,
                                     @Nullable String reasoningEffort,
                                     @Nullable String outputConstraints) {
        var promptTemplates = loadPromptTemplates();
        var config = new ReviewOrchestrator.Config(
            githubToken,
            githubMcpConfig,
            reviewerConfig.localFiles(),
            executionConfig,
            customInstructions,
            reasoningEffort,
            outputConstraints,
            promptTemplates,
            resilienceConfig
        );
        return new ReviewOrchestrator(copilotService.getClient(), config);
    }

    private ReviewAgent.PromptTemplates loadPromptTemplates() {
        return new ReviewAgent.PromptTemplates(
            loadTemplateOrDefault("agent-focus-areas-guidance.md",
                AgentPromptBuilder.DEFAULT_FOCUS_AREAS_GUIDANCE),
            loadTemplateOrDefault("local-source-header.md",
                AgentPromptBuilder.DEFAULT_LOCAL_SOURCE_HEADER),
            loadTemplateOrDefault("local-review-result-request.md",
                AgentPromptBuilder.DEFAULT_LOCAL_REVIEW_RESULT_PROMPT));
    }

    private String loadTemplateOrDefault(String templateName, String fallback) {
        try {
            String content = templateService.loadTemplateContent(templateName);
            if (content == null || content.isBlank()) return fallback;
            return content.trim();
        } catch (IllegalStateException | IllegalArgumentException e) {
            logger.debug("Template '{}' unavailable, using fallback: {}", templateName, e.getMessage());
            return fallback;
        }
    }
}
