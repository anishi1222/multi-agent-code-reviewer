package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.orchestrator.ReviewOrchestrator;
import dev.logicojp.reviewer.orchestrator.ReviewOrchestratorFactory;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/// Service for executing code reviews with multiple agents.
@Singleton
public class ReviewService {

    @FunctionalInterface
    interface OrchestratorRunner {
        List<ReviewResult> run(Map<String, AgentConfig> agentConfigs,
                               ReviewTarget target,
                               String githubToken,
                               ExecutionConfig executionConfig,
                               String reasoningEffort,
                               String outputConstraints,
                               String invocationTimestamp);
    }
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewService.class);
    
    private final ExecutionConfig executionConfig;
    private final TemplateService templateService;
    private final OrchestratorRunner orchestratorRunner;
    
    @Inject
    public ReviewService(ReviewOrchestratorFactory orchestratorFactory,
                         ExecutionConfig executionConfig,
                         TemplateService templateService) {
        this(
            orchestratorFactory,
            executionConfig,
            templateService,
            (agentConfigs, target, githubToken, overriddenConfig, reasoningEffort, outputConstraints,
             invocationTimestamp) -> {
                try (ReviewOrchestrator orchestrator = orchestratorFactory.create(
                    githubToken,
                    overriddenConfig,
                    reasoningEffort,
                    outputConstraints,
                    invocationTimestamp
                )) {
                    return orchestrator.executeReviews(agentConfigs, target);
                }
            }
        );
    }

    ReviewService(ReviewOrchestratorFactory orchestratorFactory,
                  ExecutionConfig executionConfig,
                  TemplateService templateService,
                  OrchestratorRunner orchestratorRunner) {
        this.executionConfig = executionConfig;
        this.templateService = templateService;
        this.orchestratorRunner = orchestratorRunner;
    }
    
    /// Executes reviews with all specified agents in parallel.
    /// @param agentConfigs Map of agent configurations
    /// @param target Target to review (GitHub repository or local directory)
    /// @param githubToken GitHub authentication token (required for GitHub targets)
    /// @param parallelism Number of parallel agents (overrides config)
    /// @param reasoningEffort Reasoning effort level for reasoning models (optional)
    /// @return List of review results from all agents
    public List<ReviewResult> executeReviews(
            Map<String, AgentConfig> agentConfigs,
            ReviewTarget target,
            @Nullable String githubToken,
            int parallelism,
            @Nullable String reasoningEffort,
            boolean noSharedSession,
            String invocationTimestamp) {
        
        logger.info("Executing reviews for {} agents on target: {}", 
            agentConfigs.size(), target.displayName());
        ExecutionConfig overriddenConfig = overrideExecution(parallelism, noSharedSession);
        String outputConstraints = loadOutputConstraints();

        return orchestratorRunner.run(
            agentConfigs,
            target,
            githubToken,
            overriddenConfig,
            reasoningEffort,
            outputConstraints,
            invocationTimestamp
        );
    }

    private ExecutionConfig overrideExecution(int parallelism, boolean noSharedSession) {
        return executionConfig
            .withParallelism(parallelism)
            .withSharedSessionEnabled(!noSharedSession);
    }

    private String loadOutputConstraints() {
        String outputConstraints = templateService.getOutputConstraints();
        if (outputConstraints != null && !outputConstraints.isBlank()) {
            logger.info("Loaded output constraints from template ({} chars)", outputConstraints.length());
        }
        return outputConstraints;
    }
}
