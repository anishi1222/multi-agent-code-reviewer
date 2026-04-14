package dev.logicojp.reviewer.report.formatter;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.finding.FindingsExtractor;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.service.TemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SummaryFinalReportFormatter")
class SummaryFinalReportFormatterTest {

    @Test
    @DisplayName("結果を集計しexecutive summaryテンプレートへ展開する")
    void formatsExecutiveSummaryWithCountsAndLinks() {
        TemplateService templateService = new TemplateService(
            new TemplateConfig(null, null, null, null, null, null, null, null, null)
        );
        var formatter = new SummaryFinalReportFormatter(templateService);

        var security = success("security", "Security", """
            ### 1. SQL Injection

            | 項目 | 内容 |
            |------|------|
            | **Priority** | High |
            | **指摘の概要** | Parameter not used |
            | **該当箇所** | src/A.java L10 |
            """);
        var performanceFailure = failure("performance", "Performance", "timeout");

        String report = formatter.format(
            "summary body",
            "owner/repo",
            List.of(security, performanceFailure),
            "2026-02-16"
        );

        assertThat(report).contains("owner/repo");
        assertThat(report).contains("summary body");
        assertThat(report).contains("2");
        assertThat(report).contains("1");
        assertThat(report).contains("security_2026-02-16.md");
        assertThat(report).contains("performance_2026-02-16.md");
    }

    @Test
    @DisplayName("findingsが空の場合は既定メッセージを使用する")
    void usesDefaultFindingsMessageWhenExtractorReturnsEmpty() {
        TemplateService templateService = new TemplateService(
            new TemplateConfig(null, null, null, null, null, null, null, null, null)
        );
        var formatter = new SummaryFinalReportFormatter(templateService);

        String report = formatter.format(
            null,
            "owner/repo",
            List.of(success("security", "Security", "指摘事項なし")),
            "2026-02-16"
        );

        assertThat(report).contains("指摘事項はありません。");
    }

    private ReviewResult success(String name, String displayName, String content) {
        return ReviewResult.builder()
            .agentConfig(agent(name, displayName))
            .repository("owner/repo")
            .content(content)
            .success(true)
            .timestamp(Instant.now())
            .build();
    }

    private ReviewResult failure(String name, String displayName, String error) {
        return ReviewResult.builder()
            .agentConfig(agent(name, displayName))
            .repository("owner/repo")
            .success(false)
            .errorMessage(error)
            .timestamp(Instant.now())
            .build();
    }

    private AgentConfig agent(String name, String displayName) {
        return AgentConfig.builder()
            .name(name)
            .displayName(displayName)
            .build();
    }
}