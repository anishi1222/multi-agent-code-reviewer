package dev.logicojp.reviewer.report;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/// Generates markdown report files for individual agent reviews.
///
/// Merges v1 ReportGenerator, ReportContentFormatter, ReportFileUtils,
/// and ReportFilenameUtils into a single class.
public class ReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    private static final Set<PosixFilePermission> OWNER_DIRECTORY_PERMISSIONS =
        PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> OWNER_FILE_PERMISSIONS =
        PosixFilePermissions.fromString("rw-------");

    private final Path outputDirectory;
    private final TemplateService templateService;
    private final String invocationTimestamp;

    public ReportGenerator(Path outputDirectory, TemplateService templateService) {
        this(outputDirectory, templateService, Clock.systemDefaultZone());
    }

    ReportGenerator(Path outputDirectory, TemplateService templateService, Clock clock) {
        this.outputDirectory = outputDirectory;
        this.templateService = templateService;
        this.invocationTimestamp = LocalDateTime.now(clock).format(TIMESTAMP_FORMATTER);
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /// Generates reports for all review results.
    public List<Path> generateReports(List<ReviewResult> results) throws IOException {
        ensureOutputDirectory(outputDirectory);

        List<Path> paths = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (ReviewResult result : results) {
            try {
                paths.add(generateReport(result));
            } catch (IOException e) {
                String agentName = result.agentConfig().name();
                logger.error("Failed to generate report for {}: {}", agentName, e.getMessage(), e);
                failures.add(agentName);
            }
        }
        if (!failures.isEmpty()) {
            logger.warn("Failed to generate reports for {} agent(s): {}", failures.size(), failures);
        }
        return List.copyOf(paths);
    }

    /// Returns the invocation timestamp string (used by SummaryGenerator for report links).
    String getInvocationTimestamp() {
        return invocationTimestamp;
    }

    // ------------------------------------------------------------------
    // Single report generation
    // ------------------------------------------------------------------

    Path generateReport(ReviewResult result) throws IOException {
        ensureOutputDirectory(outputDirectory);
        Path reportPath = createReportPath(result.agentConfig());
        String reportContent = formatReportContent(result);
        writeSecureString(reportPath, reportContent);
        logger.info("Generated report: {}", reportPath);
        return reportPath;
    }

    // ------------------------------------------------------------------
    // Report content formatting (from v1 ReportContentFormatter)
    // ------------------------------------------------------------------

    private String formatReportContent(ReviewResult result) {
        AgentConfig config = result.agentConfig();
        String content = result.success()
            ? (result.content() != null ? result.content() : "")
            : "⚠️ **レビュー失敗**\n\nエラー: " + result.errorMessage();

        var placeholders = Map.of(
            "displayName", config.displayName(),
            "date", invocationTimestamp,
            "repository", result.repository(),
            "focusAreas", formatFocusAreas(config),
            "content", content
        );
        return templateService.getReportTemplate(placeholders);
    }

    private String formatFocusAreas(AgentConfig config) {
        var joiner = new StringJoiner("\n", "", "\n");
        for (String area : config.focusAreas()) {
            joiner.add("- " + area);
        }
        return joiner.toString();
    }

    // ------------------------------------------------------------------
    // File path / naming (from v1 ReportFilenameUtils)
    // ------------------------------------------------------------------

    private Path createReportPath(AgentConfig config) throws IOException {
        String filename = ReportFileNames.agentReportFileName(sanitizeAgentName(config.name()));
        Path reportPath = outputDirectory.resolve(filename).normalize();
        if (!reportPath.startsWith(outputDirectory.normalize())) {
            throw new IOException("Invalid agent name: path traversal detected in '" + config.name() + "'");
        }
        return reportPath;
    }

    /// Sanitizes an agent name for safe use in filenames.
    /// Package-private — also used by SummaryGenerator for report link entries.
    static String sanitizeAgentName(String agentName) {
        return agentName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // ------------------------------------------------------------------
    // File I/O utilities (from v1 ReportFileUtils)
    // ------------------------------------------------------------------

    /// Ensures the output directory exists with secure permissions.
    /// Package-private — also used by SummaryGenerator.
    static void ensureOutputDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            return;
        }
        if (supportsPosix(directory)) {
            Files.createDirectories(directory,
                PosixFilePermissions.asFileAttribute(OWNER_DIRECTORY_PERMISSIONS));
            Files.setPosixFilePermissions(directory, OWNER_DIRECTORY_PERMISSIONS);
        } else {
            Files.createDirectories(directory);
        }
    }

    /// Writes content to a file with owner-only permissions on POSIX systems.
    /// Package-private — also used by SummaryGenerator.
    static void writeSecureString(Path filePath, String content) throws IOException {
        ensureOutputDirectory(filePath.getParent());

        Path tempFile = Files.createTempFile(filePath.getParent(), ".tmp-", ".part");
        try {
            Files.writeString(tempFile, content);
            if (supportsPosix(tempFile)) {
                Files.setPosixFilePermissions(tempFile, OWNER_FILE_PERMISSIONS);
            }
            Files.move(tempFile, filePath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
            if (supportsPosix(filePath)) {
                Files.setPosixFilePermissions(filePath, OWNER_FILE_PERMISSIONS);
            }
        } catch (IOException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }

    private static boolean supportsPosix(Path path) {
        Path target = path;
        if (!Files.exists(target)) {
            Path parent = path.getParent();
            if (parent != null && Files.exists(parent)) {
                target = parent;
            }
        }
        return Files.getFileAttributeView(target, PosixFileAttributeView.class) != null;
    }
}
