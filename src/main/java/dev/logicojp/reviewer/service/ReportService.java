package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.PromptBudgetConfig;
import dev.logicojp.reviewer.report.core.ReportGenerator;
import dev.logicojp.reviewer.report.factory.ReportGeneratorFactory;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.summary.SummaryGenerator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/// Service for generating review reports and executive summaries.
@Singleton
public class ReportService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);
    
    private final CopilotService copilotService;
    private final ExecutionConfig executionConfig;
    private final ReportGeneratorFactory reportGeneratorFactory;
    
    @Inject
    public ReportService(
            CopilotService copilotService, 
            ExecutionConfig executionConfig,
            ReportGeneratorFactory reportGeneratorFactory) {
        this.copilotService = copilotService;
        this.executionConfig = executionConfig;
        this.reportGeneratorFactory = reportGeneratorFactory;
    }
    
    /// Generates individual reports for each review result.
    /// @param results List of review results
    /// @param outputDirectory Directory to write reports to
    /// @return List of paths to generated report files
    public List<Path> generateReports(List<ReviewResult> results, Path outputDirectory) 
            throws IOException {
        logger.info("Generating {} individual reports", results.size());

        var generator = createReportGenerator(outputDirectory);
        return generator.generateReports(results);
    }
    
    /// Generates an executive summary from all review results.
    /// @param results List of review results
    /// @param repository Target repository that was reviewed
    /// @param outputDirectory Directory to write summary to
    /// @param summaryModel LLM model to use for summary generation
    /// @param reasoningEffort Reasoning effort level for reasoning models (e.g. "high")
    /// @return Path to the generated summary file
    public Path generateSummary(
            List<ReviewResult> results,
            String repository,
            Path outputDirectory,
            String summaryModel,
            String reasoningEffort) throws IOException {
        return generateSummary(results, repository, outputDirectory, summaryModel, reasoningEffort, null);
    }

    public Path generateSummary(
            List<ReviewResult> results,
            String repository,
            Path outputDirectory,
            String summaryModel,
            String reasoningEffort,
            PromptBudgetConfig promptBudgetConfig) throws IOException {

        logger.info("Generating executive summary using model: {}", summaryModel);

        var generator = createSummaryGenerator(outputDirectory, summaryModel, reasoningEffort, promptBudgetConfig);
        
        return generator.generateSummary(results, repository);
    }

    private ReportGenerator createReportGenerator(Path outputDirectory) {
        return reportGeneratorFactory.createReportGenerator(outputDirectory);
    }

    private SummaryGenerator createSummaryGenerator(Path outputDirectory,
                                                    String summaryModel,
                                                    String reasoningEffort,
                                                    PromptBudgetConfig promptBudgetConfig) {
        if (promptBudgetConfig == null) {
            return reportGeneratorFactory.createSummaryGenerator(
                outputDirectory,
                copilotService.getClient(),
                summaryModel,
                reasoningEffort,
                executionConfig.summaryTimeoutMinutes());
        }
        return reportGeneratorFactory.createSummaryGenerator(
            outputDirectory,
            copilotService.getClient(),
            summaryModel,
            reasoningEffort,
            executionConfig.summaryTimeoutMinutes(),
            promptBudgetConfig);
    }
}
