package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.outbound.RunCopilotSessionPort;
import dev.logicojp.reviewer.application.port.outbound.SessionRequest;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.report.ReviewResultFactory;
import dev.logicojp.reviewer.domain.review.ReviewContext;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
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
        RecordingCopilotSession copilotSession = new RecordingCopilotSession();
        ReviewContext ctx = context(false, null);
        ReviewPassRunner runner = runner(copilotSession);

        List<ReviewResult> results = runner.run(config, ReviewTarget.gitHub("owner/repo"), ctx, 3, List.of(), 0);

        assertThat(results).hasSize(3);
        assertThat(results).extracting(ReviewResult::content)
            .containsExactly("pass-1", "pass-2", "pass-3");
        assertThat(results).extracting(ReviewResult::passNumber)
            .containsExactly(1, 2, 3);
        assertThat(copilotSession.requests()).hasSize(3);
    }

    @Test
    @DisplayName("hybrid mode: isolated parallel passes receive local source content")
    void hybridParallelPassesReceiveLocalSourceContent() {
        AgentConfig config = agentConfig();
        RecordingCopilotSession copilotSession = new RecordingCopilotSession();
        ReviewContext ctx = context(true, "SOURCE");
        ReviewPassRunner runner = runner(copilotSession);

        List<ReviewResult> results = runner.run(config, ReviewTarget.local(java.nio.file.Path.of("repo")), ctx, 3, List.of(), 0);

        assertThat(results).hasSize(3);
        assertThat(copilotSession.requests()).hasSize(3);
        assertThat(copilotSession.requests())
            .extracting(SessionRequest::prompt)
            .allSatisfy(prompt -> assertThat(prompt).contains("SOURCE"));
    }

    @Test
    @DisplayName("single review delegates to pass 1 of 1")
    void singleReviewUsesPassOneOfOne() {
        AgentConfig config = agentConfig();
        RecordingCopilotSession copilotSession = new RecordingCopilotSession();
        ReviewContext ctx = context(false, null);
        ReviewPassRunner runner = runner(copilotSession);

        ReviewResult result = runner.run(config, ReviewTarget.gitHub("owner/repo"), ctx, 1, List.of(), 0).getFirst();

        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("pass-1");
        assertThat(result.passNumber()).isZero();
        assertThat(copilotSession.requests()).singleElement()
            .satisfies(request -> assertThat(request.prompt()).contains("instruction"));
    }

    private ReviewPassRunner runner(RecordingCopilotSession copilotSession) {
        return new ReviewPassRunner(copilotSession, new ReviewResultFactory());
    }

    private ReviewContext context(boolean sharedSessionEnabled, String cachedSourceContent) {
        return ReviewContext.builder()
            .invocationTimestamp("2026-06-24-14-00-00")
            .sharedSessionEnabled(sharedSessionEnabled)
            .maxRetries(0)
            .cachedSourceContent(cachedSourceContent)
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

    private static final class RecordingCopilotSession implements RunCopilotSessionPort {
        private final AtomicInteger counter = new AtomicInteger();
        private final List<SessionRequest> requests = java.util.Collections.synchronizedList(new ArrayList<>());

        @Override
        public String runSession(SessionRequest request) {
            requests.add(request);
            return "pass-" + counter.incrementAndGet();
        }

        List<SessionRequest> requests() {
            return List.copyOf(requests);
        }
    }
}
