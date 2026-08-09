package dev.logicojp.reviewer.infrastructure.config;

import dev.logicojp.reviewer.application.port.outbound.ResolveApplicationSettingsPort;
import jakarta.inject.Singleton;

import java.util.Objects;

/// Maps Micronaut-bound configuration onto framework-free application settings.
@Singleton
public final class ApplicationSettingsAdapter implements ResolveApplicationSettingsPort {

    private final ExecutionConfig execution;
    private final ModelConfig models;
    private final SummaryConfig summary;
    private final PromptBudgetConfig promptBudget;

    public ApplicationSettingsAdapter(ExecutionConfig execution,
                                      ModelConfig models,
                                      SummaryConfig summary,
                                      PromptBudgetConfig promptBudget) {
        this.execution = Objects.requireNonNull(execution, "execution must not be null");
        this.models = Objects.requireNonNull(models, "models must not be null");
        this.summary = Objects.requireNonNull(summary, "summary must not be null");
        this.promptBudget = promptBudget != null ? promptBudget : new PromptBudgetConfig();
    }

    @Override
    public String defaultSkillModel() {
        return models.defaultModel();
    }

    @Override
    public SummarySettings summarySettings() {
        return new SummarySettings(
            summary.maxContentPerAgent(),
            summary.maxTotalPromptContent(),
            summary.fallbackExcerptLength(),
            summary.averageResultContentEstimate(),
            summary.initialBufferMargin(),
            summary.excerptNormalizationMultiplier(),
            promptBudget.toPromptBudget()
        );
    }

    @Override
    public boolean ghAuthFallbackEnabled() {
        return execution.isGhAuthFallbackEnabled();
    }

    @Override
    public int defaultParallelism() {
        return execution.parallelism();
    }

    @Override
    public String defaultReviewModel() {
        return models.reviewModel();
    }

    @Override
    public String defaultReportModel() {
        return models.reportModel();
    }

    @Override
    public String defaultSummaryModel() {
        return models.summaryModel();
    }

    @Override
    public String defaultReasoningEffort() {
        return models.reasoningEffort();
    }

    @Override
    public int reviewPasses() {
        return execution.reviewPasses();
    }
}
