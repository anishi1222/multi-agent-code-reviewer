package dev.logicojp.reviewer.orchestrator;

import java.util.Map;
import java.util.Set;

/// Formats merged findings back into markdown content.
final class ReviewMergedContentFormatter {

    private ReviewMergedContentFormatter() {
    }

    static String formatMergedContent(Map<String, ReviewResultMerger.AggregatedFinding> aggregatedFindings,
                                      int totalPasses, int failedPasses) {
        var sb = new StringBuilder();

        if (aggregatedFindings.isEmpty()) {
            sb.append("指摘事項なし");
        } else {
            int index = 1;
            int totalFindings = aggregatedFindings.size();
            for (ReviewResultMerger.AggregatedFinding finding : aggregatedFindings.values()) {
                sb.append("### ").append(index).append(". ").append(finding.title()).append("\n\n");
                if (finding.passNumbers().size() > 1) {
                    sb.append("> 検出パス: ").append(formatPassNumbers(finding.passNumbers())).append("\n\n");
                }
                sb.append(finding.body().trim());
                if (index < totalFindings) {
                    sb.append("\n\n---\n\n");
                }
                index++;
            }
        }

        if (failedPasses > 0) {
            sb.append("\n\n---\n\n> **注記**: %d パス中 %d パスが失敗しました。上記は成功したパスの結果のみです。\n"
                .formatted(totalPasses, failedPasses));
        }

        return sb.toString();
    }

    private static String formatPassNumbers(Set<Integer> passNumbers) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Integer passNumber : passNumbers) {
            if (i > 0) sb.append(", ");
            sb.append(passNumber);
            i++;
        }
        return sb.toString();
    }
}
