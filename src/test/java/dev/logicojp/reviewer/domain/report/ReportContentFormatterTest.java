package dev.logicojp.reviewer.domain.report;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportContentFormatter")
class ReportContentFormatterTest {

    @Test
    @DisplayName("成功結果をテンプレートへ展開する")
    void formatsSuccessfulResult() {
        var formatter = new ReportContentFormatter(reportTemplate());
        var result = ReviewResult.builder()
            .agentConfig(new AgentConfig("security", "Security", "model", "system", "instruction", null,
                List.of("SQL", "Auth"), List.of()))
            .repository("owner/repo")
            .content("review body")
            .success(true)
            .timestamp(Instant.now())
            .build();

        String content = formatter.format(result, "2026-02-16");

        assertThat(content).contains("Security");
        assertThat(content).contains("owner/repo");
        assertThat(content).contains("- SQL");
        assertThat(content).contains("- Auth");
        assertThat(content).contains("review body");
        assertThat(content).contains("2026-02-16");
    }

    @Test
    @DisplayName("失敗結果はエラーメッセージ付きで出力する")
    void formatsFailedResultWithErrorMessage() {
        var formatter = new ReportContentFormatter(reportTemplate());
        var result = ReviewResult.builder()
            .agentConfig(new AgentConfig("security", "Security", "model", "system", "instruction", null,
                List.of("SQL"), List.of()))
            .repository("owner/repo")
            .success(false)
            .errorMessage("timeout")
            .timestamp(Instant.now())
            .build();

        String content = formatter.format(result, "2026-02-16");

        assertThat(content).contains("レビュー失敗");
        assertThat(content).contains("timeout");
    }

    private String reportTemplate() {
        return """
            # ${displayName}
            date=${date}
            repo=${repository}
            focus:
            ${focusAreas}
            body:
            ${content}
            """;
    }
}
