package dev.logicojp.reviewer.report.summary;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.formatter.SummaryFinalReportFormatter;
import dev.logicojp.reviewer.report.util.ReportFileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

final class SummaryReportWriter {

    private static final Pattern INVOCATION_TIMESTAMP_PATTERN =
        Pattern.compile("\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}");

    private final Path outputDirectory;
    private final String invocationTimestamp;
    private final SummaryFinalReportFormatter summaryFinalReportFormatter;

    SummaryReportWriter(Path outputDirectory,
                        String invocationTimestamp,
                        SummaryFinalReportFormatter summaryFinalReportFormatter) {
        this.outputDirectory = Objects.requireNonNull(outputDirectory);
        this.invocationTimestamp = Objects.requireNonNull(invocationTimestamp);
        this.summaryFinalReportFormatter = Objects.requireNonNull(summaryFinalReportFormatter);
    }

    Path write(String summaryContent, String repository, List<ReviewResult> results) throws IOException {
        Path summaryOutputDirectory = resolveSummaryOutputDirectory();
        ReportFileUtils.ensureOutputDirectory(summaryOutputDirectory);

        String filename = "executive_summary_%s.md".formatted(invocationTimestamp);
        Path summaryPath = summaryOutputDirectory.resolve(filename);
        String finalReport = summaryFinalReportFormatter.format(
            summaryContent,
            repository,
            results,
            invocationTimestamp
        );
        ReportFileUtils.writeSecureString(summaryPath, finalReport);
        return summaryPath;
    }

    Path resolveSummaryOutputDirectory() {
        Path invocationDirectory = outputDirectory.getFileName();
        if (invocationDirectory == null) {
            return outputDirectory;
        }
        if (!INVOCATION_TIMESTAMP_PATTERN.matcher(invocationDirectory.toString()).matches()) {
            return outputDirectory;
        }
        Path parent = outputDirectory.getParent();
        return parent != null ? parent : outputDirectory;
    }
}
