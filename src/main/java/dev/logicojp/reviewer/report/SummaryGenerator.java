package dev.logicojp.reviewer.report;

import dev.logicojp.reviewer.config.ExecutionConfig.SummarySettings;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.service.CopilotService;
import dev.logicojp.reviewer.service.TemplateService;
import dev.logicojp.reviewer.util.ApiCircuitBreaker;
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
import java.util.concurrent.ThreadLocalRandom;
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
    private static final ApiCircuitBreaker API_CIRCUIT_BREAKER = ApiCircuitBreaker.copilotApi();
    private static final int SUMMARY_MAX_ATTEMPTS = 3;
    private static final long BACKOFF_BASE_MS = 500L;
    private static final long BACKOFF_MAX_MS = 4000L;

    private final Path outputDirectory;
    private final CopilotClient client;
    private final String summaryModel;
    private final String reasoningEffort;
    private final long timeoutMinutes;
    private final TemplateService templateService;
    private final SummarySettings summarySettings;
    private final String invocationTimestamp;

    public SummaryGenerator(
            Path outputDirectory,
            CopilotClient client,
            String summaryModel,
            String reasoningEffort,
            long timeoutMinutes,
            TemplateService templateService,
            SummarySettings summarySettings) {
        this(outputDirectory, client, summaryModel, reasoningEffort, timeoutMinutes,
            templateService, summarySettings, Clock.systemDefaultZone());
    }

    SummaryGenerator(
            Path outputDirectory,
            CopilotClient client,
            String summaryModel,
            String reasoningEffort,
            long timeoutMinutes,
            TemplateService templateService,
            SummarySettings summarySettings,
            Clock clock) {
        this.outputDirectory = outputDirectory;
        this.client = client;
        this.summaryModel = summaryModel;
        this.reasoningEffort = reasoningEffort;
        this.timeoutMinutes = timeoutMinutes;
        this.templateService = templateService;
        this.summarySettings = summarySettings;
        this.invocationTimestamp = LocalDateTime.now(clock).format(TIMESTAMP_FORMATTER);
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
        ReportGenerator.writeSecureString(summaryPath, finalReport);

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

        for (int attempt = 1; attempt <= SUMMARY_MAX_ATTEMPTS; attempt++) {
            if (!API_CIRCUIT_BREAKER.isRequestAllowed()) {
                long remainingMs = API_CIRCUIT_BREAKER.remainingOpenMs();
                return fallbackSummary(results,
                    "Copilot API circuit breaker is open (remaining " + remainingMs + " ms)");
            }

            try (CopilotSession session = client.createSession(sessionConfig)
                    .get(timeoutMinutes, TimeUnit.MINUTES)) {
                String prompt = buildSummaryPrompt(results, repository);
                var response = session
                    .sendAndWait(new MessageOptions().setPrompt(prompt), timeoutMs)
                    .get(timeoutMinutes, TimeUnit.MINUTES);
                String content = response.getData().content();
                if (content == null || content.isBlank()) {
                    API_CIRCUIT_BREAKER.recordFailure();
                    if (attempt < SUMMARY_MAX_ATTEMPTS) {
                        sleepWithJitter(attempt);
                        continue;
                    }
                    return fallbackSummary(results, "AI summary response was empty");
                }

                API_CIRCUIT_BREAKER.recordSuccess();
                return content;
            } catch (ExecutionException | TimeoutException e) {
                API_CIRCUIT_BREAKER.recordFailure();
                if (attempt < SUMMARY_MAX_ATTEMPTS) {
                    logger.warn("Summary generation attempt {}/{} failed: {}",
                        attempt, SUMMARY_MAX_ATTEMPTS, e.getMessage());
                    sleepWithJitter(attempt);
                    continue;
                }
                return fallbackSummary(results,
                    "Failed to create or execute summary session: " + e.getMessage());
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                throw new CopilotService.CliException("Summary generation interrupted");
            }
        }

        return fallbackSummary(results, "Summary generation exhausted retry attempts");
    }

    private void sleepWithJitter(int attempt) {
        long exponentialMs = Math.min(BACKOFF_BASE_MS << Math.max(0, attempt - 1), BACKOFF_MAX_MS);
        long jitteredMs = ThreadLocalRandom.current().nextLong(exponentialMs + 1);
        try {
            Thread.sleep(jitteredMs);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
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
        int multiplier = summarySettings.excerptNormalizationMultiplier();
        String content = result.content();
        int prefixLength = Math.min(content.length(), excerptLength * multiplier);
        String normalized = WHITESPACE_PATTERN.matcher(content.substring(0, prefixLength))
            .replaceAll(" ")
            .trim();
        if (normalized.length() <= excerptLength) {
            return normalized;
        }
        return normalized.substring(0, excerptLength) + "...";
    }

    // ------------------------------------------------------------------
    // Final report formatting (from v1 SummaryFinalReportFormatter)
    // ------------------------------------------------------------------

    private String formatFinalReport(String summaryContent, String repository,
                                     List<ReviewResult> results, String date) {
        String reportLinks = buildReportLinks(results, date);
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

    private String buildReportLinks(List<ReviewResult> results, String date) {
        var sb = new StringBuilder();
        for (ReviewResult result : results) {
            String safeName = ReportGenerator.sanitizeAgentName(result.agentConfig().name());
            String filename = "%s_%s.md".formatted(safeName, date);
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
}
