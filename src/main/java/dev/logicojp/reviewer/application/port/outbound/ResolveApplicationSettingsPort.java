package dev.logicojp.reviewer.application.port.outbound;

import dev.logicojp.reviewer.shared.PromptBudget;

/// Outbound port over framework-owned settings needed while wiring application use cases.
///
/// The implementation maps Micronaut configuration records onto these framework-free values.
public interface ResolveApplicationSettingsPort {

    String defaultSkillModel();

    SummarySettings summarySettings();

    boolean ghAuthFallbackEnabled();

    int defaultParallelism();

    String defaultReviewModel();

    String defaultReportModel();

    String defaultSummaryModel();

    String defaultReasoningEffort();

    int reviewPasses();

    /// Framework-free summary-generation settings.
    record SummarySettings(
        int maxContentPerAgent,
        int maxTotalPromptContent,
        int fallbackExcerptLength,
        int averageResultContentEstimate,
        int initialBufferMargin,
        int excerptNormalizationMultiplier,
        PromptBudget promptBudget
    ) {
        public SummarySettings {
            promptBudget = promptBudget != null ? promptBudget : new PromptBudget();
        }
    }
}
