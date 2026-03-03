package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.merger.ReviewResultMerger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/// Finalizes orchestrated review results: collect, filter, log, and optional merge.
final class ReviewResultPipeline {

    private static final Logger logger = LoggerFactory.getLogger(ReviewResultPipeline.class);

    ReviewResultPipeline() {
    }

    List<ReviewResult> finalizeResults(List<ReviewResult> results, int reviewPasses) {
        List<ReviewResult> filtered = filterNonNull(results);
        logCompletionSummary(filtered);
        if (reviewPasses <= 1) {
            logger.info("Skipping merge normalization for single-pass run (results={})", filtered.size());
            return filtered;
        }
        return mergeAndLog(filtered, reviewPasses);
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

    private List<ReviewResult> mergeAndLog(List<ReviewResult> filtered, int reviewPasses) {
        List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(filtered);
        logger.info("Normalized {} result(s) into {} agent result(s) (reviewPasses={})",
            filtered.size(), merged.size(), reviewPasses);
        return merged;
    }
}