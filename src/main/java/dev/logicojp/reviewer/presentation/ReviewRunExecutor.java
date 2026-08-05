package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.GenerateReportPort;
import dev.logicojp.reviewer.application.port.inbound.ReportOptions;
import dev.logicojp.reviewer.application.port.inbound.ReportOutput;
import dev.logicojp.reviewer.application.port.inbound.ReviewRequest;
import dev.logicojp.reviewer.application.port.inbound.RunReviewPort;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.presentation.formatter.ReviewOutputFormatter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/// Executes a complete code review run via inbound ports.
///
/// Delegates all orchestration to {@link RunReviewPort} and all report output
/// to {@link GenerateReportPort}. No direct infrastructure imports.
@Singleton
public class ReviewRunExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ReviewRunExecutor.class);

    private final RunReviewPort runReviewPort;
    private final GenerateReportPort generateReportPort;
    private final ReviewOutputFormatter outputFormatter;

    @Inject
    public ReviewRunExecutor(
            RunReviewPort runReviewPort,
            GenerateReportPort generateReportPort,
            ReviewOutputFormatter outputFormatter) {
        this.runReviewPort = runReviewPort;
        this.generateReportPort = generateReportPort;
        this.outputFormatter = outputFormatter;
    }

    /// Runs the review for the given request.
    ///
    /// @param request  fully-populated application DTO
    /// @return exit code (0 = success, non-zero = failure)
    public int execute(ReviewRequest request) {
        Path outputDir = request.outputDir();
        try {
            List<ReviewResult> results = runReviewPort.execute(request);
            ReportOutput reportOutput = generateReport(request, results);
            outputFormatter.printCompletionSummary(results, outputDir);
            if (reportOutput.hasSummary()) {
                printSummary(reportOutput.summaryText());
            }
            return allSucceeded(results) ? ExitCodes.OK : ExitCodes.PARTIAL_FAILURE;
        } catch (Exception e) {
            logger.error("Review execution failed", e);
            throw e;
        }
    }

    private ReportOutput generateReport(ReviewRequest request, List<ReviewResult> results) {
        ReportOptions reportOptions = new ReportOptions(
            request.outputDir(), "markdown", request.noSummary());
        return generateReportPort.generate(results, reportOptions);
    }

    private void printSummary(String summaryText) {
        System.out.println("");
        System.out.println("Executive Summary");
        System.out.println("─────────────────");
        System.out.println(summaryText);
    }

    private boolean allSucceeded(List<ReviewResult> results) {
        return results.stream().allMatch(ReviewResult::success);
    }
}
