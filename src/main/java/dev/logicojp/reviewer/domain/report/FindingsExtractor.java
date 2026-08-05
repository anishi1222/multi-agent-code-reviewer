package dev.logicojp.reviewer.domain.report;

import java.util.ArrayList;
import java.util.List;

/// Extracts structured findings from review result content.
///
/// Parses the Markdown output format produced by review agents and builds
/// a list of {@link ReviewFinding} instances for each successful result.
///
/// Cycle-9 fix: this class no longer imports {@code FindingsSummaryFormatter}.
/// Callers that need a formatted findings summary must call
/// {@code FindingsSummaryFormatter.formatSummary} separately on the returned list.
///
/// Pure {@code java.*} — no framework dependencies.
public final class FindingsExtractor {

    private FindingsExtractor() {
    }

    /// Extracts all findings from the given review results.
    ///
    /// Only successful results with non-blank content are processed.
    ///
    /// @param results list of review results (may be null or empty)
    /// @return all extracted findings across all results
    public static List<ReviewFinding> extractAll(List<ReviewResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<ReviewFinding> allFindings = new ArrayList<>();

        for (ReviewResult result : results) {
            if (shouldSkipResult(result)) {
                continue;
            }

            String agentName = resolveAgentName(result);
            String category = resolveCategory(result);

            List<ReviewFinding> findings = FindingsParser.extractFindings(result.content(), agentName, category);
            allFindings.addAll(findings);
        }

        return allFindings;
    }

    /// Extracts findings from a single agent's review content.
    ///
    /// @param content   raw Markdown review content
    /// @param agentName name of the agent that produced the content
    /// @return findings extracted from the content
    public static List<ReviewFinding> extractFindings(String content, String agentName) {
        return FindingsParser.extractFindings(content, agentName, agentName);
    }

    /// Extracts findings from a single agent's review content with an explicit category.
    ///
    /// @param content   raw Markdown review content
    /// @param agentName name of the agent that produced the content
    /// @param category  category label for all extracted findings
    /// @return findings extracted from the content
    public static List<ReviewFinding> extractFindings(String content, String agentName, String category) {
        return FindingsParser.extractFindings(content, agentName, category);
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
}
