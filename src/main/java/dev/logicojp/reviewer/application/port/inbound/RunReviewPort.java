package dev.logicojp.reviewer.application.port.inbound;

import dev.logicojp.reviewer.domain.report.ReviewResult;

import java.util.List;

/// Inbound port: run a code review.
///
/// Implementer: {@code application.review.ReviewOrchestrator}
/// Callers:     {@code presentation.command.ReviewCommand}
///
/// Covers behaviors: ORC-01–ORC-10, RTY-01–RTY-04
public interface RunReviewPort {

    /// Execute a full review with the given request.
    ///
    /// Returns one {@link ReviewResult} per agent per pass, preserving all individual
    /// results so that {@code GenerateReportPort} can write per-agent files (OUT-02) and
    /// per-pass files (OUT-03).  Results carry {@code passNumber == 0} for single-pass
    /// runs and {@code passNumber ≥ 1} for multi-pass runs.
    ///
    /// @param request the review parameters
    /// @return unmerged per-agent (and per-pass) review results
    List<ReviewResult> execute(ReviewRequest request);
}
