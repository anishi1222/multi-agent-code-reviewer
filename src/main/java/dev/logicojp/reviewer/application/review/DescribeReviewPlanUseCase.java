package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.inbound.DescribeReviewPlanPort;
import dev.logicojp.reviewer.application.port.inbound.ReviewPlan;

import java.util.Objects;
import java.util.function.IntSupplier;

/// Application use-case: describe the execution plan a review run will follow.
///
/// Implements {@link DescribeReviewPlanPort}. Holds no configuration of its own — the pass count
/// arrives as an {@link IntSupplier} bound by the composition root.
///
/// ## Why a supplier rather than an `int`
///
/// The point of t28 is provenance, not the number. Taking a supplier lets the composition root
/// bind the **method reference of the same accessor the executor resolves**
/// (`ExecutionConfig::reviewPasses`), so the wiring itself reads as "one source". Snapshotting an
/// `int` at wiring time would work identically at runtime but would put a second, independent
/// read of the configuration in the factory — the exact shape that produced t24/F3.
///
/// No framework annotations — DI is handled by the composition root.
///
/// Application layer: imports only {@code application.port.*}, {@code java.*}.
public final class DescribeReviewPlanUseCase implements DescribeReviewPlanPort {

    private final IntSupplier effectiveReviewPasses;

    /// @param effectiveReviewPasses supplies the already-normalised per-agent pass count that the
    ///                              executor will use; must never return a value below 1
    public DescribeReviewPlanUseCase(IntSupplier effectiveReviewPasses) {
        this.effectiveReviewPasses =
            Objects.requireNonNull(effectiveReviewPasses, "effectiveReviewPasses must not be null");
    }

    /// {@inheritDoc}
    ///
    /// Reads the supplier on every call rather than caching, so the plan can never report a value
    /// the configuration no longer holds.
    @Override
    public ReviewPlan describePlan() {
        return new ReviewPlan(effectiveReviewPasses.getAsInt());
    }
}
