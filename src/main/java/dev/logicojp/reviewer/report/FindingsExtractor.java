package dev.logicojp.reviewer.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Extracts structured findings from review result content and formats a
/// deterministic summary grouped by priority level.
///
/// Merges v1 FindingsExtractor, FindingsParser, and FindingsSummaryFormatter.
public final class FindingsExtractor {

    private static final Logger logger = LoggerFactory.getLogger(FindingsExtractor.class);

    /// A single extracted finding.
    public record Finding(String title, String priority, String agent) {}

    // --- Parsing patterns (from v1 FindingsParser) ---

    private static final Pattern PRIORITY_PATTERN = Pattern.compile(
        "\\|\\s*\\*{0,2}Priority\\*{0,2}\\s*\\|\\s*(Critical|High|Medium|Low)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private static final Pattern FINDING_HEADING_PATTERN = Pattern.compile(
        "^###\\s+\\[?\\d+\\]?\\.?\\s+(.+)$",
        Pattern.MULTILINE
    );

    /// Ordered priority levels for the formatted output.
    private static final List<String> PRIORITY_ORDER = List.of(
        "Critical", "High", "Medium", "Low", "Unknown"
    );

    private FindingsExtractor() {
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /// Builds a deterministic findings summary from all review results.
    ///
    /// Extracts findings from each successful agent's review content
    /// and groups them by priority level. The output is a Markdown-formatted
    /// summary suitable for inclusion in the executive summary.
    public static String buildFindingsSummary(List<ReviewResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        List<Finding> allFindings = new ArrayList<>();

        for (ReviewResult result : results) {
            if (!result.success() || result.content() == null || result.content().isBlank()) {
                continue;
            }
            String agentName = result.agentConfig() != null
                ? result.agentConfig().displayName()
                : "unknown";

            List<Finding> findings = extractFindings(result.content(), agentName);
            allFindings.addAll(findings);
        }

        if (allFindings.isEmpty()) {
            return "";
        }

        return formatSummary(allFindings);
    }

    // ------------------------------------------------------------------
    // Finding extraction (from v1 FindingsParser)
    // ------------------------------------------------------------------

    static List<Finding> extractFindings(String content, String agentName) {
        List<String> titles = new ArrayList<>();
        List<String> priorities = new ArrayList<>();

        Matcher headingMatcher = FINDING_HEADING_PATTERN.matcher(content);
        while (headingMatcher.find()) {
            titles.add(headingMatcher.group(1).trim());
        }

        Matcher priorityMatcher = PRIORITY_PATTERN.matcher(content);
        while (priorityMatcher.find()) {
            priorities.add(capitalize(priorityMatcher.group(1).trim()));
        }

        if (titles.isEmpty() && content.contains("指摘事項なし")) {
            return List.of();
        }

        List<Finding> findings = new ArrayList<>();

        if (!titles.isEmpty() && !priorities.isEmpty()) {
            // Both present: 1:1 pairing
            int count = Math.min(titles.size(), priorities.size());
            for (int i = 0; i < count; i++) {
                findings.add(new Finding(titles.get(i), priorities.get(i), agentName));
            }
        } else if (titles.isEmpty() && !priorities.isEmpty()) {
            // Priorities only: assign generic titles
            for (int i = 0; i < priorities.size(); i++) {
                findings.add(new Finding("Finding " + (i + 1), priorities.get(i), agentName));
            }
        } else if (!titles.isEmpty()) {
            // Titles only: assign Unknown priority
            for (String title : titles) {
                findings.add(new Finding(title, "Unknown", agentName));
            }
        }

        logger.debug("Agent '{}': extracted {} finding(s)", agentName, findings.size());
        return findings;
    }

    // ------------------------------------------------------------------
    // Summary formatting (from v1 FindingsSummaryFormatter)
    // ------------------------------------------------------------------

    private static String formatSummary(List<Finding> findings) {
        var sb = new StringBuilder();
        Map<String, List<Finding>> grouped = groupByPriority(findings);

        for (String priority : PRIORITY_ORDER) {
            List<Finding> group = grouped.getOrDefault(priority, List.of());
            if (group.isEmpty()) {
                continue;
            }
            sb.append("#### ").append(priority).append(" (").append(group.size()).append(")\n\n");
            for (Finding finding : group) {
                sb.append("- **").append(finding.title()).append("** — ").append(finding.agent()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString().stripTrailing();
    }

    private static Map<String, List<Finding>> groupByPriority(List<Finding> findings) {
        Map<String, List<Finding>> grouped = new LinkedHashMap<>();
        for (Finding finding : findings) {
            grouped.computeIfAbsent(finding.priority(), _ -> new ArrayList<>()).add(finding);
        }
        return grouped;
    }

    // ------------------------------------------------------------------
    // Utilities
    // ------------------------------------------------------------------

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
