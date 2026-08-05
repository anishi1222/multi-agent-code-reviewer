package dev.logicojp.reviewer.application.report;

import dev.logicojp.reviewer.application.port.inbound.GenerateReportPort;
import dev.logicojp.reviewer.application.port.inbound.ReportOptions;
import dev.logicojp.reviewer.application.port.inbound.ReportOutput;
import dev.logicojp.reviewer.application.port.outbound.GenerateAiSummaryPort;
import dev.logicojp.reviewer.application.port.outbound.LoadTemplatePort;
import dev.logicojp.reviewer.application.port.outbound.WriteReportPort;
import dev.logicojp.reviewer.application.report.SummaryGenerator.SummaryGenerationConfig;
import dev.logicojp.reviewer.domain.report.ReportContentFormatter;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.shared.ReportFilenameUtils;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Application use-case: generate per-agent reports and an optional executive summary.
///
/// Implements {@link GenerateReportPort}. Writes files via {@link WriteReportPort},
/// loads templates via {@link LoadTemplatePort}, and delegates AI summary generation
/// to {@link SummaryGenerator}.
///
/// No framework annotations — DI is handled by the infrastructure configuration layer.
///
/// Template key used directly: {@code "report"} (per-agent report template).
///
/// Application layer: imports only {@code application.port.*}, {@code domain.*},
/// {@code shared.*}, {@code java.*} — no {@code infrastructure.*}.
public final class GenerateReportUseCase implements GenerateReportPort {

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final WriteReportPort writer;
    private final LoadTemplatePort templates;
    private final SummaryGenerator summaryGenerator;
    private final Clock clock;

    /// Template key for the per-agent report template.
    private static final String TEMPLATE_REPORT = "report";

    public GenerateReportUseCase(WriteReportPort writer,
                                  LoadTemplatePort templates,
                                  GenerateAiSummaryPort aiSummary,
                                  SummaryGenerationConfig config) {
        this(writer, templates, aiSummary, config, Clock.systemUTC());
    }

    /// Full constructor for testing — allows clock injection.
    public GenerateReportUseCase(WriteReportPort writer,
                                  LoadTemplatePort templates,
                                  GenerateAiSummaryPort aiSummary,
                                  SummaryGenerationConfig config,
                                  Clock clock) {
        this.writer = writer;
        this.templates = templates;
        this.summaryGenerator = new SummaryGenerator(templates, aiSummary, config);
        this.clock = clock;
    }

    /// Generates individual per-agent reports and, unless skipped, an executive summary.
    ///
    /// {@inheritDoc}
    @Override
    public ReportOutput generate(List<ReviewResult> results, ReportOptions options) {
        if (results == null || results.isEmpty()) {
            return ReportOutput.of(List.of());
        }

        String date = LocalDateTime.now(clock).format(DATE_FORMATTER);
        Path outputDir = writer.createOutputDirectory(options.outputDir());

        // 1. Write per-agent reports
        String reportTemplate = templates.loadRaw(TEMPLATE_REPORT);
        ReportContentFormatter formatter = new ReportContentFormatter(reportTemplate);
        List<Path> reportPaths = writePerAgentReports(results, formatter, outputDir, date);

        // 2. Generate executive summary (unless skipped)
        if (options.skipSummary()) {
            return ReportOutput.of(reportPaths);
        }

        String repository = extractRepository(results);
        String summaryContent = summaryGenerator.buildSummaryContent(results, repository);
        String formattedSummary = summaryGenerator.formatSummary(summaryContent, results, repository, date);

        String summaryFilename = "summary_%s.md".formatted(date);
        writer.write(formattedSummary, summaryFilename, outputDir);

        return new ReportOutput(reportPaths, summaryContent);
    }

    /// Generates only the AI summary prose — no files are written.
    ///
    /// {@inheritDoc}
    @Override
    public Optional<String> generateSummary(List<ReviewResult> results, ReportOptions options) {
        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }
        if (options.skipSummary()) {
            return Optional.empty();
        }
        String repository = extractRepository(results);
        String summaryContent = summaryGenerator.buildSummaryContent(results, repository);
        if (summaryContent.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(summaryContent);
    }

    private List<Path> writePerAgentReports(List<ReviewResult> results,
                                             ReportContentFormatter formatter,
                                             Path outputDir,
                                             String date) {
        List<Path> paths = new ArrayList<>(results.size());
        for (ReviewResult result : results) {
            String content = formatter.format(result, date);
            String safeName = ReportFilenameUtils.sanitizeAgentName(result.agentConfig().name());
            String filename = "%s_%s.md".formatted(safeName, date);
            Path path = writer.write(content, filename, outputDir);
            paths.add(path);
        }
        return List.copyOf(paths);
    }

    private String extractRepository(List<ReviewResult> results) {
        if (results.isEmpty()) {
            return "";
        }
        String repo = results.getFirst().repository();
        return repo != null ? repo : "";
    }
}
