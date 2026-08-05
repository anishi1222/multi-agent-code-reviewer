package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.infrastructure.logging.MdcCorrelationAdapter;
import dev.logicojp.reviewer.application.review.OrchestratorConfig;
import dev.logicojp.reviewer.domain.review.PromptTexts;
import dev.logicojp.reviewer.infrastructure.auth.CopilotCliPathResolver;
import dev.logicojp.reviewer.infrastructure.config.CopilotConfig;
import dev.logicojp.reviewer.infrastructure.config.ExecutionConfig;
import dev.logicojp.reviewer.infrastructure.config.ModelConfig;
import dev.logicojp.reviewer.infrastructure.config.RubberDuckConfig;
import dev.logicojp.reviewer.infrastructure.config.TemplateConfig;
import dev.logicojp.reviewer.infrastructure.file.LocalFileProvider;
import dev.logicojp.reviewer.infrastructure.template.TemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewOrchestratorFactory")
class ReviewOrchestratorFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("インフラ設定からOrchestratorConfigを構築する")
    void buildsOrchestratorConfigFromInfrastructureSettings() {
        ReviewOrchestratorFactory factory = factory(
            new ExecutionConfig(
                new ExecutionConfig.ConcurrencySettings(3, 2),
                new ExecutionConfig.TimeoutSettings(11, 7, 5, 4, 6, 10),
                new ExecutionConfig.RetrySettings(1),
                null,
                true,
                false
            ),
            new ModelConfig(null, null, null, "high", "claude-sonnet-4.5"),
            new RubberDuckConfig(true, 3, "model-b", "last-responder")
        );

        OrchestratorConfig config = factory.buildConfig(
            "token",
            "2026-03-05-12-34-56",
            "medium",
            "constraints"
        );

        assertThat(config.githubToken()).isEqualTo("token");
        assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(11);
        assertThat(config.agentTimeoutMinutes()).isEqualTo(7);
        assertThat(config.reviewPasses()).isEqualTo(2);
        assertThat(config.maxRetries()).isEqualTo(1);
        assertThat(config.reasoningEffort()).isEqualTo("medium");
        assertThat(config.outputConstraints()).isEqualTo("constraints");
        assertThat(config.invocationTimestamp()).isEqualTo("2026-03-05-12-34-56");
        assertThat(config.rubberDuckEnabled()).isTrue();
        assertThat(config.rubberDuckRounds()).isEqualTo(3);
        assertThat(config.promptTexts()).isEqualTo(new PromptTexts(null, null, null));
    }

    @Test
    @DisplayName("reasoning effort未指定時はモデル設定の値を使う")
    void usesConfiguredReasoningEffortWhenOverrideIsBlank() {
        ReviewOrchestratorFactory factory = factory(
            ExecutionConfig.defaults(),
            new ModelConfig(null, null, null, "high", "claude-sonnet-4.5"),
            new RubberDuckConfig(false, 1, "model-b", "last-responder")
        );

        OrchestratorConfig config = factory.buildConfig("token", "time", " ", null);

        assertThat(config.reasoningEffort()).isEqualTo("high");
        assertThat(config.rubberDuckEnabled()).isFalse();
    }

    // removed: template fallback prompt assertions moved out of ReviewOrchestratorFactory; templates are now loaded through LoadTemplatePort.

    private ReviewOrchestratorFactory factory(ExecutionConfig executionConfig,
                                              ModelConfig modelConfig,
                                              RubberDuckConfig rubberDuckConfig) {
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
            new LocalFileProvider(),
            new TemplateRepository(new TemplateConfig(tempDir.toString(), null, null, null, null, null, null, null, null)),
            executionConfig,
            modelConfig,
            rubberDuckConfig,
            new ReviewSessionConfigFactory(),
            new MdcCorrelationAdapter()
        );
    }
}
