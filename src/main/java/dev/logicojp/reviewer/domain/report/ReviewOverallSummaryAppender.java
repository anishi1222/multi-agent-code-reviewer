package dev.logicojp.reviewer.domain.report;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/// Appends deterministic overall summaries to merged per-agent review results.
///
/// The summary is calculated from the merged report content itself so the displayed
/// counts always match the actual merged findings.
///
/// Pure {@code java.*} — no framework dependencies.
public final class ReviewOverallSummaryAppender {

    private static final String NO_FINDINGS_TEXT = "重大な指摘事項は確認されませんでした。";
    private static final String SUMMARY_PREFIX = "マージ後のレビュー結果として、";
    private static final String COUNT_SUFFIX = "件の指摘事項を確認しました。";
    private static final String BREAKDOWN_PREFIX = " 優先度内訳: ";
    private static final String TOP_PREFIX = " 主な指摘: ";

    private enum Priority {
        CRITICAL("Critical"),
        HIGH("High"),
        MEDIUM("Medium"),
        LOW("Low"),
        UNKNOWN("未分類");

        private final String label;

        Priority(String label) {
            this.label = label;
        }

        static Priority fromRaw(String raw) {
            if (raw == null || raw.isBlank()) {
                return UNKNOWN;
            }
            return switch (raw.trim().toLowerCase()) {
                case "critical" -> CRITICAL;
                case "high"     -> HIGH;
                case "medium"   -> MEDIUM;
                case "low"      -> LOW;
                default         -> UNKNOWN;
            };
        }
    }

    private ReviewOverallSummaryAppender() {
    }

    public static List<ReviewResult> appendToMergedResults(List<ReviewResult> mergedResults) {
        if (mergedResults == null || mergedResults.isEmpty()) {
            return List.of();
        }

        List<ReviewResult> finalized = new ArrayList<>(mergedResults.size());
        for (ReviewResult result : mergedResults) {
            finalized.add(appendOverallSummary(result));
        }
        return List.copyOf(finalized);
    }

    private static ReviewResult appendOverallSummary(ReviewResult result) {
        if (result == null || !result.success() || result.content() == null || result.content().isBlank()) {
            return result;
        }

        String contentWithoutOverall = ReviewFindingParser.stripOverallSummary(result.content());
        String summary = buildOverallSummary(contentWithoutOverall);
        String finalized = contentWithoutOverall
            + "\n\n---\n\n"
            + "**総評**\n\n"
            + summary;

        return ReviewResult.builder()
            .agentConfig(result.agentConfig())
            .repository(result.repository())
            .content(finalized)
            .success(true)
            .errorMessage(result.errorMessage())
            .timestamp(result.timestamp())
            .build();
    }

    static String buildOverallSummary(String mergedContent) {
        List<ReviewFindingParser.FindingBlock> findings = ReviewFindingParser.extractFindingBlocks(mergedContent);
        if (findings.isEmpty()) {
            return NO_FINDINGS_TEXT;
        }

        EnumMap<Priority, Integer> counts = new EnumMap<>(Priority.class);
        for (Priority priority : Priority.values()) {
            counts.put(priority, 0);
        }

        List<String> topTitles = new ArrayList<>();
        for (ReviewFindingParser.FindingBlock finding : findings) {
            String priorityValue = ReviewFindingParser.extractTableValue(finding.body(), "Priority");
            Priority priority = Priority.fromRaw(priorityValue);
            counts.compute(priority, (_, count) -> count + 1);
            if (topTitles.size() < 3) {
                topTitles.add(finding.title());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(SUMMARY_PREFIX).append(findings.size()).append(COUNT_SUFFIX);
        sb.append(BREAKDOWN_PREFIX)
            .append(Priority.CRITICAL.label).append(" ").append(counts.get(Priority.CRITICAL)).append("件, ")
            .append(Priority.HIGH.label).append(" ").append(counts.get(Priority.HIGH)).append("件, ")
            .append(Priority.MEDIUM.label).append(" ").append(counts.get(Priority.MEDIUM)).append("件, ")
            .append(Priority.LOW.label).append(" ").append(counts.get(Priority.LOW)).append("件");

        int unknown = counts.get(Priority.UNKNOWN);
        if (unknown > 0) {
            sb.append(", ").append(Priority.UNKNOWN.label).append(" ").append(unknown).append("件");
        }
        sb.append("。");

        if (!topTitles.isEmpty()) {
            sb.append(TOP_PREFIX).append(String.join("、", topTitles)).append("。");
        }

        return sb.toString();
    }
}
