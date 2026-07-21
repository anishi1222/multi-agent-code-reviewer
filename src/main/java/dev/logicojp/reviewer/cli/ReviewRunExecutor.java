package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.config.PromptBudgetConfig;
import dev.logicojp.reviewer.config.RubberDuckConfig;
import dev.logicojp.reviewer.report.formatter.ReviewOverallSummaryAppender;
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
                context.reasoningEffort(),
                context.invocationTimestamp(),
                context.rubberDuckConfig(),
                context.promptBudgetConfig()
            ),
            reportService::generateReports,
            (results, context) -> reportService.generateSummary(
                results,
                context.target().displayName(),
                context.outputDirectory(),
                context.summaryModel(),
                context.reasoningEffort(),
                context.promptBudgetConfig()
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
        List<ReviewResult> reviewResults = executeReviews(resolvedToken, context);
        List<ReviewResult> finalResults = ReviewOverallSummaryAppender.appendToResults(reviewResults);

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

    public record ReviewRunRequest(
        ReviewTarget target,
        String summaryModel,
        String reasoningEffort,
        String invocationTimestamp,
        Map<String, AgentConfig> agentConfigs,
        int parallelism,
        boolean noSummary,
        Path outputDirectory,
        RubberDuckConfig rubberDuckConfig,
        PromptBudgetConfig promptBudgetConfig
    ) {
        public ReviewRunRequest(
            ReviewTarget target,
            String summaryModel,
            String reasoningEffort,
            String invocationTimestamp,
            Map<String, AgentConfig> agentConfigs,
            int parallelism,
            boolean noSummary,
            Path outputDirectory
        ) {
            this(target, summaryModel, reasoningEffort, invocationTimestamp, agentConfigs,
                parallelism, noSummary, outputDirectory, new RubberDuckConfig(), new PromptBudgetConfig());
        }

        public ReviewRunRequest(
            ReviewTarget target,
            String summaryModel,
            String reasoningEffort,
            String invocationTimestamp,
            Map<String, AgentConfig> agentConfigs,
            int parallelism,
            boolean noSummary,
            Path outputDirectory,
            RubberDuckConfig rubberDuckConfig
        ) {
            this(target, summaryModel, reasoningEffort, invocationTimestamp, agentConfigs,
                parallelism, noSummary, outputDirectory, rubberDuckConfig, new PromptBudgetConfig());
        }

        public ReviewRunRequest {
            rubberDuckConfig = rubberDuckConfig != null ? rubberDuckConfig : new RubberDuckConfig();
            promptBudgetConfig = promptBudgetConfig != null ? promptBudgetConfig : new PromptBudgetConfig();
        }

        @Override
        public String toString() {
            return "ReviewRunRequest{target=%s, summaryModel='%s', reasoningEffort='%s', invocationTimestamp='%s', parallelism=%d, noSummary=%s, outputDirectory=%s, rubberDuck=%s, compactPrompts=%s}"
                .formatted(target, summaryModel, reasoningEffort, invocationTimestamp,
                    parallelism, noSummary, outputDirectory,
                    rubberDuckConfig.enabled(), promptBudgetConfig.compactPrompts());
        }
    }
}
