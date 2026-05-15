package dev.logicojp.reviewer.report.finding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    static List<FindingsExtractor.Finding> extractFindings(String content, String agentName, String category) {
        List<FindingsExtractor.Finding> findings = new ArrayList<>();

        List<String> titles = new ArrayList<>();
        List<String> priorities = new ArrayList<>();

        collectTitlesAndPriorities(content, titles, priorities);

        if (priorities.isEmpty() && hasNoFindingsMarker(content)) {
            return List.of();
        }

        int count = Math.min(titles.size(), priorities.size());
        for (int i = 0; i < count; i++) {
            findings.add(new FindingsExtractor.Finding(titles.get(i), priorities.get(i), agentName, category));
        }

        appendPriorityOnlyFindings(findings, titles, priorities, agentName, category);
        appendTitleOnlyFindings(findings, titles, priorities, agentName, category);

        return findings;
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

    private static void appendPriorityOnlyFindings(List<FindingsExtractor.Finding> findings,
                                                   List<String> titles,
                                                   List<String> priorities,
                                                   String agentName,
                                                   String category) {
        if (!titles.isEmpty() || priorities.isEmpty()) {
            return;
        }
        for (int i = 0; i < priorities.size(); i++) {
            findings.add(new FindingsExtractor.Finding("Finding " + (i + 1), priorities.get(i), agentName, category));
        }
    }

    private static void appendTitleOnlyFindings(List<FindingsExtractor.Finding> findings,
                                                List<String> titles,
                                                List<String> priorities,
                                                String agentName,
                                                String category) {
        if (titles.isEmpty() || !priorities.isEmpty()) {
            return;
        }
        for (String title : titles) {
            findings.add(new FindingsExtractor.Finding(title, "Unknown", agentName, category));
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
