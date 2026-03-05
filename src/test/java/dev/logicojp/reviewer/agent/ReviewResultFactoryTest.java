package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.report.core.ReviewResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewResultFactory")
class ReviewResultFactoryTest {

    private static final String REPOSITORY = "owner/repo";
    private static final AgentConfig AGENT_CONFIG = new AgentConfig(
        "test-agent", "テストエージェント", "model",
        "system prompt", "instruction", null,
        List.of("area"), List.of()
    );

    private final ReviewResultFactory factory = new ReviewResultFactory();

    @Nested
    @DisplayName("fromException")
    class FromException {

        @Test
        @DisplayName("例外からエラーのReviewResultを生成する")
        void createsFailureResultFromException() {
            RuntimeException exception = new RuntimeException("Something went wrong");

            ReviewResult result = factory.fromException(AGENT_CONFIG, REPOSITORY, exception);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("Something went wrong");
            assertThat(result.agentConfig()).isEqualTo(AGENT_CONFIG);
            assertThat(result.repository()).isEqualTo(REPOSITORY);
            assertThat(result.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("メッセージのない例外でもnullが設定される")
        void handlesNullExceptionMessage() {
            RuntimeException exception = new RuntimeException((String) null);

            ReviewResult result = factory.fromException(AGENT_CONFIG, REPOSITORY, exception);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("emptyContentFailure")
    class EmptyContentFailure {

        @Test
        @DisplayName("MCP使用時は専用のエラーメッセージを返す")
        void returnsSpecificMessageForMcpTimeout() {
            ReviewResult result = factory.emptyContentFailure(AGENT_CONFIG, REPOSITORY, true);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("MCP tool calls");
            assertThat(result.agentConfig()).isEqualTo(AGENT_CONFIG);
            assertThat(result.repository()).isEqualTo(REPOSITORY);
            assertThat(result.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("MCP未使用時は一般的なエラーメッセージを返す")
        void returnsGenericMessageWithoutMcp() {
            ReviewResult result = factory.emptyContentFailure(AGENT_CONFIG, REPOSITORY, false);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("Agent returned empty review content");
            assertThat(result.errorMessage()).doesNotContain("MCP");
        }
    }

    @Nested
    @DisplayName("success")
    class Success {

        @Test
        @DisplayName("成功のReviewResultを生成する")
        void createsSuccessResult() {
            String content = "# Review Findings\n\nNo issues found.";

            ReviewResult result = factory.success(AGENT_CONFIG, REPOSITORY, content);

            assertThat(result.success()).isTrue();
            assertThat(result.content()).isEqualTo(content);
            assertThat(result.agentConfig()).isEqualTo(AGENT_CONFIG);
            assertThat(result.repository()).isEqualTo(REPOSITORY);
            assertThat(result.timestamp()).isNotNull();
            assertThat(result.errorMessage()).isNull();
        }

        @Test
        @DisplayName("ツール権限エラー診断のみの出力は無効として失敗扱いにする")
        void rejectsToolAccessDiagnosticContent() {
            String content = """
                ### 1. レビュー結果

                すべてのファイルアクセスツールで権限エラーが発生しています。
                セッションの再起動を試してください。
                """;

            ReviewResult result = factory.fromContent(AGENT_CONFIG, REPOSITORY, content, true);

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("non-review content");
        }

        @Test
        @DisplayName("Priority付きの構造化レビューは成功として扱う")
        void acceptsStructuredReviewContentWithPriority() {
            String content = """
                ### 1. SQLインジェクション

                | 項目 | 内容 |
                |------|------|
                | **Priority** | High |
                """;

            ReviewResult result = factory.fromContent(AGENT_CONFIG, REPOSITORY, content, true);

            assertThat(result.success()).isTrue();
            assertThat(result.content()).contains("SQLインジェクション");
        }
    }
}
