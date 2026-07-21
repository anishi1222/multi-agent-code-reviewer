package dev.logicojp.reviewer.report.finding;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.formatter.FindingsSummaryFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/// Extracts structured findings from review result content.
///
/// Parses the Markdown output format produced by review agents and builds
/// a deterministic summary of all findings grouped by priority level.
/// This summary is included in the executive summary report without
/// requiring an additional LLM call.
public final class FindingsExtractor {

    @FunctionalInterface
    interface FindingsParserStrategy {
        List<Finding> parse(String content, String agentName, String category);
    }

    @FunctionalInterface
    interface FindingsSummaryFormatterStrategy {
        String format(List<Finding> findings);
    }

    private static final Logger logger = LoggerFactory.getLogger(FindingsExtractor.class);

    private FindingsExtractor() {
        // Utility class — not instantiable
    }

    /// A single extracted finding.
    /// @param title   The finding title
    /// @param priority The priority level (Critical, High, Medium, Low)
    /// @param agent   The agent name that produced the finding
    public record Finding(String title,
                          String priority,
                          String agent,
                          String category,
                          String summary,
                          String location) {
        public Finding(String title, String priority, String agent, String category) {
            this(title, priority, agent, category, "", "");
        }

        public Finding {
            title = title != null ? title : "";
            priority = priority != null ? priority : "Unknown";
            agent = agent != null ? agent : "unknown";
            category = category != null ? category : "unknown";
            summary = summary != null ? summary : "";
            location = location != null ? location : "";
        }
    }

    /// Builds a deterministic findings summary from all review results.
    ///
    /// Extracts findings from each successful agent's review content
    /// and groups them by priority level. The output is a Markdown-formatted
    /// summary suitable for inclusion in the executive summary.
    ///
    /// @param results List of review results from all agents
    /// @return Formatted findings summary, or empty string if no findings
    public static String buildFindingsSummary(List<ReviewResult> results) {
        return buildFindingsSummary(
            results,
            FindingsExtractor::extractFindings,
            FindingsSummaryFormatter::formatSummary
        );
    }

    static String buildFindingsSummary(List<ReviewResult> results,
                                       FindingsParserStrategy parserStrategy,
                                       FindingsSummaryFormatterStrategy formatterStrategy) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        List<Finding> allFindings = new ArrayList<>();

        for (ReviewResult result : results) {
            if (shouldSkipResult(result)) {
                continue;
            }

            String agentName = resolveAgentName(result);
            String category = resolveCategory(result);

            List<Finding> findings = parserStrategy.parse(result.content(), agentName, category);
            allFindings.addAll(findings);
        }

        if (allFindings.isEmpty()) {
            return "";
        }

        return formatterStrategy.format(allFindings);
    }

    private static boolean shouldSkipResult(ReviewResult result) {
        return !result.success() || result.content() == null || result.content().isBlank();
    }

    private static String resolveAgentName(ReviewResult result) {
        return result.agentConfig() != null
            ? result.agentConfig().displayName()
            : "unknown";
    }

    private static String resolveCategory(ReviewResult result) {
        if (result.agentConfig() == null) {
            return "unknown";
        }
        if (!result.agentConfig().focusAreas().isEmpty()) {
            return result.agentConfig().focusAreas().getFirst();
        }
        return result.agentConfig().displayName();
    }

    /// Extracts findings from a single agent's review content.
    static List<Finding> extractFindings(String content, String agentName) {
        return extractFindings(content, agentName, agentName);
    }

    static List<Finding> extractFindings(String content, String agentName, String category) {
        List<ReviewFindingParser.FindingBlock> blocks = ReviewFindingParser.extractFindingBlocks(content);
        if (!blocks.isEmpty()) {
            List<Finding> findings = blocks.stream()
                .map(block -> new Finding(
                    block.title(),
                    priorityOrUnknown(ReviewFindingParser.extractTableValue(block.body(), "Priority")),
                    agentName,
                    category,
                    ReviewFindingParser.extractTableValue(block.body(), "指摘の概要"),
                    ReviewFindingParser.extractTableValue(block.body(), "該当箇所")
                ))
                .toList();
            logger.debug("Agent '{}': extracted {} structured finding(s)", agentName, findings.size());
            return findings;
        }
        List<Finding> findings = FindingsParser.extractFindings(content, agentName, category);
        logger.debug("Agent '{}': extracted {} finding(s)", agentName, findings.size());
        return findings;
    }

    private static String priorityOrUnknown(String priority) {
        return priority == null || priority.isBlank() ? "Unknown" : priority;
    }
}
