package dev.logicojp.reviewer.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApplicationSettingsAdapter")
class ApplicationSettingsAdapterTest {

    @Test
    @DisplayName("Micronaut設定をframework-freeなapplication設定へ写像する")
    void mapsFrameworkConfigurationToApplicationSettings() {
        ModelConfig modelConfig = new ModelConfig();
        ExecutionConfig executionConfig = new ExecutionConfig(
            new ExecutionConfig.ConcurrencySettings(3, 4),
            null,
            null,
            null,
            true,
            true);
        SummaryConfig summaryConfig = new SummaryConfig(11, 22, 33, 44, 55, 66);
        PromptBudgetConfig promptBudgetConfig = new PromptBudgetConfig();

        var adapter = new ApplicationSettingsAdapter(
            executionConfig,
            modelConfig,
            summaryConfig,
            promptBudgetConfig);

        assertThat(adapter.defaultSkillModel()).isEqualTo(modelConfig.defaultModel());
        assertThat(adapter.ghAuthFallbackEnabled()).isTrue();
        assertThat(adapter.reviewPasses()).isEqualTo(4);
        assertThat(adapter.defaultParallelism()).isEqualTo(3);
        assertThat(adapter.defaultReviewModel()).isEqualTo(modelConfig.reviewModel());
        assertThat(adapter.defaultReportModel()).isEqualTo(modelConfig.reportModel());
        assertThat(adapter.defaultSummaryModel()).isEqualTo(modelConfig.summaryModel());
        assertThat(adapter.defaultReasoningEffort()).isEqualTo(modelConfig.reasoningEffort());
        assertThat(adapter.summarySettings())
            .extracting(
                settings -> settings.maxContentPerAgent(),
                settings -> settings.maxTotalPromptContent(),
                settings -> settings.fallbackExcerptLength(),
                settings -> settings.averageResultContentEstimate(),
                settings -> settings.initialBufferMargin(),
                settings -> settings.excerptNormalizationMultiplier())
            .containsExactly(11, 22, 33, 44, 55, 66);
        assertThat(adapter.summarySettings().promptBudget())
            .isEqualTo(promptBudgetConfig.toPromptBudget());
    }
}
