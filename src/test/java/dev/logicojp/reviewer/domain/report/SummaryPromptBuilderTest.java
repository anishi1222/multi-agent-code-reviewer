package dev.logicojp.reviewer.domain.report;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SummaryPromptBuilder")
class SummaryPromptBuilderTest {

    @Test
    @DisplayName("成功結果は最大サイズまで連結され超過分は切り詰められる")
    void truncatesAndLimitsPromptContent() {
        var builder = new SummaryPromptBuilder(
            "repo=${repository}\n${results}",
            "${displayName}:${content}\n",
            "ERR:${displayName}:${errorMessage}\n",
            20,
            25,
            8192,
            4096
        );

        var result1 = successResult("A", "123456789012345678901234567890");
        var result2 = successResult("B", "abcdefghij");

        String prompt = builder.buildSummaryPrompt(List.of(result1, result2), "owner/repo");

        assertThat(prompt).contains("repo=owner/repo");
        assertThat(prompt).contains("A:12345678901234567890");
        assertThat(prompt).contains("... (truncated for summary)");
        assertThat(prompt).doesNotContain("B:abcdefghij");
    }

    @Test
    @DisplayName("失敗結果はエントリとして出力される")
    void includesErrorEntries() {
        var builder = new SummaryPromptBuilder(
            "repo=${repository}\n${results}",
            "${displayName}:${content}\n",
            "ERR:${displayName}:${errorMessage}\n",
            50,
            200,
            8192,
            4096
        );

        var errorResult = new ReviewResult(
            agent("security", "Security"),
            "owner/repo",
            null,
            Instant.now(),
            false,
            "timeout",
            0
        );

        String prompt = builder.buildSummaryPrompt(List.of(errorResult), "owner/repo");

        assertThat(prompt).contains("ERR:Security:timeout");
    }

    private ReviewResult successResult(String name, String content) {
        return new ReviewResult(
            agent(name, name),
            "owner/repo",
            content,
            Instant.now(),
            true,
            null,
            0
        );
    }

    private AgentConfig agent(String name, String displayName) {
        return AgentConfig.builder()
            .name(name)
            .displayName(displayName)
            .build();
    }
}
