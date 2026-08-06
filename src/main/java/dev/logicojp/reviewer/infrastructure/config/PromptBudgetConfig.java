package dev.logicojp.reviewer.infrastructure.config;

import dev.logicojp.reviewer.shared.PromptBudget;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/// Micronaut binding for `reviewer.prompt-budget`.
///
/// Budgets are character based to avoid adding a tokenizer dependency. They are
/// applied only when compact prompt mode is enabled.
///
/// This type exists purely as the framework-facing adapter. The values are consumed by
/// `domain` prompt builders, which per ADR-0006 Rule 1 may not see Micronaut types, so
/// this record maps onto the pure [PromptBudget] carrier via [#toPromptBudget()].
///
/// Ported from `origin/main` commit `38dcbc8` (`config.PromptBudgetConfig`).
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

    /// All-defaults configuration, with compaction disabled.
    public PromptBudgetConfig() {
        this(
            PromptBudget.DEFAULT_COMPACT_PROMPTS,
            PromptBudget.DEFAULT_PEER_CONTENT_MAX_CHARS,
            PromptBudget.DEFAULT_SYNTHESIS_TURN_MAX_CHARS,
            PromptBudget.DEFAULT_SYNTHESIS_HISTORY_MAX_CHARS,
            PromptBudget.DEFAULT_LOCAL_SOURCE_MAX_CHARS,
            PromptBudget.DEFAULT_SUMMARY_CONTENT_PER_AGENT_MAX_CHARS,
            PromptBudget.DEFAULT_SUMMARY_TOTAL_MAX_CHARS,
            PromptBudget.DEFAULT_SUMMARY_FALLBACK_MAX_CHARS
        );
    }

    /// Converts to the pure carrier consumed by `domain`.
    /// Budget normalisation (non-positive falls back to default) happens in
    /// [PromptBudget]'s compact constructor, so it applies to bound values too.
    public PromptBudget toPromptBudget() {
        return new PromptBudget(
            compactPrompts,
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
