package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.port.outbound.ResolveReviewSettingsPort;
import dev.logicojp.reviewer.application.port.outbound.ResolveReviewSettingsPort.ReviewSettings;
import dev.logicojp.reviewer.application.port.outbound.ResolveReviewSettingsPort.ReviewSettingsInput;
import dev.logicojp.reviewer.infrastructure.config.ExecutionConfig;
import dev.logicojp.reviewer.infrastructure.config.ModelConfig;
import dev.logicojp.reviewer.infrastructure.config.PromptBudgetConfig;
import dev.logicojp.reviewer.infrastructure.config.RubberDuckConfig;
import jakarta.inject.Singleton;

import java.util.Objects;

/// Maps Micronaut configuration records onto framework-free review settings.
///
/// Extracted from the brownfield {@code ReviewContextFactory} (which directly held a
/// {@code CopilotClient}). In the new architecture the client is not part of the context
/// — this outbound adapter only maps configuration values to a port DTO. Credentials and
/// invocation timestamps never cross this boundary.
@Singleton
public final class ReviewContextFactory implements ResolveReviewSettingsPort {

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

    @Override
    public ReviewSettings resolve(ReviewSettingsInput input) {
        Objects.requireNonNull(input, "input must not be null");
        String effort = input.reasoningEffortOverride() != null
                && !input.reasoningEffortOverride().isBlank()
            ? input.reasoningEffortOverride() : modelConfig.reasoningEffort();

        return new ReviewSettings(
            executionConfig.orchestratorTimeoutMinutes(),
            executionConfig.agentTimeoutMinutes(),
            executionConfig.reviewPasses(),
            executionConfig.maxRetries(),
            executionConfig.isSharedSessionEnabled(),
            effort,
            rubberDuckConfig.enabled(),
            rubberDuckConfig.dialogueRounds(),
            promptBudgetConfig.toPromptBudget()
        );
    }
}
