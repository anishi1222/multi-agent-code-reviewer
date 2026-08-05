package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.inbound.ReviewRequest;
import dev.logicojp.reviewer.application.port.outbound.CollectLocalSourcePort;
import dev.logicojp.reviewer.application.port.outbound.LoadTemplatePort;
import dev.logicojp.reviewer.application.port.outbound.ManageCopilotClientPort;
import dev.logicojp.reviewer.application.port.outbound.RunCopilotSessionPort;
import dev.logicojp.reviewer.application.port.outbound.RunRubberDuckSessionPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.DialogueRound;
import dev.logicojp.reviewer.domain.review.LocalFileCandidate;
import dev.logicojp.reviewer.domain.review.LocalFileSelectionConfig;
import dev.logicojp.reviewer.domain.review.PromptTexts;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewOrchestrator")
class ReviewOrchestratorTest {

    @Test
    @DisplayName("注入ポート経由でエージェントレビューを実行できる")
    void executesReviewsUsingInjectedPorts() {
        var started = new AtomicBoolean();
        var stopped = new AtomicBoolean();
        ManageCopilotClientPort lifecycle = new ManageCopilotClientPort() {
            @Override
            public void start(String token) {
                assertThat(token).isEqualTo("secret-token");
                started.set(true);
            }

            @Override
            public void stop() {
                stopped.set(true);
            }

            @Override
            public boolean isHealthy() {
                return true;
            }
        };
        RunCopilotSessionPort copilotSession = request -> {
            assertThat(request.agentConfig().name()).isEqualTo("security");
            assertThat(request.prompt()).contains("instruction");
            return "ok";
        };
        var orchestrator = new ReviewOrchestrator(
            lifecycle,
            unusedCollectLocalSource(),
            unusedTemplates(),
            copilotSession,
            unusedRubberDuckSession(),
            OrchestratorConfig.builder()
                .githubToken("secret-token")
                .reviewPasses(1)
                .maxRetries(0)
                .invocationTimestamp("2026-03-05-12-34-56")
                .promptTexts(new PromptTexts("focus guidance", "local source header", "local result request"))
                .build()
        );

        var agentConfig = new AgentConfig(
            "security", "Security", "model", "system", "instruction", null, List.of(), List.of()
        );

        var results = orchestrator.execute(new ReviewRequest(
            ReviewTarget.gitHub("owner/repo"),
            List.of(agentConfig),
            1,
            Path.of("reports"),
            List.of(),
            null,
            false,
            "secret-token",
            "2026-03-05-12-34-56",
            null,
            false,
            false
        ));

        assertThat(started).isTrue();
        assertThat(stopped).isTrue();
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().success()).isTrue();
        assertThat(results.getFirst().content()).isEqualTo("ok");
    }

    private CollectLocalSourcePort unusedCollectLocalSource() {
        return new CollectLocalSourcePort() {
            @Override
            public List<LocalFileCandidate> collect(Path directory, LocalFileSelectionConfig config) {
                throw new IllegalStateException("should not be called for GitHub target");
            }

            @Override
            public String formatContent(List<LocalFileCandidate> candidates) {
                throw new IllegalStateException("should not be called for GitHub target");
            }
        };
    }

    private LoadTemplatePort unusedTemplates() {
        return new LoadTemplatePort() {
            @Override
            public String render(String templateKey, Map<String, String> placeholders) {
                throw new IllegalStateException("should not be called in standard review mode");
            }

            @Override
            public String loadRaw(String templateKey) {
                throw new IllegalStateException("should not be called in standard review mode");
            }
        };
    }

    private RunRubberDuckSessionPort unusedRubberDuckSession() {
        return request -> List.<DialogueRound>of();
    }
}
