package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.finding.ReviewFindingParser;
import dev.logicojp.reviewer.report.merger.ReviewOverallSummaryAppender;
import dev.logicojp.reviewer.report.merger.ReviewResultMerger;
import dev.logicojp.reviewer.service.ReportService;
import dev.logicojp.reviewer.service.ReviewService;
import dev.logicojp.reviewer.target.ReviewTarget;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/// Executes the review run lifecycle: review execution, report generation, summary generation.
@Singleton
class ReviewRunExecutor {

    @FunctionalInterface
    interface ReviewRunner {
        List<ReviewResult> run(String resolvedToken, ReviewRunRequest context);
    }

    @FunctionalInterface
    interface ReportsGenerator {
        List<Path> generate(List<ReviewResult> results, Path outputDirectory) throws IOException;
    }

    @FunctionalInterface
    interface SummaryGeneratorRunner {
        Path generate(List<ReviewResult> results, ReviewRunRequest context) throws IOException;
    }

    private static final Logger logger = LoggerFactory.getLogger(ReviewRunExecutor.class);

    private final ReviewOutputFormatter outputFormatter;
    private final CliOutput output;
    private final ReviewRunner reviewRunner;
    private final ReportsGenerator reportsGenerator;
    private final SummaryGeneratorRunner summaryGeneratorRunner;

    @Inject
    public ReviewRunExecutor(ReviewService reviewService,
                             ReportService reportService,
                             ReviewOutputFormatter outputFormatter,
                             CliOutput output) {
        this(
            reviewService,
            reportService,
            outputFormatter,
            output,
            (resolvedToken, context) -> reviewService.executeReviews(
                context.agentConfigs(),
                context.target(),
                resolvedToken,
                context.parallelism(),
                context.reasoningEffort()
            ),
            reportService::generateReports,
            (results, context) -> reportService.generateSummary(
                results,
                context.target().displayName(),
                context.outputDirectory(),
                context.summaryModel(),
                context.reasoningEffort()
            )
        );
    }

    ReviewRunExecutor(ReviewService reviewService,
                      ReportService reportService,
                      ReviewOutputFormatter outputFormatter,
                      CliOutput output,
                      ReviewRunner reviewRunner,
                      ReportsGenerator reportsGenerator,
                      SummaryGeneratorRunner summaryGeneratorRunner) {
        this.outputFormatter = outputFormatter;
        this.output = output;
        this.reviewRunner = reviewRunner;
        this.reportsGenerator = reportsGenerator;
        this.summaryGeneratorRunner = summaryGeneratorRunner;
    }

    public int execute(String resolvedToken, ReviewRunRequest context) {
        output.println("Starting reviews...");
        List<ReviewResult> passResults = executeReviews(resolvedToken, context);
        List<ReviewResult> sanitizedPassResults = sanitizePassResults(passResults);
        generatePassReports(sanitizedPassResults, context.outputDirectory());

        List<ReviewResult> mergedResults = ReviewResultMerger.mergeByAgent(sanitizedPassResults);
        List<ReviewResult> finalResults = ReviewOverallSummaryAppender.appendToMergedResults(mergedResults);

        generateFinalOutputs(finalResults, context);

        outputFormatter.printCompletionSummary(finalResults, context.outputDirectory());
        return ExitCodes.OK;
    }

    private List<ReviewResult> executeReviews(String resolvedToken, ReviewRunRequest context) {
        return reviewRunner.run(resolvedToken, context);
    }

    private void generateFinalOutputs(List<ReviewResult> results, ReviewRunRequest context) {
        generateReports(results, context.outputDirectory());
        generateSummaryIfEnabled(results, context);
    }

    private void generatePassReports(List<ReviewResult> passResults, Path outputDirectory) {
        output.println("\nGenerating pass reports...");
        Path passDirectory = outputDirectory.resolve("passes");
        List<Path> reports;
        try {
            reports = reportsGenerator.generate(passResults, passDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Pass report generation failed", e);
        }
        for (Path report : reports) {
            output.println("  ✓ " + report.getFileName());
        }
    }

    private List<ReviewResult> sanitizePassResults(List<ReviewResult> passResults) {
        return passResults.stream()
            .map(this::stripOverallSummary)
            .toList();
    }

    private ReviewResult stripOverallSummary(ReviewResult result) {
        if (result == null || !result.success() || result.content() == null || result.content().isBlank()) {
            return result;
        }
        String strippedContent = ReviewFindingParser.stripOverallSummary(result.content());
        return ReviewResult.builder()
            .agentConfig(result.agentConfig())
            .repository(result.repository())
            .content(strippedContent)
            .success(true)
            .errorMessage(result.errorMessage())
            .timestamp(result.timestamp())
            .build();
    }

    private void generateSummaryIfEnabled(List<ReviewResult> results, ReviewRunRequest context) {
        if (shouldGenerateSummary(context)) {
            generateSummary(results, context);
        }
    }

    private void generateReports(List<ReviewResult> results, Path outputDirectory) {
        output.println("\nGenerating reports...");
        List<Path> reports;
        try {
            reports = reportsGenerator.generate(results, outputDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Report generation failed", e);
        }
        for (Path report : reports) {
            output.println("  ✓ " + report.getFileName());
        }
    }

    private boolean shouldGenerateSummary(ReviewRunRequest context) {
        return !context.noSummary();
    }

    private void generateSummary(List<ReviewResult> results, ReviewRunRequest context) {
        output.println("\nGenerating executive summary...");
        try {
            Path summaryPath = summaryGeneratorRunner.generate(results, context);
            output.println("  ✓ " + summaryPath.getFileName());
        } catch (IOException e) {
            logger.error("Summary generation failed: {}", e.getMessage(), e);
            output.errorln("Warning: Summary generation failed: " + e.getMessage());
        }
    }

    public record ReviewRunRequest(
        ReviewTarget target,
        String summaryModel,
        String reasoningEffort,
        Map<String, AgentConfig> agentConfigs,
        int parallelism,
        boolean noSummary,
        Path outputDirectory
    ) {
        @Override
        public String toString() {
            return "ReviewRunRequest{target=%s, summaryModel='%s', reasoningEffort='%s', parallelism=%d, noSummary=%s, outputDirectory=%s}"
                .formatted(target, summaryModel, reasoningEffort, parallelism, noSummary, outputDirectory);
        }
    }
}
