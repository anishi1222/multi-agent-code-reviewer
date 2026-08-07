package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.port.outbound.ResolveReviewSettingsPort.ReviewSettingsInput;
import dev.logicojp.reviewer.infrastructure.config.ExecutionConfig;
import dev.logicojp.reviewer.infrastructure.config.ModelConfig;
import dev.logicojp.reviewer.infrastructure.config.RubberDuckConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import dev.logicojp.reviewer.infrastructure.config.PromptBudgetConfig;

@DisplayName("ReviewContextFactory")
class ReviewContextFactoryTest {

    @Test
    @DisplayName("設定値をframework-freeなReviewSettingsへ写像する")
    void createsContextWithConfiguredValues() {
        var executionConfig = new ExecutionConfig(
            new ExecutionConfig.ConcurrencySettings(2, 1),
            new ExecutionConfig.TimeoutSettings(10, 5, 3, 5, 5, 10),
            new ExecutionConfig.RetrySettings(2),
            new ExecutionConfig.BufferSettings(32),
            true,
            false
        );
        var modelConfig = new ModelConfig(null, null, null, "medium", "model");
        var rubberDuckConfig = new RubberDuckConfig(true, 4, "peer-model", "last-responder");
        var factory = new ReviewContextFactory(executionConfig, modelConfig, rubberDuckConfig, new PromptBudgetConfig());

        var context = factory.resolve(new ReviewSettingsInput("high"));

        assertThat(context.orchestratorTimeoutMinutes()).isEqualTo(10);
        assertThat(context.agentTimeoutMinutes()).isEqualTo(5);
        assertThat(context.reviewPasses()).isEqualTo(1);
        assertThat(context.maxRetries()).isEqualTo(2);
        assertThat(context.sharedSessionEnabled()).isTrue();
        assertThat(context.reasoningEffort()).isEqualTo("high");
        assertThat(context.rubberDuckEnabled()).isTrue();
        assertThat(context.rubberDuckRounds()).isEqualTo(4);
    }
}
