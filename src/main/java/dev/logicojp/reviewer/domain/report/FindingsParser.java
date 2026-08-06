package dev.logicojp.reviewer.domain.report;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Extracts individual {@link ReviewFinding} instances from raw agent Markdown content.
///
/// Uses {@link ReviewFinding} (top-level record) instead of an inner type — this breaks
/// the cycle-9 mutual import between {@code FindingsExtractor} and
/// {@code FindingsSummaryFormatter}.
///
/// Pure {@code java.*} — no framework dependencies.
final class FindingsParser {

    private static final Pattern PRIORITY_PATTERN = Pattern.compile(
        "\\|\\s*\\*{0,2}Priority\\*{0,2}\\s*\\|\\s*(Critical|High|Medium|Low)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private static final Pattern FINDING_HEADING_PATTERN = Pattern.compile(
        "^###\\s+\\[?\\d+\\]?\\.?\\s+(.+)$",
        Pattern.MULTILINE
    );
    private static final Pattern NO_FINDINGS_MARKER_PATTERN = Pattern.compile(
        "(?m)^\\s*(?:確認した範囲では\\s*)?指摘事項なし(?:。)?\\s*$"
    );

    private FindingsParser() {
    }

    static List<ReviewFinding> extractFindings(String content, String agentName, String category) {
        List<ReviewFinding> structured = extractStructuredFindings(content, agentName, category);
        if (!structured.isEmpty()) {
            return structured;
        }

        List<ReviewFinding> findings = new ArrayList<>();

        List<String> titles = new ArrayList<>();
        List<String> priorities = new ArrayList<>();

        collectTitlesAndPriorities(content, titles, priorities);

        if (priorities.isEmpty() && hasNoFindingsMarker(content)) {
            return List.of();
        }

        int count = Math.min(titles.size(), priorities.size());
        for (int i = 0; i < count; i++) {
            findings.add(new ReviewFinding(titles.get(i), priorities.get(i), agentName, category));
        }

        appendPriorityOnlyFindings(findings, titles, priorities, agentName, category);
        appendTitleOnlyFindings(findings, titles, priorities, agentName, category);

        return findings;
    }

    /// Preferred extraction path, ported from origin/main: when the content contains
    /// well-formed finding blocks, read `Priority` / `指摘の概要` / `該当箇所` straight
    /// out of each block's table instead of inferring them with regex heuristics.
    ///
    /// Returns an empty list when no blocks are present, so the caller falls back to
    /// the heading/priority scanning path.
    private static List<ReviewFinding> extractStructuredFindings(String content,
                                                                String agentName,
                                                                String category) {
        List<ReviewFindingParser.FindingBlock> blocks = ReviewFindingParser.extractFindingBlocks(content);
        if (blocks.isEmpty()) {
            return List.of();
        }
        return blocks.stream()
            .map(block -> new ReviewFinding(
                block.title(),
                priorityOrUnknown(ReviewFindingParser.extractTableValue(block.body(), "Priority")),
                agentName,
                category,
                ReviewFindingParser.extractTableValue(block.body(), "指摘の概要"),
                ReviewFindingParser.extractTableValue(block.body(), "該当箇所")
            ))
            .toList();
    }

    private static String priorityOrUnknown(String priority) {
        return priority == null || priority.isBlank() ? "Unknown" : priority;
    }

    private static void collectTitlesAndPriorities(String content,
                                                   List<String> titles,
                                                   List<String> priorities) {
        Matcher headingMatcher = FINDING_HEADING_PATTERN.matcher(content);
        while (headingMatcher.find()) {
            titles.add(headingMatcher.group(1).trim());
        }

        Matcher priorityMatcher = PRIORITY_PATTERN.matcher(content);
        while (priorityMatcher.find()) {
            priorities.add(capitalize(priorityMatcher.group(1).trim()));
        }
    }

    private static void appendPriorityOnlyFindings(List<ReviewFinding> findings,
                                                   List<String> titles,
                                                   List<String> priorities,
                                                   String agentName,
                                                   String category) {
        if (!titles.isEmpty() || priorities.isEmpty()) {
            return;
        }
        for (int i = 0; i < priorities.size(); i++) {
            findings.add(new ReviewFinding("Finding " + (i + 1), priorities.get(i), agentName, category));
        }
    }

    private static void appendTitleOnlyFindings(List<ReviewFinding> findings,
                                                List<String> titles,
                                                List<String> priorities,
                                                String agentName,
                                                String category) {
        if (titles.isEmpty() || !priorities.isEmpty()) {
            return;
        }
        for (String title : titles) {
            findings.add(new ReviewFinding(title, "Unknown", agentName, category));
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private static boolean hasNoFindingsMarker(String content) {
        return NO_FINDINGS_MARKER_PATTERN.matcher(content).find();
    }
}
