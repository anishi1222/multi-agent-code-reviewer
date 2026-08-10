package dev.logicojp.reviewer.infrastructure.config;

import dev.logicojp.reviewer.shared.PromptBudget;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

/// Micronaut binding for `reviewer.prompt-budget`.
///
/// Budgets are character based to avoid adding a tokenizer dependency. They are
/// applied only when compact prompt mode is enabled.
///
/// This type exists purely as the framework-facing adapter. The values are consumed by
/// `domain` prompt builders, which per ADR-0006 Rule 1 may not see Micronaut types, so
/// this record maps onto the pure [PromptBudget] carrier via [#toPromptBudget()].
///
/// ## Why the components are boxed and `@Nullable`
///
/// [PromptBudget] already owns the canonical default for every budget. Restating those
/// numbers here — as `@Bindable(defaultValue = "...")` string literals did until t27 —
/// created a second source of truth that could drift from the first without any test
/// noticing.
///
/// The obvious removal (drop `@Bindable`, keep `int`) is **not** available: Micronaut
/// 5.1.2 cannot construct a `@ConfigurationProperties` record when a primitive component
/// has no bound value, and fails with `DependencyInjectionException: Property doesn't
/// exist` before any constructor body runs. Boxing the components and marking them
/// `@Nullable` makes an absent key bind as `null` instead, which the compact constructor
/// below normalises to the [PromptBudget] default. Defaults are therefore declared
/// exactly once, in [PromptBudget].
///
/// Ported from `origin/main` commit `38dcbc8` (`config.PromptBudgetConfig`).
@ConfigurationProperties("reviewer.prompt-budget")
public record PromptBudgetConfig(
    @Nullable Boolean compactPrompts,
    @Nullable Integer peerContentMaxChars,
    @Nullable Integer synthesisTurnMaxChars,
    @Nullable Integer synthesisHistoryMaxChars,
    @Nullable Integer localSourceMaxChars,
    @Nullable Integer summaryContentPerAgentMaxChars,
    @Nullable Integer summaryTotalMaxChars,
    @Nullable Integer summaryFallbackMaxChars
) {

    /// Substitutes the [PromptBudget] default for any key absent from configuration, so
    /// accessors never return `null` and the defaults have a single owner.
    public PromptBudgetConfig {
        compactPrompts = compactPrompts != null
            ? compactPrompts : PromptBudget.DEFAULT_COMPACT_PROMPTS;
        peerContentMaxChars = peerContentMaxChars != null
            ? peerContentMaxChars : PromptBudget.DEFAULT_PEER_CONTENT_MAX_CHARS;
        synthesisTurnMaxChars = synthesisTurnMaxChars != null
            ? synthesisTurnMaxChars : PromptBudget.DEFAULT_SYNTHESIS_TURN_MAX_CHARS;
        synthesisHistoryMaxChars = synthesisHistoryMaxChars != null
            ? synthesisHistoryMaxChars : PromptBudget.DEFAULT_SYNTHESIS_HISTORY_MAX_CHARS;
        localSourceMaxChars = localSourceMaxChars != null
            ? localSourceMaxChars : PromptBudget.DEFAULT_LOCAL_SOURCE_MAX_CHARS;
        summaryContentPerAgentMaxChars = summaryContentPerAgentMaxChars != null
            ? summaryContentPerAgentMaxChars : PromptBudget.DEFAULT_SUMMARY_CONTENT_PER_AGENT_MAX_CHARS;
        summaryTotalMaxChars = summaryTotalMaxChars != null
            ? summaryTotalMaxChars : PromptBudget.DEFAULT_SUMMARY_TOTAL_MAX_CHARS;
        summaryFallbackMaxChars = summaryFallbackMaxChars != null
            ? summaryFallbackMaxChars : PromptBudget.DEFAULT_SUMMARY_FALLBACK_MAX_CHARS;
    }

    /// All-defaults configuration, with compaction disabled.
    /// Every component is left unset so the compact constructor above supplies the
    /// [PromptBudget] default — this constructor states no default of its own.
    public PromptBudgetConfig() {
        this(null, null, null, null, null, null, null, null);
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
