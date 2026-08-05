package dev.logicojp.reviewer.domain.report;

import dev.logicojp.reviewer.shared.PlaceholderUtils;

import java.util.List;
import java.util.Map;

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

    public SummaryPromptBuilder(String userPromptTemplate,
                          String successEntryTemplate,
                          String errorEntryTemplate,
                          int maxContentPerAgent,
                          int maxTotalPromptContent,
                          int averageResultContentEstimate,
                          int initialBufferMargin) {
        this.userPromptTemplate = userPromptTemplate;
        this.successEntryTemplate = successEntryTemplate;
        this.errorEntryTemplate = errorEntryTemplate;
        this.maxContentPerAgent = maxContentPerAgent;
        this.maxTotalPromptContent = maxTotalPromptContent;
        this.averageResultContentEstimate = averageResultContentEstimate;
        this.initialBufferMargin = initialBufferMargin;
    }

    public String buildSummaryPrompt(List<ReviewResult> results, String repository) {
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
                "results", resultsSection.toString()
            ));
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
