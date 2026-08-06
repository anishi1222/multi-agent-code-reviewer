package dev.logicojp.reviewer.domain.report;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FallbackSummaryBuilder")
class FallbackSummaryBuilderTest {

    @Test
    @DisplayName("成功と失敗の結果をテンプレートに従って組み立てる")
    void buildsFallbackSummaryWithSuccessAndFailure() {
        var builder = new FallbackSummaryBuilder(
            "TABLE\n${tableRows}\nSUM\n${agentSummaries}",
            "ROW:${displayName}:${content}\n",
            "OK:${displayName}:${content}\n",
            "NG:${displayName}:${errorMessage}\n",
            10,
            3
        );

        var success = new ReviewResult(
            agent("code", "Code"),
            "owner/repo",
            "line1\nline2   line3",
            Instant.now(),
            true,
            null,
            0
        );
        var failure = new ReviewResult(
            agent("security", "Security"),
            "owner/repo",
            null,
            Instant.now(),
            false,
            "api error",
            0
        );

        String summary = builder.buildFallbackSummary(List.of(success, failure));

        assertThat(summary).contains("ROW:Code:line1 line...");
        assertThat(summary).contains("ROW:Security:N/A");
        assertThat(summary).contains("OK:Code:line1 line...");
        assertThat(summary).contains("NG:Security:api error");
    }

    private AgentConfig agent(String name, String displayName) {
        return AgentConfig.builder()
            .name(name)
            .displayName(displayName)
            .build();
    }
}
