package dev.logicojp.reviewer.domain.report;

import dev.logicojp.reviewer.shared.PlaceholderUtils;
import dev.logicojp.reviewer.shared.ReportFilenameUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Renders the executive summary final report.
///
/// The {@code findingsSummary} string is passed in by the caller — this class
/// no longer calls {@code FindingsExtractor} directly, which breaks cycle-9
/// ({@code FindingsExtractor} ⇄ {@code FindingsSummaryFormatter}).
///
/// Both template strings are pre-loaded by the application layer via
/// {@code LoadTemplatePort} and passed to the constructor — this class has no I/O.
///
/// Pure: imports only {@code domain.*}, {@code shared.*}, {@code java.*}.
public final class SummaryFinalReportFormatter {

    private final String summaryTemplate;
    private final String reportLinkEntryTemplate;

    public SummaryFinalReportFormatter(String summaryTemplate, String reportLinkEntryTemplate) {
        this.summaryTemplate = summaryTemplate;
        this.reportLinkEntryTemplate = reportLinkEntryTemplate;
    }

    /// Renders the executive summary.
    ///
    /// @param summaryContent  the AI-generated (or fallback) summary prose
    /// @param repository      the repository that was reviewed
    /// @param results         all review results (for counts and report links)
    /// @param date            formatted date string used in the summary header
    /// @param findingsSummary pre-computed findings summary string — pass
    ///                        {@code "指摘事項はありません。"} when findings are empty
    /// @return fully rendered executive summary content
    public String format(String summaryContent,
                         String repository,
                         List<ReviewResult> results,
                         String date,
                         String findingsSummary) {
        String reportLinks = buildReportLinks(results, date);
        Map<String, String> placeholders = buildSummaryPlaceholders(
            summaryContent, repository, results, date, reportLinks, findingsSummary);
        return PlaceholderUtils.replaceDollarPlaceholders(summaryTemplate, placeholders);
    }

    private Map<String, String> buildSummaryPlaceholders(String summaryContent,
                                                          String repository,
                                                          List<ReviewResult> results,
                                                          String date,
                                                          String reportLinks,
                                                          String findingsSummary) {
        var placeholders = new HashMap<String, String>();
        placeholders.put("date", date);
        placeholders.put("repository", repository != null ? repository : "");
        placeholders.put("agentCount", String.valueOf(results.size()));
        long successCount = results.stream().filter(ReviewResult::success).count();
        placeholders.put("successCount", String.valueOf(successCount));
        placeholders.put("failureCount", String.valueOf(results.size() - successCount));
        placeholders.put("summaryContent", summaryContent != null ? summaryContent : "");
        placeholders.put("findingsSummary", findingsSummary != null ? findingsSummary : "指摘事項はありません。");
        placeholders.put("reportLinks", reportLinks);
        return placeholders;
    }

    private String buildReportLinks(List<ReviewResult> results, String date) {
        var sb = new StringBuilder();
        for (ReviewResult result : results) {
            String safeName = ReportFilenameUtils.sanitizeAgentName(result.agentConfig().name());
            String filename = "%s_%s.md".formatted(safeName, date);
            var linkPlaceholders = Map.of(
                "displayName", result.agentConfig().displayName(),
                "filename", filename
            );
            sb.append(PlaceholderUtils.replaceDollarPlaceholders(reportLinkEntryTemplate, linkPlaceholders));
        }
        return sb.toString();
    }
}
