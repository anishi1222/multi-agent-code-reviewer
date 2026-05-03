package dev.logicojp.reviewer.report.core;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.report.formatter.ReportContentFormatter;
import dev.logicojp.reviewer.report.util.ReportFileUtils;
import dev.logicojp.reviewer.report.util.ReportFilenameUtils;
import dev.logicojp.reviewer.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Generates markdown report files for individual agent reviews.
public class ReportGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    
    private final Path outputDirectory;
    private final ReportContentFormatter reportContentFormatter;
    private final String invocationTimestamp;
    
    public ReportGenerator(Path outputDirectory, TemplateService templateService) {
        this(outputDirectory, templateService, Clock.systemDefaultZone());
    }

    ReportGenerator(Path outputDirectory, TemplateService templateService, Clock clock) {
        this.outputDirectory = outputDirectory;
        this.reportContentFormatter = new ReportContentFormatter(templateService);
        this.invocationTimestamp = LocalDateTime.now(clock).format(TIMESTAMP_FORMATTER);
    }
    
    /// Generates a markdown report file for the given review result.
    /// @param result The review result to generate a report for
    /// @return Path to the generated report file
      Path generateReport(ReviewResult result) throws IOException {
          return generateReport(result, 1);
     }

     Path generateReport(ReviewResult result, int sequence) throws IOException {
        ensureOutputDirectory();
        AgentConfig config = result.agentConfig();
          Path reportPath = createReportPath(config, sequence);
        
        String reportContent = reportContentFormatter.format(result, invocationTimestamp);
        ReportFileUtils.writeSecureString(reportPath, reportContent);
        
        logger.info("Generated report: {}", reportPath);
        return reportPath;
    }
    
    /// Generates reports for all review results.
    /// @param results List of review results
    /// @return List of paths to generated report files
    public List<Path> generateReports(List<ReviewResult> results) throws IOException {
        ensureOutputDirectory();

        List<Path> paths = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        Map<String, Integer> agentSequence = new HashMap<>();
        for (ReviewResult result : results) {
            try {
                String agentName = result.agentConfig() != null ? result.agentConfig().name() : "unknown";
                int sequence = agentSequence.merge(agentName, 1, Integer::sum);
                paths.add(generateReport(result, sequence));
            } catch (IOException e) {
                String agentName = result.agentConfig() != null ? result.agentConfig().name() : "unknown";
                logger.error("Failed to generate report for {}: {}", agentName, e.getMessage(), e);
                failures.add(agentName);
            }
        }
        if (!failures.isEmpty()) {
            logger.warn("Failed to generate reports for {} agent(s): {}", failures.size(), failures);
        }
        return List.copyOf(paths);
    }

    private Path createReportPath(AgentConfig config, int sequence) throws IOException {
        String filename = buildReportFilename(config, sequence);
        Path normalizedOutput = outputDirectory.toRealPath();
        Path reportPath = normalizedOutput.resolve(filename).normalize();
        if (!reportPath.startsWith(normalizedOutput)) {
            throw new IOException("Invalid agent name: path traversal detected in '" + config.name() + "'");
        }
        return reportPath;
    }

    private String buildReportFilename(AgentConfig config, int sequence) {
        String safeName = ReportFilenameUtils.sanitizeAgentName(config.name());
        if (sequence > 1) {
            return "%s-pass-%d-report.md".formatted(safeName, sequence);
        }
        return "%s-report.md".formatted(safeName);
    }
    
    private void ensureOutputDirectory() throws IOException {
        ReportFileUtils.ensureOutputDirectory(outputDirectory);
        logger.debug("Ensured output directory exists: {}", outputDirectory);
    }
}
