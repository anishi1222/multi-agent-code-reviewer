package dev.logicojp.reviewer.shared;

/// Character budgets governing prompt compaction.
///
/// Budgets are character based to avoid adding a tokenizer dependency. They are
/// applied only when [#compactPrompts()] is enabled.
///
/// This is the **pure** carrier of the budget values. It is read by `domain` prompt
/// builders, so per ADR-0006 Rule 1 (domain purity) it must not carry framework
/// annotations. The Micronaut-bound counterpart lives at
/// `infrastructure.config.PromptBudgetConfig`, which maps onto this type via
/// `toPromptBudget()`.
///
/// Origin: ported from `origin/main` commit `38dcbc8` (`config.PromptBudgetConfig`),
/// which combined binding and value-carrying in a single Micronaut record. That shape
/// is not expressible in the layered tree because `domain` may depend only on the JDK,
/// `domain` and `shared`.
public record PromptBudget(
    boolean compactPrompts,
    int peerContentMaxChars,
    int synthesisTurnMaxChars,
    int synthesisHistoryMaxChars,
    int localSourceMaxChars,
    int summaryContentPerAgentMaxChars,
    int summaryTotalMaxChars,
    int summaryFallbackMaxChars
) {

    public static final boolean DEFAULT_COMPACT_PROMPTS = false;
    public static final int DEFAULT_PEER_CONTENT_MAX_CHARS = 12_000;
    public static final int DEFAULT_SYNTHESIS_TURN_MAX_CHARS = 6_000;
    public static final int DEFAULT_SYNTHESIS_HISTORY_MAX_CHARS = 50_000;
    public static final int DEFAULT_LOCAL_SOURCE_MAX_CHARS = 1_048_576;
    public static final int DEFAULT_SUMMARY_CONTENT_PER_AGENT_MAX_CHARS = 12_000;
    public static final int DEFAULT_SUMMARY_TOTAL_MAX_CHARS = 60_000;
    public static final int DEFAULT_SUMMARY_FALLBACK_MAX_CHARS = 2_000;

    /// Normalises every budget: a non-positive value falls back to its default.
    /// Mirrors the compact-constructor behaviour of main's `PromptBudgetConfig`.
    public PromptBudget {
        peerContentMaxChars = ConfigDefaults.defaultIfNonPositive(
            peerContentMaxChars, DEFAULT_PEER_CONTENT_MAX_CHARS);
        synthesisTurnMaxChars = ConfigDefaults.defaultIfNonPositive(
            synthesisTurnMaxChars, DEFAULT_SYNTHESIS_TURN_MAX_CHARS);
        synthesisHistoryMaxChars = ConfigDefaults.defaultIfNonPositive(
            synthesisHistoryMaxChars, DEFAULT_SYNTHESIS_HISTORY_MAX_CHARS);
        localSourceMaxChars = ConfigDefaults.defaultIfNonPositive(
            localSourceMaxChars, DEFAULT_LOCAL_SOURCE_MAX_CHARS);
        summaryContentPerAgentMaxChars = ConfigDefaults.defaultIfNonPositive(
            summaryContentPerAgentMaxChars, DEFAULT_SUMMARY_CONTENT_PER_AGENT_MAX_CHARS);
        summaryTotalMaxChars = ConfigDefaults.defaultIfNonPositive(
            summaryTotalMaxChars, DEFAULT_SUMMARY_TOTAL_MAX_CHARS);
        summaryFallbackMaxChars = ConfigDefaults.defaultIfNonPositive(
            summaryFallbackMaxChars, DEFAULT_SUMMARY_FALLBACK_MAX_CHARS);
    }

    /// All-defaults budget, with compaction disabled.
    public PromptBudget() {
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

    public PromptBudget withCompactPrompts(boolean enabled) {
        return new PromptBudget(
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
