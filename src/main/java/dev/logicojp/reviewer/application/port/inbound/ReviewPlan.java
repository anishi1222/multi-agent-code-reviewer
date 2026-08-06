package dev.logicojp.reviewer.application.port.inbound;

/// Inbound port DTO: the execution plan that a review run will follow.
///
/// Carries only values that a caller may legitimately need **before** execution starts —
/// today that is the per-agent pass count, which the startup banner reports.
///
/// ## Why this exists
///
/// Before t28, `presentation.formatter.ReviewOutputFormatter` obtained the pass count by binding
/// `@Value("${reviewer.execution.review-passes:1}")` — a configuration key that **nothing else
/// read**. The executor resolved `reviewer.execution.concurrency.review-passes` instead, so the
/// two could disagree in both directions: setting the real key ran N passes while the banner
/// reported 1, and setting the formatter's key printed N while a single pass ran (t24/F3).
///
/// The defect was structural, not a typo. A presentation class that names an infrastructure
/// configuration key by string has no compile-time or test-time link to the value the executor
/// uses, so drift is invisible. Routing the value through this port restores that link: the
/// banner can only display what the plan says, and the plan is derived from the same accessor
/// the executor resolves.
///
/// @param reviewPasses number of review passes each agent will run; always at least 1
public record ReviewPlan(int reviewPasses) {

    /// Rejects a non-positive pass count rather than normalising it.
    ///
    /// Normalisation is already the responsibility of the configuration record that owns the
    /// value — ADR-0006 D6 gives cross-layer defaults a single owner. If a zero or negative
    /// count ever reaches this constructor, that owner has stopped normalising; silently
    /// substituting 1 here would hide the regression and re-create the class of defect this
    /// port was introduced to remove.
    public ReviewPlan {
        if (reviewPasses < 1) {
            throw new IllegalArgumentException(
                "reviewPasses must be at least 1, was " + reviewPasses
                    + " — the configuration source stopped normalising non-positive values");
        }
    }

    /// Whether the plan runs more than one pass per agent.
    ///
    /// A fact about the plan, not a display rule: callers decide what to do with it.
    public boolean isMultiPass() {
        return reviewPasses > 1;
    }
}
