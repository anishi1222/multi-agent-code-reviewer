package dev.logicojp.reviewer.application.report;

import dev.logicojp.reviewer.application.port.outbound.GenerateAiSummaryPort;
import dev.logicojp.reviewer.application.port.outbound.LoadTemplatePort;
import dev.logicojp.reviewer.domain.report.FallbackSummaryBuilder;
import dev.logicojp.reviewer.domain.report.FindingsExtractor;
import dev.logicojp.reviewer.domain.report.FindingsSummaryFormatter;
import dev.logicojp.reviewer.domain.report.ReviewFinding;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.report.SummaryFinalReportFormatter;
import dev.logicojp.reviewer.domain.report.SummaryPromptBuilder;

import java.util.List;
import java.util.Optional;

/// Application-layer orchestrator for executive summary generation.
///
/// Loads templates once at construction time via {@link LoadTemplatePort}, then
/// delegates AI generation to {@link GenerateAiSummaryPort} and formatting to
/// the domain collaborators ({@link SummaryPromptBuilder}, {@link FallbackSummaryBuilder},
/// {@link SummaryFinalReportFormatter}).
///
/// Template keys:
/// <ul>
///   <li>{@code "summary/user-prompt"}           — user prompt for the AI model</li>
///   <li>{@code "summary/result-entry"}           — per-agent success entry in the prompt</li>
///   <li>{@code "summary/result-error-entry"}     — per-agent error entry in the prompt</li>
///   <li>{@code "summary/fallback"}               — fallback top-level template</li>
///   <li>{@code "summary/fallback-agent-row"}     — fallback per-agent table row</li>
///   <li>{@code "summary/fallback-agent-success"} — fallback per-agent success section</li>
///   <li>{@code "summary/fallback-agent-failure"} — fallback per-agent failure section</li>
///   <li>{@code "summary/executive"}              — final executive summary template</li>
///   <li>{@code "report/link-entry"}              — per-agent report link line</li>
/// </ul>
///
/// Application layer: imports only {@code application.port.*}, {@code domain.*},
/// {@code shared.*}, {@code java.*} — no {@code infrastructure.*}.
public final class SummaryGenerator {

    private final GenerateAiSummaryPort aiSummary;
    private final SummaryPromptBuilder promptBuilder;
    private final FallbackSummaryBuilder fallbackBuilder;
    final SummaryFinalReportFormatter finalReportFormatter;

    /// Configuration record for summary generation tuning parameters.
    ///
    /// @param maxContentPerAgent          max characters included per agent in the AI prompt
    /// @param maxTotalPromptContent       max total characters across all agents
    /// @param fallbackExcerptLength       excerpt length used in the fallback summary
    /// @param averageResultContentEstimate initial buffer estimate (agents × this)
    /// @param initialBufferMargin         headroom added to initial buffer
    /// @param excerptNormalizationMultiplier multiplier for whitespace-normalised excerpts
    public record SummaryGenerationConfig(
        int maxContentPerAgent,
        int maxTotalPromptContent,
        int fallbackExcerptLength,
        int averageResultContentEstimate,
        int initialBufferMargin,
        int excerptNormalizationMultiplier
    ) {}

    // Template key constants — must match what LoadTemplatePort.loadRaw() serves
    static final String TEMPLATE_SUMMARY_USER_PROMPT    = "summary-prompt.md";
    static final String TEMPLATE_SUMMARY_RESULT_ENTRY   = "summary-result-entry.md";
    static final String TEMPLATE_SUMMARY_RESULT_ERROR   = "summary-result-error-entry.md";
    static final String TEMPLATE_FALLBACK               = "fallback-summary.md";
    static final String TEMPLATE_FALLBACK_AGENT_ROW     = "fallback-agent-row.md";
    static final String TEMPLATE_FALLBACK_AGENT_SUCCESS = "fallback-agent-success.md";
    static final String TEMPLATE_FALLBACK_AGENT_FAILURE = "fallback-agent-failure.md";
    static final String TEMPLATE_EXECUTIVE_SUMMARY      = "executive-summary.md";
    static final String TEMPLATE_REPORT_LINK_ENTRY      = "report-link-entry.md";

    public SummaryGenerator(LoadTemplatePort templates,
                             GenerateAiSummaryPort aiSummary,
                             SummaryGenerationConfig config) {
        this.aiSummary = aiSummary;
        this.promptBuilder = new SummaryPromptBuilder(
            templates.loadRaw(TEMPLATE_SUMMARY_USER_PROMPT),
            templates.loadRaw(TEMPLATE_SUMMARY_RESULT_ENTRY),
            templates.loadRaw(TEMPLATE_SUMMARY_RESULT_ERROR),
            config.maxContentPerAgent(),
            config.maxTotalPromptContent(),
            config.averageResultContentEstimate(),
            config.initialBufferMargin()
        );
        this.fallbackBuilder = new FallbackSummaryBuilder(
            templates.loadRaw(TEMPLATE_FALLBACK),
            templates.loadRaw(TEMPLATE_FALLBACK_AGENT_ROW),
            templates.loadRaw(TEMPLATE_FALLBACK_AGENT_SUCCESS),
            templates.loadRaw(TEMPLATE_FALLBACK_AGENT_FAILURE),
            config.fallbackExcerptLength(),
            config.excerptNormalizationMultiplier()
        );
        this.finalReportFormatter = new SummaryFinalReportFormatter(
            templates.loadRaw(TEMPLATE_EXECUTIVE_SUMMARY),
            templates.loadRaw(TEMPLATE_REPORT_LINK_ENTRY)
        );
    }

    /// Builds the executive summary content (AI-generated or fallback).
    ///
    /// Invokes the AI model via {@link GenerateAiSummaryPort}. Falls back to
    /// {@link FallbackSummaryBuilder} if the AI response is blank.
    ///
    /// @param results    all review results
    /// @param repository the repository that was reviewed
    /// @return the summary prose (never null, never blank — falls back on AI failure)
    public String buildSummaryContent(List<ReviewResult> results, String repository) {
        String prompt = promptBuilder.buildSummaryPrompt(results, repository);
        Optional<String> aiContent = aiSummary.generate(prompt);
        if (aiContent.isPresent() && !aiContent.get().isBlank()) {
            return aiContent.get();
        }
        return fallbackBuilder.buildFallbackSummary(results);
    }

    /// Renders the final executive summary document.
    ///
    /// Pre-computes {@code findingsSummary} from all results and delegates
    /// to {@link SummaryFinalReportFormatter}.
    ///
    /// @param summaryContent AI-generated (or fallback) prose
    /// @param results        all review results
    /// @param repository     the reviewed repository
    /// @param date           formatted date for the report header
    /// @return fully rendered executive summary markdown
    public String formatSummary(String summaryContent,
                                List<ReviewResult> results,
                                String repository,
                                String date) {
        List<ReviewFinding> findings = FindingsExtractor.extractAll(results);
        String findingsSummary = findings.isEmpty()
            ? "指摘事項はありません。"
            : FindingsSummaryFormatter.formatSummary(findings);
        return finalReportFormatter.format(summaryContent, repository, results, date, findingsSummary);
    }
}
