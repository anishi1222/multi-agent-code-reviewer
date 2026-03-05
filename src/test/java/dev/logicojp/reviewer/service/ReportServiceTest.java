package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.report.finding.AggregatedFinding;
import dev.logicojp.reviewer.report.finding.ReviewFindingParser;
import dev.logicojp.reviewer.report.finding.ReviewFindingSimilarity;
import dev.logicojp.reviewer.report.finding.FindingsExtractor;
import dev.logicojp.reviewer.report.sanitize.ContentSanitizer;
import dev.logicojp.reviewer.report.summary.SummaryGenerator;

import com.github.copilot.sdk.CopilotClient;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.SummaryConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.report.core.ReportGenerator;
import dev.logicojp.reviewer.report.factory.ReportGeneratorFactory;
import dev.logicojp.reviewer.report.core.ReviewResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportService")
class ReportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("generateReports は factory 生成のレポートジェネレータを使用する")
    void generateReportsUsesFactoryGenerator() throws IOException {
        TemplateService templateService = new TemplateService(new TemplateConfig(tempDir.toString(),
            null, null, null, null, null, null, null));

        AtomicReference<Path> capturedDir = new AtomicReference<>();
        ReportGeneratorFactory factory = new ReportGeneratorFactory(templateService, new SummaryConfig(0, 0, 0, 0, 0, 0)) {
            @Override
            public ReportGenerator createReportGenerator(Path outputDirectory) {
                return new ReportGenerator(outputDirectory, templateService) {
                    @Override
                    public List<Path> generateReports(List<ReviewResult> results) {
                        capturedDir.set(outputDirectory);
                        return List.of(outputDirectory.resolve("a.md"));
                    }
                };
            }

            @Override
            public SummaryGenerator createSummaryGenerator(Path outputDirectory,
                                                           CopilotClient copilotClient,
                                                           String summaryModel,
                                                           String reasoningEffort,
                                                           long timeoutMinutes) {
                return SummaryGenerator.builder(outputDirectory, copilotClient, summaryModel, templateService)
                    .reasoningEffort(reasoningEffort)
                    .timeoutMinutes(timeoutMinutes)
                    .summaryConfig(new SummaryConfig(0, 0, 0, 0, 0, 0))
                    .build();
            }
        };

        CopilotService copilotService = new CopilotService(
            new CopilotCliPathResolver(),
            new CopilotCliHealthChecker(new CopilotTimeoutResolver()),
            new CopilotTimeoutResolver(),
            new CopilotStartupErrorFormatter(),
            new CopilotClientStarter(),
            null
        ) {
            @Override
            public CopilotClient getClient() {
                return null;
            }
        };

        ReportService service = new ReportService(
            copilotService,
            dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(1, 1, 1, 1, 1, 1, 3, 1, 0, 0, 0, 0),
            factory
        );

        List<Path> paths = service.generateReports(List.of(), tempDir);

        assertThat(capturedDir.get()).isEqualTo(tempDir);
        assertThat(paths).containsExactly(tempDir.resolve("a.md"));
    }

    @Test
    @DisplayName("generateSummary は timeout を渡して factory 生成ジェネレータを使用する")
    void generateSummaryUsesFactoryGeneratorWithTimeout() throws IOException {
        Files.writeString(tempDir.resolve("executive-summary.md"), "${repository}\n${summary}\n");

        TemplateService templateService = new TemplateService(new TemplateConfig(tempDir.toString(),
            null, null, null, null, null, null, null));

        AtomicReference<Long> capturedTimeout = new AtomicReference<>();
        AtomicReference<String> capturedModel = new AtomicReference<>();
        ReportGeneratorFactory factory = new ReportGeneratorFactory(templateService, new SummaryConfig(0, 0, 0, 0, 0, 0)) {
            @Override
            public ReportGenerator createReportGenerator(Path outputDirectory) {
                return new ReportGenerator(outputDirectory, templateService);
            }

            @Override
            public SummaryGenerator createSummaryGenerator(Path outputDirectory,
                                                           CopilotClient copilotClient,
                                                           String summaryModel,
                                                           String reasoningEffort,
                                                           long timeoutMinutes) {
                capturedTimeout.set(timeoutMinutes);
                capturedModel.set(summaryModel);
                return SummaryGenerator.builder(outputDirectory, copilotClient, summaryModel, templateService)
                    .reasoningEffort(reasoningEffort)
                    .timeoutMinutes(timeoutMinutes)
                    .summaryConfig(new SummaryConfig(0, 0, 0, 0, 0, 0))
                    .aiSummaryBuilder((results, repository) -> "summary")
                    .build();
            }
        };

        CopilotService copilotService = new CopilotService(
            new CopilotCliPathResolver(),
            new CopilotCliHealthChecker(new CopilotTimeoutResolver()),
            new CopilotTimeoutResolver(),
            new CopilotStartupErrorFormatter(),
            new CopilotClientStarter(),
            null
        ) {
            @Override
            public CopilotClient getClient() {
                return null;
            }
        };

        ReportService service = new ReportService(
            copilotService,
            dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(1, 1, 1, 1, 1, 1, 7, 1, 0, 0, 0, 0),
            factory
        );

        Path summary = service.generateSummary(List.of(), "owner/repo", tempDir, "m", "high");

        assertThat(capturedTimeout.get()).isEqualTo(7L);
        assertThat(capturedModel.get()).isEqualTo("m");
        assertThat(summary).exists();
        assertThat(summary.getParent()).isEqualTo(tempDir);
    }
}
