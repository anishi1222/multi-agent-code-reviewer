package dev.logicojp.reviewer.report.summary;

import dev.logicojp.reviewer.agent.SharedCircuitBreaker;
import dev.logicojp.reviewer.report.util.ReportFileUtils;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.formatter.SummaryFinalReportFormatter;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.SystemMessageMode;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SystemMessageConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.config.SummaryConfig;
import dev.logicojp.reviewer.service.TemplateService;
import dev.logicojp.reviewer.util.RetryExecutor;
import dev.logicojp.reviewer.util.RetryPolicyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.logicojp.reviewer.service.CopilotCliException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

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
        SummaryFinalReportFormatter summaryFinalReportFormatter,
        AiSummaryBuilder aiSummaryBuilder
    ) {
        static SummaryCollaborators defaults(TemplateService templateService,
                                              SummaryConfig summaryConfig,
                                              SummaryGenerator generator) {
            SummaryConfig effective = summaryConfig != null ? summaryConfig : new SummaryConfig(0, 0, 0, 0, 0, 0);
            return new SummaryCollaborators(
                new SummaryPromptBuilder(templateService,
                    effective.maxContentPerAgent(), effective.maxTotalPromptContent(),
                    effective.averageResultContentEstimate(), effective.initialBufferMargin()),
                new FallbackSummaryBuilder(templateService, effective.fallbackExcerptLength(),
                    effective.excerptNormalizationMultiplier()),
                new SummaryFinalReportFormatter(templateService),
                generator::buildSummaryWithAI
            );
        }

        /// Merges this collaborators instance with defaults, filling in null fields.
        SummaryCollaborators withDefaults(SummaryCollaborators defaults) {
            return new SummaryCollaborators(
                summaryPromptBuilder != null ? summaryPromptBuilder : defaults.summaryPromptBuilder(),
                fallbackSummaryBuilder != null ? fallbackSummaryBuilder : defaults.fallbackSummaryBuilder(),
                summaryFinalReportFormatter != null ? summaryFinalReportFormatter : defaults.summaryFinalReportFormatter(),
                aiSummaryBuilder != null ? aiSummaryBuilder : defaults.aiSummaryBuilder()
            );
        }
    }
    
    private static final Logger logger = LoggerFactory.getLogger(SummaryGenerator.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final Pattern INVOCATION_TIMESTAMP_PATTERN =
        Pattern.compile("\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}");
    private static final int AI_SUMMARY_MAX_RETRIES = 1;
    private static final long RETRY_BACKOFF_BASE_MS = 1_000L;
    private static final long RETRY_BACKOFF_MAX_MS = 15_000L;
    
    private final Path outputDirectory;
    private final CopilotClient client;
    private final String summaryModel;
    private final String reasoningEffort;
    private final long timeoutMinutes;
    private final TemplateService templateService;
    private final String invocationTimestamp;
    private final SummaryPromptBuilder summaryPromptBuilder;
    private final FallbackSummaryBuilder fallbackSummaryBuilder;
    private final SummaryFinalReportFormatter summaryFinalReportFormatter;
    private final AiSummaryBuilder aiSummaryBuilder;
    private final SharedCircuitBreaker circuitBreaker;
    
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
        private Clock clock = Clock.systemDefaultZone();
        private SharedCircuitBreaker circuitBreaker = SharedCircuitBreaker.withDefaultConfig();

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

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder circuitBreaker(SharedCircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
            return this;
        }

        public SummaryGenerator build() {
            return new SummaryGenerator(
                outputDirectory,
                client,
                summaryModel,
                reasoningEffort,
                timeoutMinutes,
                templateService,
                summaryConfig,
                collaborators,
                clock,
                circuitBreaker
            );
        }
    }

    public SummaryGenerator(Path outputDirectory,
                            CopilotClient client,
                            String summaryModel,
                            String reasoningEffort,
                            long timeoutMinutes,
                            TemplateService templateService,
                            SummaryConfig summaryConfig) {
        this(
            outputDirectory,
            client,
            summaryModel,
            reasoningEffort,
            timeoutMinutes,
            templateService,
            summaryConfig,
            null,
            Clock.systemDefaultZone(),
            SharedCircuitBreaker.withDefaultConfig()
        );
    }

    public SummaryGenerator(Path outputDirectory,
                            CopilotClient client,
                            String summaryModel,
                            String reasoningEffort,
                            long timeoutMinutes,
                            TemplateService templateService,
                            SummaryConfig summaryConfig,
                            SharedCircuitBreaker circuitBreaker) {
        this(
            outputDirectory,
            client,
            summaryModel,
            reasoningEffort,
            timeoutMinutes,
            templateService,
            summaryConfig,
            null,
            Clock.systemDefaultZone(),
            circuitBreaker
        );
    }

    /// Full-parameter constructor for testing — all collaborators are injectable.
    SummaryGenerator(
            Path outputDirectory,
            CopilotClient client,
            String summaryModel,
            String reasoningEffort,
            long timeoutMinutes,
            TemplateService templateService,
            SummaryConfig summaryConfig,
            SummaryCollaborators collaborators,
            Clock clock,
            SharedCircuitBreaker circuitBreaker) {
        this.outputDirectory = outputDirectory;
        this.client = client;
        this.summaryModel = summaryModel;
        this.reasoningEffort = reasoningEffort;
        this.timeoutMinutes = timeoutMinutes;
        this.templateService = templateService;
        this.circuitBreaker = circuitBreaker;
        this.invocationTimestamp = LocalDateTime.now(clock).format(TIMESTAMP_FORMATTER);
        SummaryCollaborators defaults = SummaryCollaborators.defaults(templateService, summaryConfig, this);
        var effective = (collaborators != null ? collaborators : defaults).withDefaults(defaults);
        this.summaryPromptBuilder = effective.summaryPromptBuilder();
        this.fallbackSummaryBuilder = effective.fallbackSummaryBuilder();
        this.summaryFinalReportFormatter = effective.summaryFinalReportFormatter();
        this.aiSummaryBuilder = effective.aiSummaryBuilder();
    }
    
    /// Generates an executive summary from all review results.
    /// @param results List of review results from all agents
    /// @param repository The repository that was reviewed
    /// @return Path to the generated summary file
    public Path generateSummary(List<ReviewResult> results, String repository) throws IOException {
        Path summaryOutputDirectory = resolveSummaryOutputDirectory();
        ensureOutputDirectory(summaryOutputDirectory);

        String timestamp = invocationTimestamp;
        String filename = "executive_summary_%s.md".formatted(timestamp);
        Path summaryPath = summaryOutputDirectory.resolve(filename);
        
        logger.info("Generating executive summary from {} review results", results.size());
        
        // Build the summary using AI
        String summaryContent = aiSummaryBuilder.build(results, repository);
        
        // Build the final report
        String finalReport = summaryFinalReportFormatter.format(summaryContent, repository, results, timestamp);
        ReportFileUtils.writeSecureString(summaryPath, finalReport);
        
        logger.info("Generated executive summary: {}", summaryPath);
        return summaryPath;
    }
    
    private String buildSummaryWithAI(List<ReviewResult> results, String repository) {
        logger.info("Using model for summary: {}", summaryModel);
        String prompt = summaryPromptBuilder.buildSummaryPrompt(results, repository);
        RetryExecutor<String> retryExecutor = new RetryExecutor<>(
            AI_SUMMARY_MAX_RETRIES,
            RETRY_BACKOFF_BASE_MS,
            RETRY_BACKOFF_MAX_MS,
            Thread::sleep,
            circuitBreaker
        );

        String summary = retryExecutor.execute(
            () -> runSummaryAttempt(prompt),
            e -> fallbackSummary(results, "Failed to create or execute summary session: " + e.getMessage()),
            this::isNonBlank,
            _ -> false,
            this::isTransientFailure,
            new RetryExecutor.RetryObserver<>() {
                @Override
                public void onCircuitOpen() {
                    logger.warn("Summary generation skipped by open circuit breaker");
                }

                @Override
                public void onRetryableException(int attempt, int totalAttempts, Exception exception) {
                    logger.warn("Summary generation failed on attempt {}/{}: {}. Retrying...",
                        attempt, totalAttempts, exception.getMessage(), exception);
                }

                @Override
                public void onFinalException(int attempt,
                                             int totalAttempts,
                                             Exception exception,
                                             boolean transientFailure) {
                    if (!transientFailure) {
                        logger.warn("Summary generation failed without retry: {}", exception.getMessage(), exception);
                    }
                }
            }
        );

        if (!isNonBlank(summary)) {
            return fallbackSummary(results, "AI summary response was empty");
        }
        return summary;
    }

    private String runSummaryAttempt(String prompt)
            throws ExecutionException, TimeoutException {
        var sessionConfig = createSummarySessionConfig();
        long sessionCreateTimeoutMinutes = sessionCreateTimeoutMinutes(timeoutMinutes);
        long timeoutMs = messageTimeoutMs(timeoutMinutes);
        int contentAttempts = AI_SUMMARY_MAX_RETRIES + 1;

        try (CopilotSession session = client.createSession(sessionConfig)
            .get(sessionCreateTimeoutMinutes, TimeUnit.MINUTES)) {
            for (int attempt = 1; attempt <= contentAttempts; attempt++) {
                var response = session
                    .sendAndWait(new MessageOptions().setPrompt(prompt), timeoutMs)
                    .get(timeoutMinutes, TimeUnit.MINUTES);
                String content = response.getData().content();
                if (isNonBlank(content)) {
                    return content;
                }

                if (attempt < contentAttempts) {
                    RetryPolicyUtils.sleepWithBackoff(RETRY_BACKOFF_BASE_MS, RETRY_BACKOFF_MAX_MS, attempt);
                    logger.warn(
                        "Summary generation returned empty content on attempt {}/{} in same session. Retrying...",
                        attempt,
                        contentAttempts
                    );
                }
            }
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CopilotCliException("Summary generation interrupted", ex);
        }
    }

    static long sessionCreateTimeoutMinutes(long totalTimeoutMinutes) {
        return Math.max(1L, totalTimeoutMinutes / 4L);
    }

    static long messageTimeoutMs(long totalTimeoutMinutes) {
        return TimeUnit.MINUTES.toMillis(totalTimeoutMinutes);
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isTransientFailure(Exception exception) {
        return RetryPolicyUtils.isTransientException(exception);
    }

    private SessionConfig createSummarySessionConfig() {
        String systemPrompt = templateService.getSummarySystemPrompt();
        var sessionConfig = new SessionConfig()
            .setModel(summaryModel)
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.REPLACE)
                .setContent(systemPrompt));

        applyReasoningEffort(sessionConfig);
        return sessionConfig;
    }

    private void applyReasoningEffort(SessionConfig sessionConfig) {
        String effort = ModelConfig.resolveReasoningEffort(summaryModel, reasoningEffort);
        if (effort != null) {
            logger.info("Setting reasoning effort '{}' for model: {}", effort, summaryModel);
            sessionConfig.setReasoningEffort(effort);
        }
    }

    private String fallbackSummary(List<ReviewResult> results, String reason) {
        logger.warn("{}, using fallback summary", reason);
        return fallbackSummaryBuilder.buildFallbackSummary(results);
    }

    private Path resolveSummaryOutputDirectory() {
        Path invocationDirectory = outputDirectory.getFileName();
        if (invocationDirectory == null) {
            return outputDirectory;
        }
        if (!INVOCATION_TIMESTAMP_PATTERN.matcher(invocationDirectory.toString()).matches()) {
            return outputDirectory;
        }
        Path parent = outputDirectory.getParent();
        return parent != null ? parent : outputDirectory;
    }

    private void ensureOutputDirectory(Path directory) throws IOException {
        ReportFileUtils.ensureOutputDirectory(directory);
    }
}
