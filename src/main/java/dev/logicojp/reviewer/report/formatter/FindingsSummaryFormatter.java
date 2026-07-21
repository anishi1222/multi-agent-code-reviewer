package dev.logicojp.reviewer.report.formatter;

import dev.logicojp.reviewer.report.finding.FindingsExtractor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class FindingsSummaryFormatter {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private FindingsSummaryFormatter() {
    }

    public static String formatSummary(List<FindingsExtractor.Finding> findings) {
        var summary = new StringBuilder();
        Map<String, List<ConsolidatedFinding>> grouped = groupByCanonicalPriority(consolidate(findings));

        for (String priority : List.of("Critical", "High", "Medium", "Low", "Unknown")) {
            List<ConsolidatedFinding> group = grouped.getOrDefault(priority, List.of());
            if (!group.isEmpty()) {
                appendPriorityBlock(summary, priority, group);
            }
        }

        return summary.toString().stripTrailing();
    }

    private static List<ConsolidatedFinding> consolidate(List<FindingsExtractor.Finding> findings) {
        Map<String, List<ConsolidatedFinding>> findingsByTitle = new LinkedHashMap<>();
        for (FindingsExtractor.Finding finding : findings) {
            String normalizedTitle = normalize(finding.title());
            List<ConsolidatedFinding> sameTitle =
                findingsByTitle.computeIfAbsent(normalizedTitle, _ -> new ArrayList<>());
            ConsolidatedFinding match = sameTitle.stream()
                .filter(existing -> existing.matches(finding))
                .findFirst()
                .orElseGet(() -> {
                    var created = new ConsolidatedFinding(finding);
                    sameTitle.add(created);
                    return created;
                });
            match.addSource(finding);
        }

        return findingsByTitle.values().stream()
            .flatMap(List::stream)
            .toList();
    }

    private static Map<String, List<ConsolidatedFinding>> groupByCanonicalPriority(
            List<ConsolidatedFinding> findings) {
        Map<String, List<ConsolidatedFinding>> grouped = new LinkedHashMap<>();
        for (ConsolidatedFinding finding : findings) {
            grouped.computeIfAbsent(finding.priority, _ -> new ArrayList<>()).add(finding);
        }
        return grouped;
    }

    private static void appendPriorityBlock(StringBuilder summary,
                                            String priority,
                                            List<ConsolidatedFinding> group) {
        summary.append("#### ").append(priority).append(" (").append(group.size()).append(")\n\n");
        for (ConsolidatedFinding finding : group) {
            summary.append("- **").append(finding.title).append("**")
                .append(" — カテゴリー: ").append(String.join(", ", finding.categories))
                .append(" / 指摘元: ").append(String.join(", ", finding.agents))
                .append("\n");
        }
        summary.append("\n");
    }

    private static String normalize(String value) {
        String safeValue = value != null ? value : "";
        return WHITESPACE.matcher(
            safeValue.replace("*", "").strip().toLowerCase(Locale.ROOT)
        ).replaceAll(" ");
    }

    private static int priorityRank(String priority) {
        return switch (priority != null ? priority.toLowerCase(Locale.ROOT) : "") {
            case "critical" -> 4;
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
    }

    private static String canonicalPriority(String priority) {
        return switch (priority != null ? priority.toLowerCase(Locale.ROOT) : "") {
            case "critical" -> "Critical";
            case "high" -> "High";
            case "medium" -> "Medium";
            case "low" -> "Low";
            default -> "Unknown";
        };
    }

    private static final class ConsolidatedFinding {
        private final String title;
        private String priority;
        private String normalizedSummary;
        private String normalizedLocation;
        private final Set<String> agents = new LinkedHashSet<>();
        private final Set<String> categories = new LinkedHashSet<>();

        private ConsolidatedFinding(FindingsExtractor.Finding finding) {
            this.title = finding.title();
            this.priority = canonicalPriority(finding.priority());
            this.normalizedSummary = normalize(finding.summary());
            this.normalizedLocation = normalize(finding.location());
        }

        private boolean matches(FindingsExtractor.Finding finding) {
            return compatible(normalizedSummary, normalize(finding.summary()))
                && compatible(normalizedLocation, normalize(finding.location()));
        }

        private ConsolidatedFinding addSource(FindingsExtractor.Finding finding) {
            String incomingSummary = normalize(finding.summary());
            String incomingLocation = normalize(finding.location());
            if (normalizedSummary.isEmpty()) {
                normalizedSummary = incomingSummary;
            }
            if (normalizedLocation.isEmpty()) {
                normalizedLocation = incomingLocation;
            }
            if (priorityRank(finding.priority()) > priorityRank(priority)) {
                priority = canonicalPriority(finding.priority());
            }
            agents.add(finding.agent());
            categories.add(finding.category());
            return this;
        }

        private static boolean compatible(String existing, String incoming) {
            return existing.isEmpty() || incoming.isEmpty() || existing.equals(incoming);
        }
    }
}
