package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.domain.report.ReviewResult;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/// Finalizes orchestrated review results: collect, filter, and log.
///
/// Purified from {@code orchestrator.ReviewResultPipeline}:
/// - Replaced SLF4J with {@code java.util.logging}.
/// - Fixed {@code ReviewResult} import to {@code domain.report.ReviewResult}.
public final class ReviewResultPipeline {

    private static final Logger logger = Logger.getLogger(ReviewResultPipeline.class.getName());

    public ReviewResultPipeline() {
    }

    public List<ReviewResult> finalizeResults(List<ReviewResult> results, int reviewPasses) {
        List<ReviewResult> filtered = filterNonNull(results);
        logCompletionSummary(filtered);
        logger.info("Collected " + filtered.size() + " raw pass result(s) (reviewPasses=" + reviewPasses + ")");
        return filtered;
    }

    private List<ReviewResult> filterNonNull(List<ReviewResult> results) {
        return results.stream()
            .filter(Objects::nonNull)
            .toList();
    }

    private void logCompletionSummary(List<ReviewResult> results) {
        long successCount = results.stream().filter(ReviewResult::success).count();
        logger.info("Completed " + results.size() + " reviews (success: " + successCount
            + ", failed: " + (results.size() - successCount) + ")");
    }
}
