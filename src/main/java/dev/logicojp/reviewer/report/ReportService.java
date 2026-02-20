package dev.logicojp.reviewer.report;

import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.ResilienceConfig;
import dev.logicojp.reviewer.service.TemplateService;
import com.github.copilot.sdk.CopilotClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;

/// Factory service for creating report and summary generators.
///
/// Replaces v1 ReportGeneratorFactory with a simpler Micronaut singleton
/// that holds shared dependencies and creates per-invocation instances.
@Singleton
public class ReportService {

    private final TemplateService templateService;
    private final ExecutionConfig.SummarySettings summarySettings;
    private final ResilienceConfig resilienceConfig;

    @Inject
    public ReportService(TemplateService templateService,
                         ExecutionConfig executionConfig,
                         ResilienceConfig resilienceConfig) {
        this.templateService = templateService;
        this.summarySettings = executionConfig.summary();
        this.resilienceConfig = resilienceConfig;
    }

    /// Creates a new ReportGenerator for the given output directory.
    public ReportGenerator createReportGenerator(Path outputDirectory) {
        return new ReportGenerator(outputDirectory, templateService);
    }

    /// Creates a new SummaryGenerator with the given configuration.
    public SummaryGenerator createSummaryGenerator(Path outputDirectory,
                                                    CopilotClient client,
                                                    String summaryModel,
                                                    String reasoningEffort,
                                                    long timeoutMinutes) {
        return new SummaryGenerator(
            outputDirectory,
            client,
            summaryModel,
            reasoningEffort,
            timeoutMinutes,
            templateService,
            summarySettings,
            resilienceConfig.summary()
        );
    }
}
