package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.orchestrator.ReviewOrchestratorFactory;
import dev.logicojp.reviewer.report.ReportGenerator;
import dev.logicojp.reviewer.report.ReportService;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.report.SummaryGenerator;
import dev.logicojp.reviewer.service.CopilotService;
import dev.logicojp.reviewer.target.ReviewTarget;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/// Executes the review orchestration + report generation pipeline.
@Singleton
public class ReviewPipelineExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ReviewPipelineExecutor.class);

    private final ExecutionConfig executionConfig;
    private final CopilotService copilotService;
    private final ReviewOrchestratorFactory orchestratorFactory;
    private final ReportService reportService;
    private final CliOutput output;
    private final CheckpointLifecycleManager checkpointLifecycleManager;

    @jakarta.inject.Inject
    public ReviewPipelineExecutor(ExecutionConfig executionConfig,
                                  CopilotService copilotService,
                                  ReviewOrchestratorFactory orchestratorFactory,
                                  ReportService reportService,
                                  CliOutput output) {
        this.executionConfig = executionConfig;
        this.copilotService = copilotService;
        this.orchestratorFactory = orchestratorFactory;
        this.reportService = reportService;
        this.output = output;
        this.checkpointLifecycleManager = new CheckpointLifecycleManager();
    }

    public int execute(ReviewTarget target,
                       ModelConfig modelConfig,
                       Map<String, AgentConfig> agentConfigs,
                       List<CustomInstruction> customInstructions,
                       Path outputDirectory,
                       String outputConstraints,
                       int parallelism,
                       boolean noSummary,
                       boolean keepCheckpoints,
                       String token) {
        output.println("Starting reviews...");
        LocalDateTime invocationTime = LocalDateTime.now();

        String reasoningEffort = modelConfig.reasoningEffort();
        var effectiveConfig = executionConfig.withParallelism(parallelism);
        List<ReviewResult> results;
        try (var orchestrator = orchestratorFactory.create(
            token, effectiveConfig, customInstructions, reasoningEffort, outputConstraints)) {
            results = orchestrator.executeReviews(agentConfigs, target);
        }

        generateReports(results, outputDirectory);

        if (!noSummary) {
            generateSummary(results, target, modelConfig, outputDirectory, reasoningEffort);
        }

        printCompletionSummary(results, outputDirectory);
        checkpointLifecycleManager.handle(
            Path.of(executionConfig.checkpointDirectory()), keepCheckpoints, invocationTime);
        return ExitCodes.OK;
    }

    private void generateReports(List<ReviewResult> results, Path outputDirectory) {
        output.println("\nGenerating reports...");
        try {
            ReportGenerator generator = reportService.createReportGenerator(outputDirectory);
            List<Path> reports = generator.generateReports(results);
            for (Path report : reports) {
                output.println("  ✓ " + report.getFileName());
            }
        } catch (IOException e) {
            logger.error("Report generation failed: {}", e.getMessage(), e);
            output.errorln("Warning: Report generation failed: " + e.getMessage());
            output.errorln("Review results are available in checkpoint files at: "
                + Path.of(executionConfig.checkpointDirectory()));
        }
    }

    private void generateSummary(List<ReviewResult> results, ReviewTarget target,
                                 ModelConfig modelConfig, Path outputDirectory,
                                 String reasoningEffort) {
        output.println("\nGenerating executive summary...");
        try {
            SummaryGenerator generator = reportService.createSummaryGenerator(
                outputDirectory, copilotService.getClient(),
                modelConfig.summaryModel(), reasoningEffort,
                executionConfig.summaryTimeoutMinutes());
            Path summaryPath = generator.generateSummary(results, target.displayName());
            output.println("  ✓ " + summaryPath.getFileName());
        } catch (IOException e) {
            logger.error("Summary generation failed: {}", e.getMessage(), e);
            output.errorln("Warning: Summary generation failed: " + e.getMessage());
        }
    }

    private void printCompletionSummary(List<ReviewResult> results, Path outputDirectory) {
        long successCount = results.stream().filter(ReviewResult::success).count();
        output.println("");
        output.println("════════════════════════════════════════════════════════════");
        output.println("Review completed!");
        output.println("  Total agents: " + results.size());
        output.println("  Successful: " + successCount);
        output.println("  Failed: " + (results.size() - successCount));
        output.println("  Reports: " + outputDirectory.toAbsolutePath());
        output.println("════════════════════════════════════════════════════════════");
    }

}