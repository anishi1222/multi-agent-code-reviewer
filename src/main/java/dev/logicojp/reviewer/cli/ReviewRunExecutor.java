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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// Executes the review run lifecycle: review execution, report generation, summary generation.
@Singleton
class ReviewRunExecutor {

    private static final String CHECKPOINTS_DIR = ".checkpoints";
    private static final String PASS_REPORTS_DIR = "passes";

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
                context.reasoningEffort(),
                context.noSharedSession(),
                context.invocationTimestamp(),
                context.rubberDuckConfig()
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
        try {
            output.println("Starting reviews...");
            List<ReviewResult> passResults = executeReviews(resolvedToken, context);
            List<ReviewResult> sanitizedPassResults = sanitizePassResults(passResults);
            generatePassReports(sanitizedPassResults, context.outputDirectory());

            List<ReviewResult> mergedResults = ReviewResultMerger.mergeByAgent(sanitizedPassResults);
            List<ReviewResult> finalResults = ReviewOverallSummaryAppender.appendToMergedResults(mergedResults);

            generateFinalOutputs(finalResults, context);

            outputFormatter.printCompletionSummary(finalResults, context.outputDirectory());
            return ExitCodes.OK;
        } finally {
            cleanupCheckpoints(context.outputDirectory());
        }
    }

    private List<ReviewResult> executeReviews(String resolvedToken, ReviewRunRequest context) {
        return reviewRunner.run(resolvedToken, context);
    }

    private void generateFinalOutputs(List<ReviewResult> results, ReviewRunRequest context) {
        generateReports(results, context.outputDirectory());
        generateSummaryIfEnabled(results, context);
    }

    private void generatePassReports(List<ReviewResult> passResults, Path outputDirectory) {
        Path passDirectory = outputDirectory.resolve(CHECKPOINTS_DIR).resolve(PASS_REPORTS_DIR);
        generateAndPrintReports(passResults, passDirectory, "Generating pass reports");
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
        generateAndPrintReports(results, outputDirectory, "Generating reports");
    }

    private void generateAndPrintReports(List<ReviewResult> results, Path directory, String label) {
        output.println("\n" + label + "...");
        List<Path> reports;
        try {
            reports = reportsGenerator.generate(results, directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed: " + label, e);
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

    private void cleanupCheckpoints(Path outputDirectory) {
        Path checkpointsDirectory = outputDirectory.resolve(CHECKPOINTS_DIR);
        if (!Files.exists(checkpointsDirectory)) {
            return;
        }

        try (Stream<Path> pathStream = Files.walk(checkpointsDirectory)) {
            pathStream
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        } catch (IOException | UncheckedIOException e) {
            logger.warn("Failed to cleanup checkpoints directory '{}': {}",
                checkpointsDirectory, e.getMessage());
        }
    }

    public record ReviewRunRequest(
        ReviewTarget target,
        String summaryModel,
        String reasoningEffort,
        String invocationTimestamp,
        Map<String, AgentConfig> agentConfigs,
        int parallelism,
        boolean noSummary,
        boolean noSharedSession,
        Path outputDirectory,
        dev.logicojp.reviewer.config.RubberDuckConfig rubberDuckConfig
    ) {
        public ReviewRunRequest(
            ReviewTarget target,
            String summaryModel,
            String reasoningEffort,
            String invocationTimestamp,
            Map<String, AgentConfig> agentConfigs,
            int parallelism,
            boolean noSummary,
            boolean noSharedSession,
            Path outputDirectory
        ) {
            this(target, summaryModel, reasoningEffort, invocationTimestamp, agentConfigs,
                parallelism, noSummary, noSharedSession, outputDirectory, new dev.logicojp.reviewer.config.RubberDuckConfig());
        }

        public ReviewRunRequest {
            rubberDuckConfig = rubberDuckConfig != null ? rubberDuckConfig : new dev.logicojp.reviewer.config.RubberDuckConfig();
        }

        @Override
        public String toString() {
            return "ReviewRunRequest{target=%s, summaryModel='%s', reasoningEffort='%s', invocationTimestamp='%s', parallelism=%d, noSummary=%s, noSharedSession=%s, outputDirectory=%s, rubberDuck=%s}"
                .formatted(target, summaryModel, reasoningEffort, invocationTimestamp,
                    parallelism, noSummary, noSharedSession, outputDirectory, rubberDuckConfig.enabled());
        }
    }
}
