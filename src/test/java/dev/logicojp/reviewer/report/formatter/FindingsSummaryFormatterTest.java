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
    @DisplayName("同一タイトルの指摘をエージェント横断で集約する")
    void mergesDuplicateFindingsAcrossAgents() {
        var findings = List.of(
            new FindingsExtractor.Finding("Same", "High", "security", "security"),
            new FindingsExtractor.Finding("Same", "High", "performance", "performance")
        );

        String summary = FindingsSummaryFormatter.formatSummary(findings);

        assertThat(summary).contains("#### High (1)");
        assertThat(summary).contains("カテゴリー: security, performance");
        assertThat(summary).contains("指摘元: security");
        assertThat(summary).contains("security, performance");
    }

    @Test
    @DisplayName("重大度が異なる重複指摘は高い優先度へ統合する")
    void usesHighestPriorityForDuplicateFinding() {
        var findings = List.of(
            new FindingsExtractor.Finding(
                "Shared issue", "High", "security", "security", "same summary", "src/A.java"
            ),
            new FindingsExtractor.Finding(
                "Shared issue", "Critical", "quality", "quality", "same summary", "src/A.java"
            )
        );

        String summary = FindingsSummaryFormatter.formatSummary(findings);

        assertThat(summary).contains("#### Critical (1)");
        assertThat(summary).doesNotContain("#### High");
        assertThat(summary).contains("指摘元: security, quality");
    }

    @Test
    @DisplayName("同一タイトルでも該当箇所が異なる指摘は統合しない")
    void keepsSameTitleAtDifferentLocationsSeparate() {
        var findings = List.of(
            new FindingsExtractor.Finding(
                "Validation issue", "High", "security", "security", "same summary", "src/A.java"
            ),
            new FindingsExtractor.Finding(
                "Validation issue", "High", "quality", "quality", "same summary", "src/B.java"
            )
        );

        String summary = FindingsSummaryFormatter.formatSummary(findings);

        assertThat(summary).contains("#### High (2)");
    }
}