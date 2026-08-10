package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.port.outbound.ResolveReviewSettingsPort.ReviewSettings;
import dev.logicojp.reviewer.application.port.outbound.ResolveReviewSettingsPort.ReviewSettingsInput;
import dev.logicojp.reviewer.infrastructure.config.ExecutionConfig;
import dev.logicojp.reviewer.infrastructure.config.ModelConfig;
import dev.logicojp.reviewer.infrastructure.config.PromptBudgetConfig;
import dev.logicojp.reviewer.infrastructure.config.RubberDuckConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Review configuration outbound mapping")
class ReviewConfigurationMappingTest {

    @Test
    @DisplayName("reasoning effort未指定時はモデル設定の値を使う")
    void usesConfiguredReasoningEffortWhenOverrideIsBlank() {
        var adapter = new ReviewContextFactory(
            ExecutionConfig.defaults(),
            new ModelConfig(null, null, null, "high", "claude-sonnet-4.5"),
            new RubberDuckConfig(false, 1, "model-b", "last-responder"),
            new PromptBudgetConfig()
        );

        var config = adapter.resolve(new ReviewSettingsInput(" "));

        assertThat(config.reasoningEffort()).isEqualTo("high");
        assertThat(config.rubberDuckEnabled()).isFalse();
    }

    @Test
    @DisplayName("設定境界はrequest credentialやtimestampを運ばない")
    void doesNotCarryRequestCredentialsAcrossTheSettingsBoundary() {
        assertThat(Arrays.stream(ReviewSettings.class.getRecordComponents())
            .map(component -> component.getName()))
            .doesNotContain("githubToken", "invocationTimestamp", "outputConstraints");
    }
}
