package dev.logicojp.reviewer.application.port.inbound;

/// Inbound port: describe the execution plan that {@link RunReviewPort#execute} will follow,
/// before any review is started.
///
/// Implementer: {@code application.review.DescribeReviewPlanUseCase}
/// Callers:     {@code presentation.ReviewPreparationService}
///
/// ## Why a port for one value
///
/// ADR-0006 D1 forbids `presentation` from importing `infrastructure`, but says nothing about
/// binding an infrastructure configuration key by **string** — which is the same coupling with
/// none of the compile-time safety. t24/F3 is what that gap costs: the startup banner and the
/// executor read two different keys for the same setting and silently disagreed.
///
/// This port is the seam that closes it. Presentation asks *what will happen*; the application
/// answers with a {@link ReviewPlan}. The configuration key stays where it belongs, in
/// `infrastructure.config`, and the composition root binds it once.
public interface DescribeReviewPlanPort {

    /// Describes the plan the next review run will follow.
    ///
    /// The returned values are the **effective** ones — already defaulted and normalised by the
    /// configuration source — so a caller may display them verbatim.
    ///
    /// @return the execution plan; never null
    ReviewPlan describePlan();
}
