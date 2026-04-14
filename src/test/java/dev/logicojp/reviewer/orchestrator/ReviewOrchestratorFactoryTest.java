package dev.logicojp.reviewer.orchestrator;

import com.github.copilot.sdk.CopilotClient;
import dev.logicojp.reviewer.agent.AgentPromptBuilder;
import dev.logicojp.reviewer.agent.CircuitBreakerFactory;
import dev.logicojp.reviewer.agent.SharedCircuitBreaker;
import dev.logicojp.reviewer.config.CircuitBreakerConfig;
import dev.logicojp.reviewer.config.CopilotConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.service.CopilotCliHealthChecker;
import dev.logicojp.reviewer.service.CopilotCliPathResolver;
import dev.logicojp.reviewer.service.CopilotClientStarter;
import dev.logicojp.reviewer.service.CopilotService;
import dev.logicojp.reviewer.service.CopilotStartupErrorFormatter;
import dev.logicojp.reviewer.service.CopilotTimeoutResolver;
import dev.logicojp.reviewer.service.TemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewOrchestratorFactory")
class ReviewOrchestratorFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("テンプレート未配置時はフォールバック文言でOrchestratorConfigを構築する")
    void usesFallbackPromptsWhenTemplatesAreUnavailable() {
        TemplateService templateService = new TemplateService(new TemplateConfig(tempDir.toString(),
            null, null, null, null, null, null, null, null));

        CopilotConfig copilotConfig = new CopilotConfig(null, null, 60, 10, 15);
        CopilotService copilotService = new CopilotService(
            new CopilotCliPathResolver(copilotConfig, System.getenv("PATH")),
            new CopilotCliHealthChecker(new CopilotTimeoutResolver(copilotConfig)),
            copilotConfig,
            new CopilotStartupErrorFormatter(),
            new CopilotClientStarter()
        ) {
            @Override
            public CopilotClient getClient() {
                return null;
            }
        };

        AtomicReference<OrchestratorConfig> captured = new AtomicReference<>();

        ReviewOrchestratorFactory factory = new ReviewOrchestratorFactory(
            copilotService,
            new GithubMcpConfig(null, null, null, null, null, null),
            new LocalFileConfig(),
            new CircuitBreakerFactory(new CircuitBreakerConfig(
                CircuitBreakerConfig.DEFAULT_FAILURE_THRESHOLD,
                CircuitBreakerConfig.DEFAULT_RESET_TIMEOUT_MS
            )),
            templateService,
            (client, config) -> {
                captured.set(config);
                return new ReviewOrchestrator(
                    client,
                    config,
                    ReviewOrchestrator.defaultCollaborators(
                        client,
                        config,
                        new SharedCircuitBreaker(
                            CircuitBreakerConfig.DEFAULT_FAILURE_THRESHOLD,
                            CircuitBreakerConfig.DEFAULT_RESET_TIMEOUT_MS
                        )
                    )
                );
            }
        );

        try (ReviewOrchestrator ignored = factory.create(
            "token",
            dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(2, 1, 5, 5, 1, 5, 5, 5, 1, 0, 0, 0),
            "high",
            "constraints",
            "2026-03-05-12-34-56"
        )) {
            assertThat(captured.get()).isNotNull();
            assertThat(captured.get().promptTexts().focusAreasGuidance()).isEqualTo(AgentPromptBuilder.DEFAULT_FOCUS_AREAS_GUIDANCE);
            assertThat(captured.get().promptTexts().localSourceHeader()).isEqualTo(AgentPromptBuilder.DEFAULT_LOCAL_SOURCE_HEADER);
            assertThat(captured.get().promptTexts().localReviewResultRequest())
                .isEqualTo("ソースコードを読み込んだ内容に基づいて、指定された出力形式でレビュー結果を返してください。");
        }
    }
}
