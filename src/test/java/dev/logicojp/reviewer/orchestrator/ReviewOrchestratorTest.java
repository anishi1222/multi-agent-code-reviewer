package dev.logicojp.reviewer.orchestrator;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.CopilotClientOptions;
import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.FeatureFlags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewOrchestrator")
class ReviewOrchestratorTest {

    @Test
    @DisplayName("注入ファクトリ経由でエージェントレビューを実行できる")
    void executesReviewsUsingInjectedFactories() {
        CopilotClient client = new CopilotClient(new CopilotClientOptions());
        var orchestratorConfig = new OrchestratorConfig(
            null,
            new GithubMcpConfig(null, null, null, null, null, null),
            new LocalFileConfig(),
            new FeatureFlags(false, false),
            ExecutionConfig.ofFlat(2, 1, 2, 1, 1, 1, 1, 1, 0, 0, 0, 0),
            "high",
            null,
            new PromptTexts(
                "focus guidance",
                "local source header",
                "local result request"
            )
        );

        AgentConfig agentConfig = new AgentConfig(
            "security", "Security", "model", "system", "instruction", null, List.of(), List.of()
        );

        try (ReviewOrchestrator orchestrator = new ReviewOrchestrator(
            client,
            orchestratorConfig,
            (config, context) -> target -> ReviewResult.builder()
                .agentConfig(config)
                .repository(target.displayName())
                .content("ok")
                .success(true)
                .timestamp(Instant.now())
                .build(),
            (directory, localConfig) -> () -> new dev.logicojp.reviewer.target.LocalFileProvider.CollectionResult(
                "source",
                "summary",
                1,
                10
            )
        )) {
            var results = orchestrator.executeReviews(
                Map.of("security", agentConfig),
                ReviewTarget.gitHub("owner/repo")
            );

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().success()).isTrue();
            assertThat(results.getFirst().content()).isEqualTo("ok");
        } finally {
            client.close();
        }
    }
}
