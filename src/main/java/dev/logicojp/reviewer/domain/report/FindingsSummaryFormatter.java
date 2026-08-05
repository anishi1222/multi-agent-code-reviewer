package dev.logicojp.reviewer.domain.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Formats a list of {@link ReviewFinding} instances into a Markdown priority-grouped summary.
///
/// Uses {@link ReviewFinding} (top-level record) — this breaks the cycle-9
/// mutual import between {@code FindingsExtractor} and this class that existed when
/// both referenced each other's types.
///
/// Pure {@code java.*} — no framework dependencies.
public final class FindingsSummaryFormatter {

    private FindingsSummaryFormatter() {
    }

    /// Formats the given findings into a priority-grouped Markdown summary.
    ///
    /// @param findings the list of findings to format (may be empty)
    /// @return formatted Markdown text, or empty string if no findings
    public static String formatSummary(List<ReviewFinding> findings) {
        var sb = new StringBuilder();
        Map<String, List<MergedFinding>> grouped = groupByPriorityWithDedup(findings);

        for (String priority : List.of("Critical", "High", "Medium", "Low", "Unknown")) {
            List<MergedFinding> group = grouped.getOrDefault(priority, List.of());
            if (group.isEmpty()) {
                continue;
            }
            appendPriorityBlock(sb, priority, group);
        }

        return sb.toString().stripTrailing();
    }

    private static Map<String, List<MergedFinding>> groupByPriorityWithDedup(List<ReviewFinding> findings) {
        Map<String, Map<String, MergedFinding>> groupedByPriority = new LinkedHashMap<>();
        for (ReviewFinding finding : findings) {
            String priority = finding.priority();
            String dedupKey = strictMergeKey(finding);
            Map<String, MergedFinding> byKey = groupedByPriority.computeIfAbsent(priority, _ -> new LinkedHashMap<>());
            byKey.computeIfAbsent(dedupKey, _ -> new MergedFinding(finding.title()))
                .merge(finding);
        }

        Map<String, List<MergedFinding>> grouped = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, MergedFinding>> entry : groupedByPriority.entrySet()) {
            grouped.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }
        return grouped;
    }

    private static String strictMergeKey(ReviewFinding finding) {
        String safeTitle    = finding.title()    != null ? finding.title().trim()    : "";
        String safePriority = finding.priority() != null ? finding.priority().trim() : "";
        String safeCategory = finding.category() != null ? finding.category().trim() : "";
        String safeAgent    = finding.agent()    != null ? finding.agent().trim()    : "";
        return String.join("\u0000", safeTitle, safePriority, safeCategory, safeAgent);
    }

    private static void appendPriorityBlock(StringBuilder sb,
                                            String priority,
                                            List<MergedFinding> group) {
        sb.append("#### ").append(priority).append(" (").append(group.size()).append(")\n\n");
        for (MergedFinding finding : group) {
            sb.append("- **").append(finding.title).append("**")
                .append(" — カテゴリー: ").append(String.join(", ", finding.categories))
                .append(" / 指摘元: ").append(String.join(", ", finding.agents))
                .append("\n");
        }
        sb.append("\n");
    }

    private static final class MergedFinding {
        private final String title;
        private final Set<String> agents = new LinkedHashSet<>();
        private final Set<String> categories = new LinkedHashSet<>();

        private MergedFinding(String title) {
            this.title = title;
        }

        private MergedFinding merge(ReviewFinding finding) {
            agents.add(finding.agent());
            categories.add(finding.category());
            return this;
        }
    }
}
