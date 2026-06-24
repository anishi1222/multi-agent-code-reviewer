package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewPassRunner")
class ReviewPassRunnerTest {

    @Test
    @DisplayName("shared session disabled: isolated fallback passes execute all passes")
    void executesFallbackPassesWhenSharedSessionDisabled() {
        AgentConfig config = agentConfig();
        RecordingSessionExecutor sessionExecutor = new RecordingSessionExecutor(config, context(false));
        ReviewPassRunner runner = runner(config, context(false), sessionExecutor);

        List<ReviewResult> results = runner.reviewPasses(ReviewTarget.gitHub("owner/repo"), 3);

        assertThat(results).hasSize(3);
        assertThat(results).extracting(ReviewResult::content)
            .containsExactly("pass-1", "pass-2", "pass-3");
        assertThat(sessionExecutor.requests())
            .extracting(ReviewSessionExecutor.Request::currentPass)
            .containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    @DisplayName("hybrid mode: isolated parallel passes receive local source content")
    void hybridParallelPassesReceiveLocalSourceContent() {
        AgentConfig config = agentConfig();
        ReviewContext ctx = context(true);
        RecordingSessionExecutor sessionExecutor = new RecordingSessionExecutor(config, ctx);
        ReviewPassRunner runner = runner(config, ctx, sessionExecutor);

        List<ReviewResult> results = runner.reviewPasses(ReviewTarget.local(java.nio.file.Path.of("/tmp/repo")), 3);

        assertThat(results).hasSize(3);
        List<ReviewSessionExecutor.Request> requests = sessionExecutor.requests();
        assertThat(requests)
            .extracting(ReviewSessionExecutor.Request::currentPass)
            .containsExactlyInAnyOrder(1, 2, 3);
        assertThat(requests)
            .extracting(ReviewSessionExecutor.Request::localSourceContent)
            .containsOnly("SOURCE");
    }

    @Test
    @DisplayName("single review delegates to pass 1 of 1")
    void singleReviewUsesPassOneOfOne() {
        AgentConfig config = agentConfig();
        ReviewContext ctx = context(false);
        RecordingSessionExecutor sessionExecutor = new RecordingSessionExecutor(config, ctx);
        ReviewPassRunner runner = runner(config, ctx, sessionExecutor);

        ReviewResult result = runner.review(ReviewTarget.gitHub("owner/repo"));

        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("pass-1");
        assertThat(sessionExecutor.requests()).singleElement()
            .satisfies(request -> {
                assertThat(request.currentPass()).isEqualTo(1);
                assertThat(request.totalPasses()).isEqualTo(1);
            });
    }

    private ReviewPassRunner runner(AgentConfig config,
                                    ReviewContext ctx,
                                    RecordingSessionExecutor sessionExecutor) {
        return new ReviewPassRunner(
            config,
            ctx,
            new ReviewRetryExecutor(config.name(), 0, 1, 4, _ -> {}),
            new ReviewResultFactory(),
            sessionExecutor,
            _ -> new ReviewPassRunner.ResolvedReviewParams(
                "owner/repo",
                "INSTRUCTION",
                "SOURCE",
                null
            )
        );
    }

    private ReviewContext context(boolean sharedSessionEnabled) {
        return ReviewContext.builder()
            .client(new com.github.copilot.CopilotClient(new com.github.copilot.rpc.CopilotClientOptions()))
            .timeoutMinutes(1)
            .idleTimeoutMinutes(1)
            .invocationTimestamp("2026-06-24-14-00-00")
            .sharedSessionEnabled(sharedSessionEnabled)
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
        private final AtomicInteger counter = new AtomicInteger();
        private final List<Request> requests = java.util.Collections.synchronizedList(new ArrayList<>());

        private RecordingSessionExecutor(AgentConfig config, ReviewContext ctx) {
            super(
                config,
                ctx,
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
            requests.add(request);
            return success(request);
        }

        private ReviewResult success(Request request) {
            counter.incrementAndGet();
            return ReviewResult.builder()
                .agentConfig(config)
                .repository(request.displayName())
                .content("pass-" + request.currentPass())
                .success(true)
                .build();
        }

        List<Request> requests() {
            return List.copyOf(requests);
        }
    }
}
