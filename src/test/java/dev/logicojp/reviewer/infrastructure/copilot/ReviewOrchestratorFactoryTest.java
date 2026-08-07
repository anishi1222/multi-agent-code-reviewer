package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.port.outbound.CreateReviewSessionPortsPort.ReviewSessionOptions;
import dev.logicojp.reviewer.infrastructure.auth.CopilotCliPathResolver;
import dev.logicojp.reviewer.infrastructure.config.CopilotConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewOrchestratorFactory")
class ReviewOrchestratorFactoryTest {

    @Test
    @DisplayName("呼び出し単位でCopilotとRubber Duckのoutboundアダプタを構築する")
    void createsInvocationScopedSessionAdapters() {
        var factory = factory();
        var options = new ReviewSessionOptions(7, "2026-03-05-12-34-56");

        var first = factory.create(options);
        var second = factory.create(options);

        assertThat(first.runCopilotSession()).isInstanceOf(ReviewSessionExecutor.class);
        assertThat(first.runRubberDuckSession()).isInstanceOf(RubberDuckDialogueExecutor.class);
        assertThat(second.runCopilotSession()).isNotSameAs(first.runCopilotSession());
        assertThat(second.runRubberDuckSession()).isNotSameAs(first.runRubberDuckSession());
    }

    private ReviewOrchestratorFactory factory() {
        CopilotConfig copilotConfig = new CopilotConfig(null, null, 60, 10, 15);
        CopilotService copilotService = new CopilotService(
            new CopilotCliPathResolver(copilotConfig),
            new CopilotHealthProbe(copilotConfig),
            copilotConfig,
            new CopilotStartupErrorFormatter(),
            new CopilotClientStarter()
        );
        return new ReviewOrchestratorFactory(
            copilotService,
            new ReviewSessionConfigFactory()
        );
    }
}
