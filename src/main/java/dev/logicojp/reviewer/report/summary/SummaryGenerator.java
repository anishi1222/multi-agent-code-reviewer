package dev.logicojp.reviewer.report.summary;

import com.github.copilot.CopilotClient;
import dev.logicojp.reviewer.agent.SharedCircuitBreaker;
import dev.logicojp.reviewer.config.SummaryConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.formatter.SummaryFinalReportFormatter;
import dev.logicojp.reviewer.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiFunction;

/// Generates executive summary by aggregating all agent review results.
/// All prompt/template content is loaded from external templates via {@link TemplateService}.
public class SummaryGenerator {

    @FunctionalInterface
    interface AiSummaryBuilder {
        String build(List<ReviewResult> results, String repository);
    }

    /// Groups collaborator dependencies for testability.
    /// Use {@link SummaryCollaborators#defaults} to create production instances.
    record SummaryCollaborators(
        SummaryPromptBuilder summaryPromptBuilder,
        FallbackSummaryBuilder fallbackSummaryBuilder,
        SummaryReportWriter summaryReportWriter,
        AiSummaryBuilder aiSummaryBuilder
    ) {
        static SummaryCollaborators defaults(TemplateService templateService,
                                             SummaryConfig summaryConfig,
                                             SummaryGeneratorConfig config,
                                             String invocationTimestamp) {
            SummaryConfig effective = summaryConfig != null ? summaryConfig : new SummaryConfig(0, 0, 0, 0, 0, 0);
            return new SummaryCollaborators(
                new SummaryPromptBuilder(templateService,
                    effective.maxContentPerAgent(), effective.maxTotalPromptContent(),
                    effective.averageResultContentEstimate(), effective.initialBufferMargin()),
                new FallbackSummaryBuilder(templateService, effective.fallbackExcerptLength(),
                    effective.excerptNormalizationMultiplier()),
                new SummaryReportWriter(
                    config.outputDirectory(),
                    invocationTimestamp,
                    new SummaryFinalReportFormatter(templateService)
                ),
                null
            );
        }

        /// Merges this collaborators instance with defaults, filling in null fields.
        SummaryCollaborators withDefaults(SummaryCollaborators defaults) {
            return new SummaryCollaborators(
                summaryPromptBuilder != null ? summaryPromptBuilder : defaults.summaryPromptBuilder(),
                fallbackSummaryBuilder != null ? fallbackSummaryBuilder : defaults.fallbackSummaryBuilder(),
                summaryReportWriter != null ? summaryReportWriter : defaults.summaryReportWriter(),
                aiSummaryBuilder
            );
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(SummaryGenerator.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    /// Groups the core configuration values for SummaryGenerator.
    record SummaryGeneratorConfig(
        Path outputDirectory,
        String summaryModel,
        String reasoningEffort,
        long timeoutMinutes
    ) {}

    private final String invocationTimestamp;
    private final SummaryPromptBuilder summaryPromptBuilder;
    private final FallbackSummaryBuilder fallbackSummaryBuilder;
    private final SummaryReportWriter summaryReportWriter;
    private final AiSummaryClient aiSummaryClient;
    private final AiSummaryBuilder aiSummaryBuilder;

    public static Builder builder(Path outputDirectory,
                                  CopilotClient client,
                                  String summaryModel,
                                  TemplateService templateService) {
        return new Builder(outputDirectory, client, summaryModel, templateService);
    }

    public static final class Builder {
        private final Path outputDirectory;
        private final CopilotClient client;
        private final String summaryModel;
        private final TemplateService templateService;
        private String reasoningEffort;
        private long timeoutMinutes = 5;
        private SummaryConfig summaryConfig = new SummaryConfig(0, 0, 0, 0, 0, 0);
        private SummaryCollaborators collaborators;
        private BiFunction<List<ReviewResult>, String, String> aiSummaryBuilderOverride;
        private Clock clock = Clock.systemDefaultZone();
        private SharedCircuitBreaker circuitBreaker = SharedCircuitBreaker.forSummaryDomain();

        private Builder(Path outputDirectory,
                        CopilotClient client,
                        String summaryModel,
                        TemplateService templateService) {
            this.outputDirectory = outputDirectory;
            this.client = client;
            this.summaryModel = summaryModel;
            this.templateService = templateService;
        }

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder timeoutMinutes(long timeoutMinutes) {
            this.timeoutMinutes = timeoutMinutes;
            return this;
        }

        public Builder summaryConfig(SummaryConfig summaryConfig) {
            this.summaryConfig = summaryConfig;
            return this;
        }

        public Builder collaborators(SummaryCollaborators collaborators) {
            this.collaborators = collaborators;
            return this;
        }

        /// Overrides only AI summary generation in tests while keeping template formatting logic.
        public Builder aiSummaryBuilder(BiFunction<List<ReviewResult>, String, String> aiSummaryBuilderOverride) {
            this.aiSummaryBuilderOverride = aiSummaryBuilderOverride;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder circuitBreaker(SharedCircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
            return this;
        }

        public SummaryGenerator build() {
            var config = new SummaryGeneratorConfig(outputDirectory, summaryModel, reasoningEffort, timeoutMinutes);
            SummaryCollaborators effectiveCollaborators = collaborators;
            if (aiSummaryBuilderOverride != null) {
                var overrideCollaborators = new SummaryCollaborators(
                    null,
                    null,
                    null,
                    (results, repository) -> aiSummaryBuilderOverride.apply(results, repository)
                );
                effectiveCollaborators = effectiveCollaborators == null
                    ? overrideCollaborators
                    : new SummaryCollaborators(
                        effectiveCollaborators.summaryPromptBuilder(),
                        effectiveCollaborators.fallbackSummaryBuilder(),
                        effectiveCollaborators.summaryReportWriter(),
                        overrideCollaborators.aiSummaryBuilder()
                    );
            }
            return new SummaryGenerator(
                config,
                client,
                templateService,
                summaryConfig,
                effectiveCollaborators,
                clock,
                circuitBreaker
            );
        }
    }

    /// Full-parameter constructor for testing — all collaborators are injectable.
    SummaryGenerator(
            SummaryGeneratorConfig config,
            CopilotClient client,
            TemplateService templateService,
            SummaryConfig summaryConfig,
            SummaryCollaborators collaborators,
            Clock clock,
            SharedCircuitBreaker circuitBreaker) {
        this.invocationTimestamp = LocalDateTime.now(clock).format(TIMESTAMP_FORMATTER);
        SummaryCollaborators defaults = SummaryCollaborators.defaults(
            templateService,
            summaryConfig,
            config,
            invocationTimestamp
        );
        var effective = (collaborators != null ? collaborators : defaults).withDefaults(defaults);
        this.summaryPromptBuilder = effective.summaryPromptBuilder();
        this.fallbackSummaryBuilder = effective.fallbackSummaryBuilder();
        this.summaryReportWriter = effective.summaryReportWriter();
        this.aiSummaryClient = new AiSummaryClient(
            client,
            templateService,
            config.summaryModel(),
            config.reasoningEffort(),
            config.timeoutMinutes(),
            circuitBreaker
        );
        this.aiSummaryBuilder = effective.aiSummaryBuilder() != null
            ? effective.aiSummaryBuilder()
            : this::buildSummaryWithAI;
    }

    /// Generates an executive summary from all review results.
    /// @param results List of review results from all agents
    /// @param repository The repository that was reviewed
    /// @return Path to the generated summary file
    public Path generateSummary(List<ReviewResult> results, String repository) throws IOException {
        logger.info("Generating executive summary from {} review results", results.size());
        String summaryContent = aiSummaryBuilder.build(results, repository);
        Path summaryPath = summaryReportWriter.write(summaryContent, repository, results);
        logger.info("Generated executive summary: {}", summaryPath);
        return summaryPath;
    }

    private String buildSummaryWithAI(List<ReviewResult> results, String repository) {
        String prompt = summaryPromptBuilder.buildSummaryPrompt(results, repository);
        String summary = aiSummaryClient.generate(prompt);
        if (!isNonBlank(summary)) {
            logger.warn("AI summary response was empty, using fallback summary");
            return fallbackSummaryBuilder.buildFallbackSummary(results);
        }
        return summary;
    }

    static long sessionCreateTimeoutMinutes(long totalTimeoutMinutes) {
        return AiSummaryClient.sessionCreateTimeoutMinutes(totalTimeoutMinutes);
    }

    static long messageTimeoutMs(long totalTimeoutMinutes) {
        return AiSummaryClient.messageTimeoutMs(totalTimeoutMinutes);
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
