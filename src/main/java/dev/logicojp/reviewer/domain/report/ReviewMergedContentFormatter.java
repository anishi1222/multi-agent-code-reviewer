package dev.logicojp.reviewer.domain.report;

import java.util.Map;
import java.util.Set;

/// Formats aggregated multi-pass findings into a single merged Markdown content block.
///
/// Pure {@code java.*} — no framework dependencies.
public final class ReviewMergedContentFormatter {

    private ReviewMergedContentFormatter() {
    }

    public static String format(Map<String, AggregatedFinding> aggregatedFindings,
                                int totalPasses,
                                int failedPasses) {
        var contentBuilder = new StringBuilder();

        if (aggregatedFindings.isEmpty()) {
            contentBuilder.append("指摘事項なし");
        } else {
            int index = 1;
            int totalFindings = aggregatedFindings.size();
            for (AggregatedFinding finding : aggregatedFindings.values()) {
                contentBuilder.append("### ").append(index).append(". ").append(finding.title()).append("\n\n");
                if (finding.passNumbers().size() > 1) {
                    contentBuilder.append("> 検出パス: ")
                        .append(formatPassNumbers(finding.passNumbers()))
                        .append("\n\n");
                }
                contentBuilder.append(finding.body().trim());
                if (index < totalFindings) {
                    contentBuilder.append("\n\n---\n\n");
                }
                index++;
            }
        }

        if (failedPasses > 0) {
            contentBuilder.append("\n\n---\n\n> **注記**: %d パス中 %d パスが失敗しました。上記は成功したパスの結果のみです。\n"
                .formatted(totalPasses, failedPasses));
        }

        return contentBuilder.toString();
    }

    private static String formatPassNumbers(Set<Integer> passNumbers) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Integer passNumber : passNumbers) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(passNumber);
            i++;
        }
        return sb.toString();
    }
}
