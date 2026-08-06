package dev.logicojp.reviewer.infrastructure.file;

import dev.logicojp.reviewer.application.port.outbound.WriteReportPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/// Infrastructure implementation of {@link WriteReportPort}.
///
/// Writes review report files to the filesystem using secure atomic writes.
/// No DI annotations — instantiated by the factory in infrastructure.copilot.
public class ReportFileWriter implements WriteReportPort {

    private static final Logger logger = LoggerFactory.getLogger(ReportFileWriter.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    @Override
    public Path write(String content, String filename, Path outputDir) {
        try {
            ReportFileUtils.ensureOutputDirectory(outputDir);
            Path reportPath = outputDir.resolve(filename);
            ReportFileUtils.writeSecureString(reportPath, content);
            logger.info("Wrote report: {}", reportPath);
            return reportPath;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write report file: " + filename, e);
        }
    }

    @Override
    public Path createOutputDirectory(Path baseDir) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        Path outputDir = baseDir.resolve(timestamp);
        try {
            ReportFileUtils.ensureOutputDirectory(outputDir);
            return outputDir;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create output directory: " + outputDir, e);
        }
    }
}
