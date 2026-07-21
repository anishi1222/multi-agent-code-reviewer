package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/// Configuration for prompt compaction budgets.
///
/// Budgets are character based to avoid adding a tokenizer dependency. They are
/// applied only when compact prompt mode is enabled.
@ConfigurationProperties("reviewer.prompt-budget")
public record PromptBudgetConfig(
    @Bindable(defaultValue = "false") boolean compactPrompts,
    @Bindable(defaultValue = "12000") int peerContentMaxChars,
    @Bindable(defaultValue = "6000") int synthesisTurnMaxChars,
    @Bindable(defaultValue = "50000") int synthesisHistoryMaxChars,
    @Bindable(defaultValue = "1048576") int localSourceMaxChars,
    @Bindable(defaultValue = "12000") int summaryContentPerAgentMaxChars,
    @Bindable(defaultValue = "60000") int summaryTotalMaxChars,
    @Bindable(defaultValue = "2000") int summaryFallbackMaxChars
) {

    public static final boolean DEFAULT_COMPACT_PROMPTS = false;
    public static final int DEFAULT_PEER_CONTENT_MAX_CHARS = 12_000;
    public static final int DEFAULT_SYNTHESIS_TURN_MAX_CHARS = 6_000;
    public static final int DEFAULT_SYNTHESIS_HISTORY_MAX_CHARS = 50_000;
    public static final int DEFAULT_LOCAL_SOURCE_MAX_CHARS = 1_048_576;
    public static final int DEFAULT_SUMMARY_CONTENT_PER_AGENT_MAX_CHARS = 12_000;
    public static final int DEFAULT_SUMMARY_TOTAL_MAX_CHARS = 60_000;
    public static final int DEFAULT_SUMMARY_FALLBACK_MAX_CHARS = 2_000;

    public PromptBudgetConfig {
        peerContentMaxChars = ConfigDefaults.defaultIfNonPositive(
            peerContentMaxChars,
            DEFAULT_PEER_CONTENT_MAX_CHARS
        );
        synthesisTurnMaxChars = ConfigDefaults.defaultIfNonPositive(
            synthesisTurnMaxChars,
            DEFAULT_SYNTHESIS_TURN_MAX_CHARS
        );
        synthesisHistoryMaxChars = ConfigDefaults.defaultIfNonPositive(
            synthesisHistoryMaxChars,
            DEFAULT_SYNTHESIS_HISTORY_MAX_CHARS
        );
        localSourceMaxChars = ConfigDefaults.defaultIfNonPositive(
            localSourceMaxChars,
            DEFAULT_LOCAL_SOURCE_MAX_CHARS
        );
        summaryContentPerAgentMaxChars = ConfigDefaults.defaultIfNonPositive(
            summaryContentPerAgentMaxChars,
            DEFAULT_SUMMARY_CONTENT_PER_AGENT_MAX_CHARS
        );
        summaryTotalMaxChars = ConfigDefaults.defaultIfNonPositive(
            summaryTotalMaxChars,
            DEFAULT_SUMMARY_TOTAL_MAX_CHARS
        );
        summaryFallbackMaxChars = ConfigDefaults.defaultIfNonPositive(
            summaryFallbackMaxChars,
            DEFAULT_SUMMARY_FALLBACK_MAX_CHARS
        );
    }

    public PromptBudgetConfig() {
        this(
            DEFAULT_COMPACT_PROMPTS,
            DEFAULT_PEER_CONTENT_MAX_CHARS,
            DEFAULT_SYNTHESIS_TURN_MAX_CHARS,
            DEFAULT_SYNTHESIS_HISTORY_MAX_CHARS,
            DEFAULT_LOCAL_SOURCE_MAX_CHARS,
            DEFAULT_SUMMARY_CONTENT_PER_AGENT_MAX_CHARS,
            DEFAULT_SUMMARY_TOTAL_MAX_CHARS,
            DEFAULT_SUMMARY_FALLBACK_MAX_CHARS
        );
    }

    public PromptBudgetConfig withCompactPrompts(boolean enabled) {
        return new PromptBudgetConfig(
            enabled,
            peerContentMaxChars,
            synthesisTurnMaxChars,
            synthesisHistoryMaxChars,
            localSourceMaxChars,
            summaryContentPerAgentMaxChars,
            summaryTotalMaxChars,
            summaryFallbackMaxChars
        );
    }
}
