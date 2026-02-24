package dev.logicojp.reviewer.report;

import dev.logicojp.reviewer.config.ExecutionConfig.SummarySettings;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.config.ResilienceConfig;
import dev.logicojp.reviewer.service.CopilotCliException;
import dev.logicojp.reviewer.service.TemplateService;
import dev.logicojp.reviewer.util.ApiCircuitBreaker;
import dev.logicojp.reviewer.util.BackoffUtils;
import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.SystemMessageMode;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SystemMessageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/// Generates the executive summary by aggregating all agent review results.
///
/// Merges v1 SummaryGenerator, SummaryPromptBuilder, FallbackSummaryBuilder,
/// and SummaryFinalReportFormatter into a single class.
///
/// Key preserved behaviours:
/// - SystemMessageMode.REPLACE (not APPEND)
/// - Reasoning effort is conditionally applied based on the model
/// - AI failure produces a fallback summary (never throws)
/// - Content truncation via maxContentPerAgent / maxTotalPromptContent
public class SummaryGenerator {

    private static final Logger logger = LoggerFactory.getLogger(SummaryGenerator.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final Pattern INVOCATION_TIMESTAMP_PATTERN =
        Pattern.compile("\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /// Grouped configuration for SummaryGenerator construction.
    public record SummaryConfig(
        Path outputDirectory,
        String summaryModel,
        String reasoningEffort,
        long timeoutMinutes,
        SummarySettings summarySettings,
        ResilienceConfig.OperationSettings resilienceSettings
    ) {
        public SummaryConfig {
            summarySettings = summarySettings != null ? summarySettings : new SummarySettings(0, 0, 0, 0, 0, 0);
            resilienceSettings = resilienceSettings != null
                ? resilienceSettings : ResilienceConfig.OperationSettings.summaryDefaults();
        }
    }

    private final Path outputDirectory;
    private final CopilotClient client;
    private final String summaryModel;
    private final String reasoningEffort;
    private final long timeoutMinutes;
    private final TemplateService templateService;
    private final SummarySettings summarySettings;
    private final String invocationTimestamp;
    private final ApiCircuitBreaker apiCircuitBreaker;
    private final int maxAttempts;
    private final long backoffBaseMs;
    private final long backoffMaxMs;

    public SummaryGenerator(
            SummaryConfig config,
            CopilotClient client,
            TemplateService templateService) {
        this(config, client, templateService, Clock.systemDefaultZone());
    }

    SummaryGenerator(
            SummaryConfig config,
            CopilotClient client,
            TemplateService templateService,
            Clock clock) {
        this.outputDirectory = config.outputDirectory();
        this.client = client;
        this.summaryModel = config.summaryModel();
        this.reasoningEffort = config.reasoningEffort();
        this.timeoutMinutes = config.timeoutMinutes();
        this.templateService = templateService;
        this.summarySettings = config.summarySettings();
        this.invocationTimestamp = resolveInvocationTimestamp(config.outputDirectory(), clock);
        var settings = config.resilienceSettings();
        this.apiCircuitBreaker = new ApiCircuitBreaker(
            settings.failureThreshold(),
            TimeUnit.SECONDS.toMillis(settings.openDurationSeconds()),
            Clock.systemUTC());
        this.maxAttempts = settings.maxAttempts();
        this.backoffBaseMs = settings.backoffBaseMs();
        this.backoffMaxMs = settings.backoffMaxMs();
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /// Generates an executive summary from all review results.
    public Path generateSummary(List<ReviewResult> results, String repository) throws IOException {
        Path summaryOutputDirectory = resolveSummaryOutputDirectory();
        ReportGenerator.ensureOutputDirectory(summaryOutputDirectory);

        String filename = "executive_summary_%s.md".formatted(invocationTimestamp);
        Path summaryPath = summaryOutputDirectory.resolve(filename);

        logger.info("Generating executive summary from {} review results", results.size());

        String summaryContent = buildSummaryWithAI(results, repository);
        String finalReport = formatFinalReport(summaryContent, repository, results, invocationTimestamp);
        try {
            ReportGenerator.writeSecureString(summaryPath, finalReport);
        } catch (IOException e) {
            logger.error("Failed to write summary to {}: {}. Outputting to stderr as fallback.",
                summaryPath, e.getMessage());
            System.err.println("=== Executive Summary (file write failed) ===");
            System.err.println(finalReport);
            throw e;
        }

        logger.info("Generated executive summary: {}", summaryPath);
        return summaryPath;
    }

    // ------------------------------------------------------------------
    // AI summary generation
    // ------------------------------------------------------------------

    private String buildSummaryWithAI(List<ReviewResult> results, String repository) {
        logger.info("Using model for summary: {}", summaryModel);
        var sessionConfig = createSummarySessionConfig();
        long timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes);
        String prompt = buildSummaryPrompt(results, repository);

        if (!apiCircuitBreaker.isRequestAllowed()) {
            long remainingMs = apiCircuitBreaker.remainingOpenMs();
            return fallbackSummary(results,
                "Copilot API circuit breaker is open (remaining " + remainingMs + " ms)");
        }

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (!apiCircuitBreaker.isRequestAllowed()) {
                long remainingMs = apiCircuitBreaker.remainingOpenMs();
                return fallbackSummary(results,
                    "Copilot API circuit breaker is open (remaining " + remainingMs + " ms)");
            }

            try (CopilotSession session = client.createSession(sessionConfig)
                .get(Math.max(1, timeoutMinutes / 4), TimeUnit.MINUTES)) {
                var response = session
                    .sendAndWait(new MessageOptions().setPrompt(prompt), timeoutMs)
                    .get(timeoutMinutes, TimeUnit.MINUTES);
                String content = response.getData().content();
                if (content == null || content.isBlank()) {
                    apiCircuitBreaker.recordFailure();
                    if (attempt < maxAttempts) {
                        BackoffUtils.sleepWithJitterQuietly(attempt, backoffBaseMs, backoffMaxMs);
                        continue;
                    }
                    return fallbackSummary(results, "AI summary response was empty");
                }

                apiCircuitBreaker.recordSuccess();
                return content;
            } catch (ExecutionException | TimeoutException e) {
                apiCircuitBreaker.recordFailure();
                if (attempt < maxAttempts) {
                    logger.warn("Summary generation attempt {}/{} failed: {}",
                        attempt, maxAttempts, e.getMessage());
                    BackoffUtils.sleepWithJitterQuietly(attempt, backoffBaseMs, backoffMaxMs);
                    continue;
                }
                return fallbackSummary(results,
                    "Failed to create summary session or generate summary: " + e.getMessage());
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                throw new CopilotCliException("Summary generation interrupted");
            }
        }

        return fallbackSummary(results, "Summary generation exhausted retry attempts");
    }

    private SessionConfig createSummarySessionConfig() {
        String systemPrompt = templateService.getSummarySystemPrompt();
        var sessionConfig = new SessionConfig()
            .setModel(summaryModel)
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.REPLACE)
                .setContent(systemPrompt));

