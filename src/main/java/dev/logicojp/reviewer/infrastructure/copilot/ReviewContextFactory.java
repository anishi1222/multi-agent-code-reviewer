package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.review.OrchestratorConfig;
import dev.logicojp.reviewer.domain.review.PromptTexts;
import dev.logicojp.reviewer.infrastructure.config.ExecutionConfig;
import dev.logicojp.reviewer.infrastructure.config.ModelConfig;
import dev.logicojp.reviewer.infrastructure.config.RubberDuckConfig;

import java.util.Objects;
import dev.logicojp.reviewer.infrastructure.config.PromptBudgetConfig;

/// Assembles {@link OrchestratorConfig} from Micronaut infrastructure configuration records.
///
/// Extracted from the brownfield {@code ReviewContextFactory} (which directly held a
/// {@code CopilotClient}). In the new architecture the client is not part of the context
/// — this factory only maps configuration values to the application-layer DTO.
///
/// No DI annotations — instantiated by {@link ReviewOrchestratorFactory}.
public final class ReviewContextFactory {

    private final ExecutionConfig executionConfig;
    private final ModelConfig modelConfig;
    private final RubberDuckConfig rubberDuckConfig;
    private final PromptBudgetConfig promptBudgetConfig;

    public ReviewContextFactory(ExecutionConfig executionConfig,
                                 ModelConfig modelConfig,
                                 RubberDuckConfig rubberDuckConfig,
                                 PromptBudgetConfig promptBudgetConfig) {
        this.executionConfig = Objects.requireNonNull(executionConfig);
        this.modelConfig = Objects.requireNonNull(modelConfig);
        this.rubberDuckConfig = Objects.requireNonNull(rubberDuckConfig);
        this.promptBudgetConfig = promptBudgetConfig != null ? promptBudgetConfig : new PromptBudgetConfig();
    }

    /// Builds an {@link OrchestratorConfig} for the current review invocation.
    ///
    /// @param githubToken        resolved GitHub token (never stored in config records)
    /// @param invocationTimestamp timestamp string set at CLI startup
    /// @param reasoningEffort    optional override from CLI flag (null = use config default)
    /// @param outputConstraints  optional output-constraints template content (null = none)
    public OrchestratorConfig buildOrchestratorConfig(String githubToken,
                                                       String invocationTimestamp,
                                                       String reasoningEffort,
                                                       String outputConstraints) {
        long orchTimeout = executionConfig.timeouts() != null
            ? executionConfig.timeouts().orchestratorTimeoutMinutes()
            : OrchestratorConfig.DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES;
        long agentTimeout = executionConfig.timeouts() != null
            ? executionConfig.timeouts().agentTimeoutMinutes()
            : OrchestratorConfig.DEFAULT_AGENT_TIMEOUT_MINUTES;
        int passes = executionConfig.concurrency() != null
            ? executionConfig.concurrency().reviewPasses()
            : OrchestratorConfig.DEFAULT_REVIEW_PASSES;
        int maxRetries = executionConfig.retry() != null
            ? executionConfig.retry().maxRetries()
            : OrchestratorConfig.DEFAULT_MAX_RETRIES;
        boolean sharedSession = executionConfig.sharedSessionEnabled() == null
            || executionConfig.sharedSessionEnabled();

        String effort = reasoningEffort != null && !reasoningEffort.isBlank()
            ? reasoningEffort : modelConfig.reasoningEffort();

        return OrchestratorConfig.builder()
            .promptBudget(promptBudgetConfig.toPromptBudget())
            .githubToken(githubToken)
            .invocationTimestamp(invocationTimestamp)
            .orchestratorTimeoutMinutes(orchTimeout)
            .agentTimeoutMinutes(agentTimeout)
            .reviewPasses(passes)
            .maxRetries(maxRetries)
            .sharedSessionEnabled(sharedSession)
            .reasoningEffort(effort)
            .outputConstraints(outputConstraints)
            .promptTexts(new PromptTexts(null, null, null))
            .rubberDuckEnabled(rubberDuckConfig.enabled())
            .rubberDuckRounds(rubberDuckConfig.dialogueRounds())
            .build();
    }
}
