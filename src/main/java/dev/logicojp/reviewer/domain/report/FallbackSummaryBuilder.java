package dev.logicojp.reviewer.domain.report;

import dev.logicojp.reviewer.shared.PlaceholderUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/// Builds a fallback summary when AI summary generation fails or is unavailable.
///
/// All four template strings (summary, agentRow, agentSuccess, agentFailure) are
/// pre-loaded by the application layer via {@code LoadTemplatePort} and passed to
/// the constructor — this class has no I/O.
///
/// Package-private: collaborator of {@link SummaryGenerator} in the application layer.
///
/// Pure: imports only {@code domain.*}, {@code shared.*}, {@code java.*}.
public final class FallbackSummaryBuilder {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /// Top-level summary template; placeholders: {@code tableRows}, {@code agentSummaries}.
    private final String summaryTemplate;
    /// Per-agent table row; placeholders: {@code displayName}, {@code content}.
    private final String agentRowTemplate;
    /// Per-agent success entry; placeholders: {@code displayName}, {@code content}.
    private final String agentSuccessTemplate;
    /// Per-agent failure entry; placeholders: {@code displayName}, {@code errorMessage}.
    private final String agentFailureTemplate;
    private final int excerptLength;
    private final int excerptNormalizationMultiplier;

    public FallbackSummaryBuilder(String summaryTemplate,
                            String agentRowTemplate,
                            String agentSuccessTemplate,
                            String agentFailureTemplate,
                            int excerptLength,
                            int excerptNormalizationMultiplier) {
        this.summaryTemplate = summaryTemplate;
        this.agentRowTemplate = agentRowTemplate;
        this.agentSuccessTemplate = agentSuccessTemplate;
        this.agentFailureTemplate = agentFailureTemplate;
        this.excerptLength = excerptLength;
        this.excerptNormalizationMultiplier = excerptNormalizationMultiplier;
    }

    public String buildFallbackSummary(List<ReviewResult> results) {
        String tableRows = buildTableRows(results);
        String agentSummaries = buildAgentSummaries(results);
        return PlaceholderUtils.replaceDollarPlaceholders(summaryTemplate,
            Map.of("tableRows", tableRows, "agentSummaries", agentSummaries));
    }

    private String buildTableRows(List<ReviewResult> results) {
        var sb = new StringBuilder();
        for (ReviewResult result : results) {
            sb.append(PlaceholderUtils.replaceDollarPlaceholders(agentRowTemplate,
                Map.of(
                    "displayName", result.agentConfig().displayName(),
                    "content", excerpt(result)
                )));
        }
        return sb.toString();
    }

    private String buildAgentSummaries(List<ReviewResult> results) {
        var sb = new StringBuilder();
        for (ReviewResult result : results) {
            if (result.success()) {
                sb.append(PlaceholderUtils.replaceDollarPlaceholders(agentSuccessTemplate,
                    Map.of(
                        "displayName", result.agentConfig().displayName(),
                        "content", excerpt(result)
                    )));
            } else {
                sb.append(PlaceholderUtils.replaceDollarPlaceholders(agentFailureTemplate,
                    Map.of(
                        "displayName", result.agentConfig().displayName(),
                        "errorMessage", errorMessageOrEmpty(result)
                    )));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String errorMessageOrEmpty(ReviewResult result) {
        return result.errorMessage() != null ? result.errorMessage() : "";
    }

    private String excerpt(ReviewResult result) {
        if (result == null || !result.success()
                || result.content() == null || result.content().isBlank()) {
            return "N/A";
        }
        String content = result.content();
        int prefixLength = Math.min(content.length(), excerptLength * excerptNormalizationMultiplier);
        String normalizedPrefix = WHITESPACE_PATTERN
            .matcher(content.substring(0, prefixLength))
            .replaceAll(" ")
            .trim();
        if (normalizedPrefix.length() <= excerptLength) {
            return normalizedPrefix;
        }
        return normalizedPrefix.substring(0, excerptLength) + "...";
    }
}
