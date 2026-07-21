package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewRunner")
class ReviewRunnerTest {

    @Test
    @DisplayName("解決したパラメータで単一レビューを実行する")
    void executesSingleReview() {
        AgentConfig config = agentConfig();
        ReviewContext context = context();
        RecordingSessionExecutor sessionExecutor = new RecordingSessionExecutor(config, context);
        var runner = new ReviewRunner(
            config,
            new ReviewRetryExecutor(config.name(), 0, 1, 4, _ -> {}),
            new ReviewResultFactory(),
            sessionExecutor,
            _ -> new ReviewRunner.ResolvedReviewParams(
                "owner/repo",
                "INSTRUCTION",
                "SOURCE",
                null
            )
        );

        ReviewResult result = runner.review(ReviewTarget.gitHub("owner/repo"));

        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("review-result");
        assertThat(sessionExecutor.request())
            .satisfies(request -> {
                assertThat(request.displayName()).isEqualTo("owner/repo");
                assertThat(request.instruction()).isEqualTo("INSTRUCTION");
                assertThat(request.localSourceContent()).isEqualTo("SOURCE");
            });
    }

    private ReviewContext context() {
        return ReviewContext.builder()
            .client(new com.github.copilot.CopilotClient(new com.github.copilot.rpc.CopilotClientOptions()))
            .timeoutMinutes(1)
            .idleTimeoutMinutes(1)
            .invocationTimestamp("2026-06-24-14-00-00")
            .maxRetries(0)
            .localFileConfig(new LocalFileConfig())
            .build();
    }

    private AgentConfig agentConfig() {
        return new AgentConfig(
            "test-agent",
            "Test Agent",
            "model-a",
            "system",
            "instruction",
            null,
            List.of("quality"),
            List.of()
        );
    }

    private static final class RecordingSessionExecutor extends ReviewSessionExecutor {
        private final AgentConfig config;
        private Request request;

        private RecordingSessionExecutor(AgentConfig config, ReviewContext context) {
            super(
                config,
                context,
                new ReviewSystemPromptFormatter(),
                new ReviewSessionMessageSender(config.name()),
                new ReviewSessionConfigFactory(),
                new ReviewResultFactory(),
                "",
                "",
                ""
            );
            this.config = config;
        }

        @Override
        ReviewResult execute(Request request) {
            this.request = request;
            return ReviewResult.builder()
                .agentConfig(config)
                .repository(request.displayName())
                .content("review-result")
                .success(true)
                .build();
        }

        Request request() {
            return request;
        }
    }
}
