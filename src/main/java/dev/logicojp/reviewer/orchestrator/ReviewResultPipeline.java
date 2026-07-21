package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.report.core.ReviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/// Finalizes orchestrated review results: collect, filter, and log.
final class ReviewResultPipeline {

    private static final Logger logger = LoggerFactory.getLogger(ReviewResultPipeline.class);

    ReviewResultPipeline() {
    }

    List<ReviewResult> finalizeResults(List<ReviewResult> results) {
        List<ReviewResult> filtered = filterNonNull(results);
        logCompletionSummary(filtered);
        logger.info("Collected {} review result(s)", filtered.size());
        return filtered;
    }

    private List<ReviewResult> filterNonNull(List<ReviewResult> results) {
        return results.stream()
            .filter(Objects::nonNull)
            .toList();
    }

    private void logCompletionSummary(List<ReviewResult> results) {
        long successCount = countSuccessful(results);
        logger.info(completionSummaryMessage(), results.size(), successCount, results.size() - successCount);
    }

    private long countSuccessful(List<ReviewResult> results) {
        return results.stream().filter(ReviewResult::success).count();
    }

    private String completionSummaryMessage() {
        return "Completed {} reviews (success: {}, failed: {})";
    }

}