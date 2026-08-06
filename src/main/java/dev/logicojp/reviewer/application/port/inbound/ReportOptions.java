package dev.logicojp.reviewer.application.port.inbound;

import java.nio.file.Path;
import java.util.Objects;

/// Options controlling report generation.
///
/// @param outputDir  directory where reports are written
/// @param format     report format identifier (e.g. "markdown", "json")
/// @param skipSummary whether to skip AI-powered executive summary generation
public record ReportOptions(
    Path outputDir,
    String format,
    boolean skipSummary
) {

    public ReportOptions {
        Objects.requireNonNull(outputDir, "outputDir must not be null");
        format = format != null && !format.isBlank() ? format : "markdown";
    }

    public static ReportOptions defaults(Path outputDir) {
        return new ReportOptions(outputDir, "markdown", false);
    }
}
