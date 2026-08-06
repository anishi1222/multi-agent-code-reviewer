package dev.logicojp.reviewer.domain.report;

import dev.logicojp.reviewer.shared.PlaceholderUtils;
import dev.logicojp.reviewer.shared.PromptBudget;
import dev.logicojp.reviewer.shared.PromptContentCompactor;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Builds the summary user-prompt sent to the AI model for executive summary generation.
///
/// All three template strings (userPrompt, successEntry, errorEntry) are pre-loaded by
/// the application layer via {@code LoadTemplatePort} and passed to the constructor —
/// this class has no I/O.
///
/// Package-private: collaborator of {@link SummaryGenerator} in the application layer.
///
/// Pure: imports only {@code domain.*}, {@code shared.*}, {@code java.*}.
public final class SummaryPromptBuilder {

    /// Matches the "Good Points" section emitted by agent templates (ported from `25c4b49`).
    private static final Pattern GOOD_POINTS_SECTION = Pattern.compile(
        "(?ms)^#{2,3}\\s+Good Points\\s*$\\R(.*?)"
            + "(?=^#{2,3}\\s+(?:改善点|Improvements?)\\s*$|^###\\s+\\[?\\d+|\\z)"
    );

    /// Top-level user-prompt template; placeholders: {@code repository}, {@code results}.
    private final String userPromptTemplate;
    /// Per-agent success entry; placeholders: {@code displayName}, {@code content}.
    private final String successEntryTemplate;
    /// Per-agent error entry; placeholders: {@code displayName}, {@code errorMessage}.
    private final String errorEntryTemplate;

    private final int maxContentPerAgent;
    private final int maxTotalPromptContent;
    private final int averageResultContentEstimate;
    private final int initialBufferMargin;
    private final PromptBudget promptBudget;

    public SummaryPromptBuilder(String userPromptTemplate,
                          String successEntryTemplate,
                          String errorEntryTemplate,
                          int maxContentPerAgent,
                          int maxTotalPromptContent,
                          int averageResultContentEstimate,
                          int initialBufferMargin) {
        this(userPromptTemplate, successEntryTemplate, errorEntryTemplate, maxContentPerAgent,
            maxTotalPromptContent, averageResultContentEstimate, initialBufferMargin, new PromptBudget());
    }

    public SummaryPromptBuilder(String userPromptTemplate,
                          String successEntryTemplate,
                          String errorEntryTemplate,
                          int maxContentPerAgent,
                          int maxTotalPromptContent,
                          int averageResultContentEstimate,
                          int initialBufferMargin,
                          PromptBudget promptBudget) {
        this.userPromptTemplate = userPromptTemplate;
        this.successEntryTemplate = successEntryTemplate;
        this.errorEntryTemplate = errorEntryTemplate;
        this.maxContentPerAgent = maxContentPerAgent;
        this.maxTotalPromptContent = maxTotalPromptContent;
        this.averageResultContentEstimate = averageResultContentEstimate;
        this.initialBufferMargin = initialBufferMargin;
        this.promptBudget = promptBudget != null ? promptBudget : new PromptBudget();
    }

    public String buildSummaryPrompt(List<ReviewResult> results, String repository) {
        if (promptBudget.compactPrompts()) {
            return buildCompactSummaryPrompt(results, repository);
        }
        return buildLegacySummaryPrompt(results, repository);
    }

    private String buildLegacySummaryPrompt(List<ReviewResult> results, String repository) {
        var resultsSection = new StringBuilder(
            Math.min(
                results.size() * averageResultContentEstimate,
                maxTotalPromptContent + initialBufferMargin
            )
        );
        int totalContentSize = 0;

        for (ReviewResult result : results) {
            if (result.success()) {
                int remaining = maxTotalPromptContent - totalContentSize;
                if (remaining <= 0) {
                    break;
                }
                String content = clipContent(result.content(), remaining);
                totalContentSize += content.length();
                resultsSection.append(PlaceholderUtils.replaceDollarPlaceholders(successEntryTemplate,
                    Map.of(
                        "displayName", result.agentConfig().displayName(),
                        "content", content
                    )));
            } else {
                resultsSection.append(PlaceholderUtils.replaceDollarPlaceholders(errorEntryTemplate,
                    Map.of(
                        "displayName", result.agentConfig().displayName(),
                        "errorMessage", result.errorMessage() != null ? result.errorMessage() : ""
                    )));
            }
        }

        return PlaceholderUtils.replaceDollarPlaceholders(userPromptTemplate,
            Map.of(
                "repository", repository != null ? repository : "",
                "results", resultsSection.toString(),
                "findingsSummary", deduplicatedFindings(results)
            ));
    }

    // ----------------------------------------------------------------------------------
    // Compact path — ported from origin/main `38dcbc8` (report.summary.SummaryPromptBuilder).
    // Adapted: TemplateService -> pre-loaded template strings + PlaceholderUtils,
    //          PromptBudgetConfig (Micronaut) -> PromptBudget (pure, ADR-0006 Rule 1).
    // ----------------------------------------------------------------------------------

