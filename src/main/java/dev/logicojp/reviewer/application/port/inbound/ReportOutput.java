package dev.logicojp.reviewer.application.port.inbound;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// Represents the output produced by a report generation operation.
///
/// @param reportPaths  paths to all written report files
/// @param summaryText  optional executive summary text (empty string if not generated)
public record ReportOutput(
    List<Path> reportPaths,
    String summaryText
) {

    public ReportOutput {
        Objects.requireNonNull(reportPaths, "reportPaths must not be null");
        reportPaths = List.copyOf(reportPaths);
        summaryText = summaryText != null ? summaryText : "";
    }

    /// Creates an output with reports but no summary.
    public static ReportOutput of(List<Path> reportPaths) {
        return new ReportOutput(reportPaths, "");
    }

    /// Returns {@code true} if an AI summary was generated.
    public boolean hasSummary() {
        return !summaryText.isBlank();
    }
}
