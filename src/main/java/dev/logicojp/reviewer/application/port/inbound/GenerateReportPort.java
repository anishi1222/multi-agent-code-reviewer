package dev.logicojp.reviewer.application.port.inbound;

import dev.logicojp.reviewer.domain.report.ReviewResult;

import java.util.List;
import java.util.Optional;

/// Inbound port: generate reports from review results.
///
/// Implementer: {@code application.report.GenerateReportUseCase}
/// Callers:     {@code presentation.command.ReviewCommand} (post-review)
///
/// Covers behaviors: OUT-01–OUT-09
public interface GenerateReportPort {

    /// Generate reports for the given review results.
    ///
    /// @param results the review results to report on
    /// @param options report generation options
    /// @return the generated report output (files written, summary)
    ReportOutput generate(List<ReviewResult> results, ReportOptions options);

    /// Generate an executive summary only (no full report files written).
    ///
    /// @param results the review results to summarise
    /// @param options report options (used for format and AI summary settings)
    /// @return the AI-generated summary text, or empty if summary generation failed/skipped
    Optional<String> generateSummary(List<ReviewResult> results, ReportOptions options);
}
