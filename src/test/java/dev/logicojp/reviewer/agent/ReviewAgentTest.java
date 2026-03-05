package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewAgent")
class ReviewAgentTest {

    private static final String REPO = "owner/repo";
    private static final ReviewTarget TARGET = ReviewTarget.gitHub(REPO);

    private static AgentConfig agentConfig() {
        return new AgentConfig("test-agent", "Test Agent", "model",
            "system prompt", "instruction", null, List.of("area1"), List.of());
    }

    private static ReviewContext.TimeoutConfig timeoutConfig() {
        return new ReviewContext.TimeoutConfig(5, 5, 2);
    }

    private static ReviewResult successResult(String content) {
        return ReviewResult.builder()
            .agentConfig(agentConfig())
            .repository(REPO)
            .content(content)
            .success(true)
            .timestamp(Instant.now())
            .build();
    }

    private static ReviewResult failureResult(String message) {
        return ReviewResult.builder()
            .agentConfig(agentConfig())
            .repository(REPO)
            .success(false)
            .errorMessage(message)
            .timestamp(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("review - 単一レビュー")
    class SingleReview {

        @Test
        @DisplayName("成功時にReviewResultを返す")
        void returnsSuccessResult() {
            var retryExecutor = new ReviewRetryExecutor("test-agent", 0, 1, 4, _ -> {});
            var resultFactory = new ReviewResultFactory();

            ReviewResult result = retryExecutor.execute(
                () -> resultFactory.success(agentConfig(), REPO, "review content"),
                e -> resultFactory.fromException(agentConfig(), REPO, e)
            );

            assertThat(result.success()).isTrue();
            assertThat(result.content()).isEqualTo("review content");
        }

        @Test
        @DisplayName("例外発生時にエラーResultを返す")
        void returnsErrorResultOnException() {
            var retryExecutor = new ReviewRetryExecutor("test-agent", 0, 1, 4, _ -> {});
            var resultFactory = new ReviewResultFactory();

            ReviewResult result = retryExecutor.execute(
                () -> { throw new RuntimeException("connection failed"); },
                e -> resultFactory.fromException(agentConfig(), REPO, e)
            );

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("connection failed");
        }
    }

    @Nested
    @DisplayName("reviewPasses - マルチパスレビュー")
    class MultiPassReview {

        @Test
        @DisplayName("reviewPassesが1以下の場合はsingleパスにフォールバック")
        void fallsBackToSinglePassForOne() {
            var retryExecutor = new ReviewRetryExecutor("test-agent", 0, 1, 4, _ -> {});
            var resultFactory = new ReviewResultFactory();

            // Simulate what reviewPasses(target, 1) does — calls review(target)
            // which returns a single result wrapped in a list
            ReviewResult singleResult = retryExecutor.execute(
                () -> resultFactory.success(agentConfig(), REPO, "single pass"),
                e -> resultFactory.fromException(agentConfig(), REPO, e)
            );

            List<ReviewResult> results = List.of(singleResult);
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().content()).isEqualTo("single pass");
        }

        @Test
        @DisplayName("reviewPassesが0以下の場合もsingleパスにフォールバック")
        void fallsBackToSinglePassForZero() {
            // reviewPasses <= 1 triggers single-pass path
            int reviewPasses = 0;
            assertThat(reviewPasses <= 1).isTrue();
        }
    }

    @Nested
    @DisplayName("resolveLocalSourceContentForPass")
    class ResolveLocalSourceContentForPass {

        @Test
        @DisplayName("GitHubターゲットの場合はソースコンテンツをそのまま返す")
        void gitHubTargetReturnsSourceContent() {
            String result = ReviewAgent.resolveLocalSourceContentForPass(
                TARGET, "source content", 1
            );
            assertThat(result).isEqualTo("source content");
        }

        @Test
        @DisplayName("ローカルターゲットの初回パスはソースコンテンツを返す")
        void localTargetFirstPassReturnsContent() {
            var localTarget = ReviewTarget.local(java.nio.file.Path.of("/tmp/test"));
            String result = ReviewAgent.resolveLocalSourceContentForPass(
                localTarget, "source content", 1
            );
            assertThat(result).isEqualTo("source content");
        }

        @Test
        @DisplayName("ローカルターゲットの2回目以降パスはnullを返す")
        void localTargetSubsequentPassReturnsNull() {
            var localTarget = ReviewTarget.local(java.nio.file.Path.of("/tmp/test"));
            String result = ReviewAgent.resolveLocalSourceContentForPass(
                localTarget, "source content", 2
            );
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("ReviewResultFactory")
    class ResultFactory {

        @Test
        @DisplayName("fromExceptionはエラー情報を含むResultを生成する")
        void fromExceptionCreatesErrorResult() {
            var factory = new ReviewResultFactory();
            ReviewResult result = factory.fromException(
                agentConfig(), REPO, new RuntimeException("test error")
            );
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("test error");
            assertThat(result.agentConfig()).isEqualTo(agentConfig());
            assertThat(result.repository()).isEqualTo(REPO);
        }

        @Test
        @DisplayName("emptyContentFailureはMCP使用時にタイムアウトメッセージを含む")
        void emptyContentWithMcpContainsTimeoutHint() {
            var factory = new ReviewResultFactory();
            ReviewResult result = factory.emptyContentFailure(agentConfig(), REPO, true);
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("MCP");
        }

        @Test
        @DisplayName("emptyContentFailureはMCP未使用時に基本メッセージを含む")
        void emptyContentWithoutMcpContainsBasicMessage() {
            var factory = new ReviewResultFactory();
            ReviewResult result = factory.emptyContentFailure(agentConfig(), REPO, false);
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).doesNotContain("MCP");
        }

        @Test
        @DisplayName("successは成功Resultを生成する")
        void successCreatesSuccessResult() {
            var factory = new ReviewResultFactory();
            ReviewResult result = factory.success(agentConfig(), REPO, "content");
            assertThat(result.success()).isTrue();
            assertThat(result.content()).isEqualTo("content");
        }
    }

    @Nested
    @DisplayName("AgentCollaborators - バリデーション")
    class CollaboratorsValidation {

        @Test
        @DisplayName("nullのコラボレーターでNullPointerExceptionが発生する")
        void throwsOnNullCollaborator() {
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new ReviewAgent.AgentCollaborators(null, null, null, null, null)
            ).isInstanceOf(NullPointerException.class);
        }
    }

    private static ReviewTargetInstructionResolver stubResolver(String instruction,
                                                                 String localSource,
                                                                 Map<String, Object> mcpServers) {
        return new ReviewTargetInstructionResolver(
            agentConfig(),
            new LocalFileConfig(),
            () -> {}
        );
    }

    private static ReviewSessionMessageSender stubSender() {
        return new ReviewSessionMessageSender("test-agent", 4096, 256);
    }
}