        String effort = ModelConfig.resolveReasoningEffort(summaryModel, reasoningEffort);
        if (effort != null) {
            logger.info("Setting reasoning effort '{}' for model: {}", effort, summaryModel);
            sessionConfig.setReasoningEffort(effort);
        }
        return sessionConfig;
    }

    // ------------------------------------------------------------------
    // Prompt building (from v1 SummaryPromptBuilder)
    // ------------------------------------------------------------------

    private String buildSummaryPrompt(List<ReviewResult> results, String repository) {
        int maxContent = summarySettings.maxContentPerAgent();
        int maxTotal = summarySettings.maxTotalPromptContent();

        var resultsSection = new StringBuilder(
            Math.min(
                results.size() * summarySettings.averageResultContentEstimate(),
                maxTotal + summarySettings.initialBufferMargin()
            )
        );
        int totalContentSize = 0;

        String successTemplate = templateService.loadTemplateContent(
            templateService.getConfig().summary().resultEntry());
        String errorTemplate = templateService.loadTemplateContent(
            templateService.getConfig().summary().resultErrorEntry());

        for (ReviewResult result : results) {
            if (result.success()) {
                int remaining = maxTotal - totalContentSize;
                if (remaining <= 0) {
                    break;
                }
                String content = clipContent(result.content(), maxContent, remaining);
                totalContentSize += content.length();
                resultsSection.append(templateService.applyPlaceholders(successTemplate,
                    Map.of(
                        "displayName", result.agentConfig().displayName(),
                        "content", content
                    )));
            } else {
                resultsSection.append(templateService.applyPlaceholders(errorTemplate,
                    Map.of(
                        "displayName", result.agentConfig().displayName(),
                        "errorMessage", result.errorMessage() != null ? result.errorMessage() : ""
                    )));
            }
        }

        return templateService.getSummaryUserPrompt(Map.of(
            "repository", repository,
            "results", resultsSection.toString()));
    }

    private String clipContent(String content, int maxPerAgent, int remaining) {
        String safe = content != null ? content : "";
        int maxAllowed = Math.min(maxPerAgent, remaining);
        if (safe.length() <= maxAllowed) {
            return safe;
        }
        return safe.substring(0, maxAllowed) + "\n\n... (truncated for summary)";
    }

    // ------------------------------------------------------------------
    // Fallback summary (from v1 FallbackSummaryBuilder)
    // ------------------------------------------------------------------

    private String fallbackSummary(List<ReviewResult> results, String reason) {
        logger.warn("{}, using fallback summary", reason);
        return buildFallbackContent(results);
    }

    private String buildFallbackContent(List<ReviewResult> results) {
        String tableRows = buildFallbackTableRows(results);
        String agentSummaries = buildFallbackAgentSummaries(results);
        return templateService.getFallbackSummaryTemplate(Map.of(
            "tableRows", tableRows,
            "agentSummaries", agentSummaries));
    }

    private String buildFallbackTableRows(List<ReviewResult> results) {
        var sb = new StringBuilder();
        for (ReviewResult result : results) {
            sb.append(templateService.getFallbackAgentRow(Map.of(
                "displayName", result.agentConfig().displayName(),
                "content", excerpt(result)
            )));
        }
        return sb.toString();
    }

    private String buildFallbackAgentSummaries(List<ReviewResult> results) {
        var sb = new StringBuilder();
        for (ReviewResult result : results) {
            if (result.success()) {
                sb.append(templateService.getFallbackAgentSuccess(Map.of(
                    "displayName", result.agentConfig().displayName(),
                    "content", excerpt(result)
                )));
            } else {
                sb.append(templateService.getFallbackAgentFailure(Map.of(
                    "displayName", result.agentConfig().displayName(),
                    "errorMessage", result.errorMessage() != null ? result.errorMessage() : ""
                )));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String excerpt(ReviewResult result) {
        if (result == null || !result.success() || result.content() == null || result.content().isBlank()) {
            return "N/A";
        }
        int excerptLength = summarySettings.fallbackExcerptLength();
        String content = result.content();
        var sb = new StringBuilder(excerptLength + 4);
        boolean lastWasSpace = true;
        for (int i = 0; i < content.length() && sb.length() < excerptLength; i++) {
            char c = content.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!lastWasSpace) { sb.append(' '); lastWasSpace = true; }
            } else {
                sb.append(c); lastWasSpace = false;
            }
        }
        String normalized = sb.toString().strip();
        return normalized.length() <= excerptLength ? normalized : normalized.substring(0, excerptLength) + "...";
    }

    // ------------------------------------------------------------------
    // Final report formatting (from v1 SummaryFinalReportFormatter)
    // ------------------------------------------------------------------

    private String formatFinalReport(String summaryContent, String repository,
                                     List<ReviewResult> results, String date) {
        String reportLinks = buildReportLinks(results);
        String findingsSummary = resolveFindingsSummary(results);

        var placeholders = new HashMap<String, String>();
        placeholders.put("date", date);
        placeholders.put("repository", repository);
        placeholders.put("agentCount", String.valueOf(results.size()));
        long successCount = results.stream().filter(ReviewResult::success).count();
        placeholders.put("successCount", String.valueOf(successCount));
        placeholders.put("failureCount", String.valueOf(results.size() - successCount));
        placeholders.put("summaryContent", summaryContent != null ? summaryContent : "");
        placeholders.put("findingsSummary", findingsSummary);
        placeholders.put("reportLinks", reportLinks);
        return templateService.getExecutiveSummaryTemplate(placeholders);
    }

    private String resolveFindingsSummary(List<ReviewResult> results) {
        String summary = FindingsExtractor.buildFindingsSummary(results);
        return summary.isEmpty() ? "指摘事項はありません。" : summary;
    }

    private String buildReportLinks(List<ReviewResult> results) {
        var sb = new StringBuilder();
        for (ReviewResult result : results) {
            String safeName = ReportGenerator.sanitizeAgentName(result.agentConfig().name());
            String filename = ReportFileNames.agentReportFileName(safeName);
            sb.append(templateService.getReportLinkEntry(Map.of(
                "displayName", result.agentConfig().displayName(),
                "filename", filename)));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Output directory resolution
    // ------------------------------------------------------------------

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

    static String resolveInvocationTimestamp(Path outputDirectory, Clock clock) {
        Path invocationDirectory = outputDirectory != null ? outputDirectory.getFileName() : null;
        if (invocationDirectory != null) {
            String value = invocationDirectory.toString();
            if (INVOCATION_TIMESTAMP_PATTERN.matcher(value).matches()) {
                return value;
            }
        }
        return LocalDateTime.now(clock).format(TIMESTAMP_FORMATTER);
    }
}
