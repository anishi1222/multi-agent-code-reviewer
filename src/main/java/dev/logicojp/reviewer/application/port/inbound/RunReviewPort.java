package dev.logicojp.reviewer.application.port.inbound;

import dev.logicojp.reviewer.domain.report.ReviewResult;

/// Inbound port: run a code review.
///
/// Implementer: {@code application.review.ReviewOrchestrator}
/// Callers:     {@code presentation.command.ReviewCommand}
///
/// Covers behaviors: ORC-01–ORC-10, RTY-01–RTY-04
public interface RunReviewPort {

    /// Execute a full review with the given request.
    ///
    /// @param request the review parameters
    /// @return the consolidated review result
    ReviewResult execute(ReviewRequest request);
}
