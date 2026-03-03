package dev.logicojp.reviewer.report.factory;

import dev.logicojp.reviewer.report.core.ReportGenerator;
import dev.logicojp.reviewer.report.summary.SummaryGenerator;

import dev.logicojp.reviewer.agent.CircuitBreakerFactory;
import dev.logicojp.reviewer.agent.SharedCircuitBreaker;
import dev.logicojp.reviewer.config.SummaryConfig;
import dev.logicojp.reviewer.service.TemplateService;
import com.github.copilot.sdk.CopilotClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;

/// Factory for creating {@link ReportGenerator} and {@link SummaryGenerator} instances.
///
/// Encapsulates the injection of shared services ({@link TemplateService})
/// so that generators can be created with per-invocation parameters
/// (output directory, model, etc.) while remaining testable via DI.
@Singleton
public class ReportGeneratorFactory {

    @FunctionalInterface
    interface ReportGeneratorCreator {
        ReportGenerator create(Path outputDirectory, TemplateService templateService);
    }

    @FunctionalInterface
    interface SummaryGeneratorCreator {
        SummaryGenerator create(Path outputDirectory,
                                CopilotClient client,
                                String summaryModel,
                                String reasoningEffort,
                                long timeoutMinutes,
                                TemplateService templateService,
                                SummaryConfig summaryConfig,
                                SharedCircuitBreaker circuitBreaker);
    }

    private final TemplateService templateService;
    private final SummaryConfig summaryConfig;
    private final ReportGeneratorCreator reportGeneratorCreator;
    private final SummaryGeneratorCreator summaryGeneratorCreator;
    private final SharedCircuitBreaker circuitBreaker;

    @Inject
    public ReportGeneratorFactory(TemplateService templateService,
                                  SummaryConfig summaryConfig,
                                  CircuitBreakerFactory circuitBreakerFactory) {
        this(
            templateService,
            summaryConfig,
            ReportGenerator::new,
            (outputDirectory, client, summaryModel, reasoningEffort, timeoutMinutes, ts, cfg, breaker) ->
                SummaryGenerator.builder(outputDirectory, client, summaryModel, ts)
                    .reasoningEffort(reasoningEffort)
                    .timeoutMinutes(timeoutMinutes)
                    .summaryConfig(cfg)
                    .circuitBreaker(breaker)
                    .build(),
            circuitBreakerFactory.forSummary()
        );
    }

    public ReportGeneratorFactory(TemplateService templateService,
                                  SummaryConfig summaryConfig) {
        this(
            templateService,
            summaryConfig,
            ReportGenerator::new,
            (outputDirectory, client, summaryModel, reasoningEffort, timeoutMinutes, ts, cfg, breaker) ->
                SummaryGenerator.builder(outputDirectory, client, summaryModel, ts)
                    .reasoningEffort(reasoningEffort)
                    .timeoutMinutes(timeoutMinutes)
                    .summaryConfig(cfg)
                    .circuitBreaker(breaker)
                    .build(),
            SharedCircuitBreaker.withDefaultConfig()
        );
    }

    ReportGeneratorFactory(TemplateService templateService,
                           SummaryConfig summaryConfig,
                           ReportGeneratorCreator reportGeneratorCreator,
                           SummaryGeneratorCreator summaryGeneratorCreator,
                           SharedCircuitBreaker circuitBreaker) {
        this.templateService = templateService;
        this.summaryConfig = summaryConfig;
        this.reportGeneratorCreator = reportGeneratorCreator;
        this.summaryGeneratorCreator = summaryGeneratorCreator;
        this.circuitBreaker = circuitBreaker;
    }

    /// Creates a new {@link ReportGenerator} for the given output directory.
    ///
    /// @param outputDirectory Directory to write reports to
    /// @return A new ReportGenerator instance
    public ReportGenerator createReportGenerator(Path outputDirectory) {
        return reportGeneratorCreator.create(outputDirectory, templateService);
    }

    /// Creates a new {@link SummaryGenerator} with the given configuration.
    ///
    /// @param outputDirectory      Directory to write summary to
    /// @param client               Copilot SDK client for AI summary generation
    /// @param summaryModel         LLM model to use for summary generation
    /// @param reasoningEffort      Reasoning effort level (nullable)
    /// @param timeoutMinutes       Timeout for summary generation
    /// @return A new SummaryGenerator instance
    public SummaryGenerator createSummaryGenerator(Path outputDirectory,
                                                    CopilotClient client,
                                                    String summaryModel,
                                                    String reasoningEffort,
                                                    long timeoutMinutes) {
        return summaryGeneratorCreator.create(
            outputDirectory,
            client,
            summaryModel,
            reasoningEffort,
            timeoutMinutes,
            templateService,
            summaryConfig,
            circuitBreaker
        );
    }
}
