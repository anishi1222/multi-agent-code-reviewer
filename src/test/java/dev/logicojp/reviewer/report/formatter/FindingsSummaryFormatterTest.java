package dev.logicojp.reviewer.report.formatter;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.finding.FindingsExtractor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FindingsSummaryFormatter")
class FindingsSummaryFormatterTest {

    @Test
    @DisplayName("優先度順にグループ化してMarkdownを生成する")
    void formatsFindingsInSeverityOrder() {
        var findings = List.of(
            new FindingsExtractor.Finding("Low issue", "Low", "quality", "quality"),
            new FindingsExtractor.Finding("Critical issue", "Critical", "security", "security"),
            new FindingsExtractor.Finding("High issue", "High", "security", "security")
        );

        String summary = FindingsSummaryFormatter.formatSummary(findings);

        assertThat(summary).contains("#### Critical (1)");
        assertThat(summary).contains("#### High (1)");
        assertThat(summary).contains("#### Low (1)");
        assertThat(summary).contains("カテゴリー:");
        assertThat(summary.indexOf("#### Critical")).isLessThan(summary.indexOf("#### High"));
        assertThat(summary.indexOf("#### High")).isLessThan(summary.indexOf("#### Low"));
    }

    @Test
    @DisplayName("同一タイトルでもカテゴリが異なる場合は集約しない")
    void doesNotMergeDifferentCategories() {
        var findings = List.of(
            new FindingsExtractor.Finding("Same", "High", "security", "security"),
            new FindingsExtractor.Finding("Same", "High", "performance", "performance")
        );

        String summary = FindingsSummaryFormatter.formatSummary(findings);

        assertThat(summary).contains("#### High (2)");
        assertThat(summary).contains("指摘元: security");
        assertThat(summary).contains("指摘元: performance");
    }
}