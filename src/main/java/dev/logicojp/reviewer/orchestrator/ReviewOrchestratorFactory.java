package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.agent.CircuitBreakerFactory;
import dev.logicojp.reviewer.service.CopilotService;
import dev.logicojp.reviewer.service.TemplateService;
import dev.logicojp.reviewer.util.FeatureFlags;
import com.github.copilot.sdk.CopilotClient;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Factory for creating {@link ReviewOrchestrator} instances.
///
/// Encapsulates the injection of shared services ({@link CopilotService},
/// {@link GithubMcpConfig}) so that callers only need to provide
/// per-invocation parameters such as execution config overrides and
/// custom instructions.
@Singleton
public class ReviewOrchestratorFactory {

    @FunctionalInterface
    interface OrchestratorCreator {
        ReviewOrchestrator create(CopilotClient client, OrchestratorConfig orchestratorConfig);
    }

    private static final Logger logger = LoggerFactory.getLogger(ReviewOrchestratorFactory.class);

    private final CopilotService copilotService;
    private final GithubMcpConfig githubMcpConfig;
    private final LocalFileConfig localFileConfig;
    private final FeatureFlags featureFlags;
    private final TemplateService templateService;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final OrchestratorCreator orchestratorCreator;

    @Inject
    public ReviewOrchestratorFactory(CopilotService copilotService,
                                     GithubMcpConfig githubMcpConfig,
                                     LocalFileConfig localFileConfig,
                                     FeatureFlags featureFlags,
                                     CircuitBreakerFactory circuitBreakerFactory,
                                     TemplateService templateService) {
        this(
            copilotService,
            githubMcpConfig,
            localFileConfig,
            featureFlags,
            circuitBreakerFactory,
            templateService,
            (client, orchestratorConfig) -> {
                var collaborators = ReviewOrchestrator.defaultCollaborators(
                    client,
                    orchestratorConfig,
                    circuitBreakerFactory.forReview()
                );
                return new ReviewOrchestrator(client, orchestratorConfig, collaborators);
            }
        );
    }

    ReviewOrchestratorFactory(CopilotService copilotService,
                              GithubMcpConfig githubMcpConfig,
                              LocalFileConfig localFileConfig,
                              FeatureFlags featureFlags,
                              CircuitBreakerFactory circuitBreakerFactory,
                              TemplateService templateService,
                              OrchestratorCreator orchestratorCreator) {
        this.copilotService = copilotService;
        this.githubMcpConfig = githubMcpConfig;
        this.localFileConfig = localFileConfig;
        this.featureFlags = featureFlags;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.templateService = templateService;
        this.orchestratorCreator = orchestratorCreator;
    }

    /// Creates a new {@link ReviewOrchestrator} for a single review run.
    ///
    /// @param githubToken        GitHub authentication token
    /// @param executionConfig    Execution configuration (may have overridden parallelism)
    /// @param reasoningEffort    Reasoning effort level for reasoning models (nullable)
    /// @param outputConstraints  Output constraints template content (nullable)
    /// @return A new ReviewOrchestrator ready to execute reviews
    public ReviewOrchestrator create(@Nullable String githubToken,
                                     ExecutionConfig executionConfig,
                                     @Nullable String reasoningEffort,
                                     @Nullable String outputConstraints) {
        var orchestratorConfig = buildOrchestratorConfig(
            githubToken,
            executionConfig,
            reasoningEffort,
            outputConstraints
        );
        return createOrchestrator(orchestratorConfig);
    }

    private OrchestratorConfig buildOrchestratorConfig(String githubToken,
                                                       ExecutionConfig executionConfig,
                                                       String reasoningEffort,
                                                       String outputConstraints) {
        PromptTexts promptTexts = loadPromptTexts();

        return new OrchestratorConfig(
            githubToken,
            githubMcpConfig,
            localFileConfig,
            featureFlags,
            executionConfig,
            reasoningEffort,
            outputConstraints,
            promptTexts
        );
    }

    private PromptTexts loadPromptTexts() {
        return new PromptTexts(
            loadTemplateOrDefault(
                "agent-focus-areas-guidance.md",
                dev.logicojp.reviewer.agent.AgentPromptBuilder.DEFAULT_FOCUS_AREAS_GUIDANCE
            ),
            loadTemplateOrDefault(
                "local-source-header.md",
                dev.logicojp.reviewer.agent.AgentPromptBuilder.DEFAULT_LOCAL_SOURCE_HEADER
            ),
            loadTemplateOrDefault(
                "local-review-result-request.md",
                dev.logicojp.reviewer.agent.AgentPromptBuilder.DEFAULT_LOCAL_REVIEW_RESULT_PROMPT
            )
        );
    }

    private String loadTemplateOrDefault(String templateName, String fallback) {
        try {
            String content = templateService.loadTemplateContent(templateName);
            if (content == null || content.isBlank()) {
                return fallback;
            }
            return content.trim();
        } catch (IllegalStateException | IllegalArgumentException e) {
            logger.debug("Template '{}' unavailable, using fallback: {}", templateName, e.getMessage());
            return fallback;
        }
    }

    private ReviewOrchestrator createOrchestrator(OrchestratorConfig orchestratorConfig) {
        return orchestratorCreator.create(copilotService.getClient(), orchestratorConfig);
    }
}
