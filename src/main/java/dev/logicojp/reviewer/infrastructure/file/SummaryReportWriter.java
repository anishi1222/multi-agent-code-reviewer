package dev.logicojp.reviewer.infrastructure.file;

import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.report.SummaryFinalReportFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/// Writes the executive summary report to the output directory.
///
/// Resolves the appropriate output subdirectory based on the invocation timestamp
/// and writes the formatted summary file.
///
/// No DI annotations — instantiated by the factory in infrastructure.copilot.
public class SummaryReportWriter {

    private static final Logger logger = LoggerFactory.getLogger(SummaryReportWriter.class);

    private static final Pattern INVOCATION_TIMESTAMP_PATTERN =
        Pattern.compile("\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}");

    private final Path outputDirectory;
    private final String invocationTimestamp;
    private final SummaryFinalReportFormatter summaryFinalReportFormatter;

    public SummaryReportWriter(Path outputDirectory,
                               String invocationTimestamp,
                               SummaryFinalReportFormatter summaryFinalReportFormatter) {
        this.outputDirectory = Objects.requireNonNull(outputDirectory);
        this.invocationTimestamp = Objects.requireNonNull(invocationTimestamp);
        this.summaryFinalReportFormatter = Objects.requireNonNull(summaryFinalReportFormatter);
    }

    public Path write(String summaryContent, String repository, List<ReviewResult> results,
                      String findingsSummary) throws IOException {
        Path summaryOutputDirectory = resolveSummaryOutputDirectory();
        ReportFileUtils.ensureOutputDirectory(summaryOutputDirectory);
        String filename = "executive_summary_%s.md".formatted(invocationTimestamp);
        Path summaryPath = summaryOutputDirectory.resolve(filename);
        String finalReport = summaryFinalReportFormatter.format(
            summaryContent, repository, results, invocationTimestamp, findingsSummary);
        ReportFileUtils.writeSecureString(summaryPath, finalReport);
        logger.info("Wrote summary report: {}", summaryPath);
        return summaryPath;
    }

    public Path resolveSummaryOutputDirectory() {
        Path invocationDirectory = outputDirectory.getFileName();
        if (invocationDirectory == null) return outputDirectory;
        if (!INVOCATION_TIMESTAMP_PATTERN.matcher(invocationDirectory.toString()).matches()) {
            return outputDirectory;
        }
        Path parent = outputDirectory.getParent();
        return parent != null ? parent : outputDirectory;
    }
}
