package dev.logicojp.reviewer.report.summary;

import dev.logicojp.reviewer.config.PromptBudgetConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.finding.FindingsExtractor;
import dev.logicojp.reviewer.report.finding.ReviewFindingParser;

import dev.logicojp.reviewer.service.TemplateService;
import dev.logicojp.reviewer.util.PromptContentCompactor;

import java.util.List;
import java.util.Map;

final class SummaryPromptBuilder {

    private final TemplateService templateService;
    private final int maxContentPerAgent;
    private final int maxTotalPromptContent;
    private final int averageResultContentEstimate;
    private final int initialBufferMargin;
    private final PromptBudgetConfig promptBudgetConfig;

    SummaryPromptBuilder(TemplateService templateService, int maxContentPerAgent,
                         int maxTotalPromptContent,
                         int averageResultContentEstimate, int initialBufferMargin) {
        this(templateService, maxContentPerAgent, maxTotalPromptContent,
            averageResultContentEstimate, initialBufferMargin, new PromptBudgetConfig());
    }

    SummaryPromptBuilder(TemplateService templateService, int maxContentPerAgent,
                         int maxTotalPromptContent,
                         int averageResultContentEstimate, int initialBufferMargin,
                         PromptBudgetConfig promptBudgetConfig) {
        this.templateService = templateService;
        this.maxContentPerAgent = maxContentPerAgent;
        this.maxTotalPromptContent = maxTotalPromptContent;
        this.averageResultContentEstimate = averageResultContentEstimate;
        this.initialBufferMargin = initialBufferMargin;
        this.promptBudgetConfig = promptBudgetConfig != null ? promptBudgetConfig : new PromptBudgetConfig();
    }

    String buildSummaryPrompt(List<ReviewResult> results, String repository) {
        if (promptBudgetConfig.compactPrompts()) {
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

        // Pre-load templates once to avoid per-iteration regex/Matcher overhead
        String successTemplate = templateService.loadTemplateContent(
            templateService.getConfig().summary().resultEntry());
        String errorTemplate = templateService.loadTemplateContent(
            templateService.getConfig().summary().resultErrorEntry());

        for (ReviewResult result : results) {
            if (result.success()) {
                int remaining = maxTotalPromptContent - totalContentSize;
                if (remaining <= 0) {
                    break;
                }
                String content = clipContentForSummary(result.content(), remaining);
                totalContentSize += content.length();
                appendSuccessEntry(resultsSection, result, content, successTemplate);
            } else {
                appendErrorEntry(resultsSection, result, errorTemplate);
            }
        }

        var placeholders = Map.of(
            "repository", repository,
            "results", resultsSection.toString(),
            "findingsSummary", deduplicatedFindings(results));
        return templateService.getSummaryUserPrompt(placeholders);
    }

    private String buildCompactSummaryPrompt(List<ReviewResult> results, String repository) {
        var resultsSection = new StringBuilder(Math.min(
            results.size() * promptBudgetConfig.summaryContentPerAgentMaxChars(),
            promptBudgetConfig.summaryTotalMaxChars() + initialBufferMargin
        ));
        int totalContentSize = 0;

        for (ReviewResult result : results) {
            String entry = compactEntry(result);
            int remaining = promptBudgetConfig.summaryTotalMaxChars() - totalContentSize;
            if (remaining <= 0) {
                break;
            }
            String clippedEntry = PromptContentCompactor.compact(entry, remaining);
            totalContentSize += clippedEntry.length();
            resultsSection.append(clippedEntry);
        }

        return templateService.getSummaryUserPrompt(Map.of(
            "repository", repository,
            "results", resultsSection.toString(),
            "findingsSummary", deduplicatedFindings(results)
        ));
    }

    private String deduplicatedFindings(List<ReviewResult> results) {
        String findings = FindingsExtractor.buildFindingsSummary(results);
        return findings.isBlank() ? "指摘事項はありません。" : findings;
    }

    private String compactEntry(ReviewResult result) {
        String displayName = result.agentConfig().displayName();
        if (!result.success()) {
            return "## " + displayName + " (failed)\n\n"
                + (result.errorMessage() != null ? result.errorMessage() : "") + "\n\n";
        }

        List<ReviewFindingParser.FindingBlock> blocks = ReviewFindingParser.extractFindingBlocks(result.content());
        if (blocks.isEmpty()) {
            return compactFallbackEntry(displayName, result.content());
        }

        var sb = new StringBuilder();
        sb.append("## ").append(displayName).append("\n\n");
        for (ReviewFindingParser.FindingBlock block : blocks) {
            appendCompactFindingOrFallback(sb, block);
        }
        return PromptContentCompactor.compact(sb.toString(), promptBudgetConfig.summaryContentPerAgentMaxChars());
    }

    private String compactFallbackEntry(String displayName, String content) {
        String excerpt = PromptContentCompactor.compact(
            content,
            Math.min(promptBudgetConfig.summaryFallbackMaxChars(), promptBudgetConfig.summaryContentPerAgentMaxChars())
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
                promptBudgetConfig.summaryFallbackMaxChars()
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

    private String clipContentForSummary(String content, int remaining) {
        String safeContent = content != null ? content : "";
        int maxAllowed = Math.min(maxContentPerAgent, remaining);
        if (safeContent.length() <= maxAllowed) {
            return safeContent;
        }
        return safeContent.substring(0, maxAllowed) + "\n\n... (truncated for summary)";
    }

    private void appendSuccessEntry(StringBuilder resultsSection, ReviewResult result,
                                     String content, String template) {
        resultsSection.append(templateService.applyPlaceholders(template,
            Map.of(
                "displayName", result.agentConfig().displayName(),
                "content", content
            )));
    }

    private void appendErrorEntry(StringBuilder resultsSection, ReviewResult result, String template) {
        resultsSection.append(templateService.applyPlaceholders(template,
            Map.of(
                "displayName", result.agentConfig().displayName(),
                "errorMessage", result.errorMessage() != null ? result.errorMessage() : ""
            )));
    }
}