    private String buildCompactSummaryPrompt(List<ReviewResult> results, String repository) {
        var resultsSection = new StringBuilder(Math.min(
            results.size() * promptBudget.summaryContentPerAgentMaxChars(),
            promptBudget.summaryTotalMaxChars() + initialBufferMargin
        ));
        int totalContentSize = 0;

        for (ReviewResult result : results) {
            String entry = compactEntry(result);
            int remaining = promptBudget.summaryTotalMaxChars() - totalContentSize;
            if (remaining <= 0) {
                break;
            }
            String clippedEntry = PromptContentCompactor.compact(entry, remaining);
            totalContentSize += clippedEntry.length();
            resultsSection.append(clippedEntry);
        }

        return PlaceholderUtils.replaceDollarPlaceholders(userPromptTemplate,
            Map.of(
                "repository", repository != null ? repository : "",
                "results", resultsSection.toString(),
                "findingsSummary", deduplicatedFindings(results)
            ));
    }

    /// Layered equivalent of main's `FindingsExtractor.buildFindingsSummary(results)`,
    /// which fused extraction and formatting. This tree keeps them separate.
    private String deduplicatedFindings(List<ReviewResult> results) {
        String findings = FindingsSummaryFormatter.formatSummary(FindingsExtractor.extractAll(results));
        return findings.isBlank() ? "指摘事項はありません。" : findings;
    }

    private String compactEntry(ReviewResult result) {
        String displayName = result.agentConfig().displayName();
        if (!result.success()) {
            return "## " + displayName + " (failed)\n\n"
                + (result.errorMessage() != null ? result.errorMessage() : "") + "\n\n";
        }

        List<ReviewFindingParser.FindingBlock> blocks =
            ReviewFindingParser.extractFindingBlocks(result.content());
        if (blocks.isEmpty()) {
            return compactFallbackEntry(displayName, result.content());
        }

        var sb = new StringBuilder();
        sb.append("## ").append(displayName).append("\n\n");
        appendCompactGoodPoints(sb, result.content());
        for (ReviewFindingParser.FindingBlock block : blocks) {
            appendCompactFindingOrFallback(sb, block);
        }
        return PromptContentCompactor.compact(sb.toString(), promptBudget.summaryContentPerAgentMaxChars());
    }

    private void appendCompactGoodPoints(StringBuilder entry, String content) {
        Matcher matcher = GOOD_POINTS_SECTION.matcher(content != null ? content : "");
        if (!matcher.find()) {
            return;
        }
        String goodPoints = matcher.group(1).trim();
        if (goodPoints.isBlank()) {
            return;
        }

        int goodPointsBudget = Math.min(
            promptBudget.summaryFallbackMaxChars(),
            Math.max(1, promptBudget.summaryContentPerAgentMaxChars() / 3)
        );
        entry.append("### Good Points\n\n")
            .append(PromptContentCompactor.compact(goodPoints, goodPointsBudget))
            .append("\n\n");
    }

    private String compactFallbackEntry(String displayName, String content) {
        String excerpt = PromptContentCompactor.compact(
            content,
            Math.min(promptBudget.summaryFallbackMaxChars(), promptBudget.summaryContentPerAgentMaxChars())
        );
        return "## " + displayName + "\n\n" + excerpt + "\n\n";
    }

    private void appendCompactFindingOrFallback(StringBuilder sb, ReviewFindingParser.FindingBlock block) {
        var finding = new StringBuilder();
        finding.append("### ").append(block.title()).append("\n");
        boolean structured = false;
        structured |= appendTableValue(finding, block, "Priority", "Priority");
        structured |= appendTableValue(finding, block, "指摘の概要", "Summary");
        structured |= appendTableValue(finding, block, "該当箇所", "Location");
        structured |= appendTableValue(finding, block, "修正しない場合の影響", "Impact");
        structured |= appendSectionExcerpt(finding, block.body(), "**推奨対応**", "Recommendation");
        if (!structured) {
            finding.append(PromptContentCompactor.compact(
                block.body(),
                promptBudget.summaryFallbackMaxChars()
            )).append("\n");
        }
        sb.append(finding).append("\n");
    }

    private static boolean appendTableValue(StringBuilder sb,
                                            ReviewFindingParser.FindingBlock block,
                                            String key,
                                            String label) {
        String value = ReviewFindingParser.extractTableValue(block.body(), key);
        if (value.isBlank()) {
            return false;
        }
        sb.append("- ").append(label).append(": ").append(value).append("\n");
        return true;
    }

    private static boolean appendSectionExcerpt(StringBuilder sb, String body, String heading, String label) {
        int start = body.indexOf(heading);
        if (start < 0) {
            return false;
        }
        int contentStart = start + heading.length();
        int nextHeading = body.indexOf("**効果**", contentStart);
        int end = nextHeading >= 0 ? nextHeading : body.length();
        String excerpt = body.substring(contentStart, end).trim();
        if (excerpt.isBlank()) {
            return false;
        }
        sb.append("- ").append(label).append(": ")
            .append(PromptContentCompactor.compact(excerpt, 800).replace('\n', ' '))
            .append("\n");
        return true;
    }

    private String clipContent(String content, int remaining) {
        String safe = content != null ? content : "";
        int maxAllowed = Math.min(maxContentPerAgent, remaining);
        if (safe.length() <= maxAllowed) {
            return safe;
        }
        return safe.substring(0, maxAllowed) + "\n\n... (truncated for summary)";
    }
}
