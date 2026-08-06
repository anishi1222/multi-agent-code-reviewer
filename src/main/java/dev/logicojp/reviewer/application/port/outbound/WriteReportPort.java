package dev.logicojp.reviewer.application.port.outbound;

import java.nio.file.Path;

/// Outbound port: write report files to the filesystem.
///
/// Implementer: {@code infrastructure.file.ReportFileWriter}
/// Callers:     {@code application.report.GenerateReportUseCase}
///
/// Covers behaviors: OUT-02, OUT-03, OUT-06
public interface WriteReportPort {

    /// Write report content to a file within the given output directory.
    ///
    /// @param content   the full text content to write
    /// @param filename  the target filename (without any path component)
    /// @param outputDir the directory to write into
    /// @return the path of the created file
    Path write(String content, String filename, Path outputDir);

    /// Create a timestamped output directory beneath the given base directory.
    ///
    /// @param baseDir the root directory under which the timestamped subdirectory is created
    /// @return the path of the created directory
    Path createOutputDirectory(Path baseDir);
}
