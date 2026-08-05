package dev.logicojp.reviewer.domain.report;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.shared.PlaceholderUtils;

import java.util.Map;
import java.util.StringJoiner;

/// Renders a per-agent review report by substituting domain data into a template string.
///
/// The template string is pre-loaded by the application layer via {@code LoadTemplatePort}
/// and passed to the constructor — this class contains no I/O.
///
/// Pure: imports only {@code domain.*}, {@code shared.*}, {@code java.*}.
public final class ReportContentFormatter {

    private final String reportTemplate;

    public ReportContentFormatter(String reportTemplate) {
        this.reportTemplate = reportTemplate;
    }

    /// Renders the report template for a single review result.
    ///
    /// @param result the review result to format
    /// @param date   formatted date string used in the report header
    /// @return fully rendered report content
    public String format(ReviewResult result, String date) {
        AgentConfig config = result.agentConfig();
        String content = resolveReportContent(result);
        Map<String, String> placeholders = Map.of(
            "displayName", config.displayName(),
            "date", date,
            "repository", result.repository() != null ? result.repository() : "",
            "focusAreas", formatFocusAreas(config),
            "content", content
        );
        return PlaceholderUtils.replaceDollarPlaceholders(reportTemplate, placeholders);
    }

    private String resolveReportContent(ReviewResult result) {
        if (result.success()) {
            return result.content() != null ? result.content() : "";
        }
        return "⚠️ **レビュー失敗**\n\nエラー: " + result.errorMessage();
    }

    private String formatFocusAreas(AgentConfig config) {
        var joiner = new StringJoiner("\n", "", "\n");
        for (String area : config.focusAreas()) {
            joiner.add("- " + area);
        }
        return joiner.toString();
    }
}
