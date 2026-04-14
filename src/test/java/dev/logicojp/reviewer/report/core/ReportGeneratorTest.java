package dev.logicojp.reviewer.report.core;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.service.TemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportGenerator")
class ReportGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("レビュー結果からMarkdownレポートを生成できる")
    void generateReportCreatesMarkdownFile() throws Exception {
        TemplateService templateService = new TemplateService(
            new TemplateConfig(null, null, null, null, null, null, null, null, null)
        );
        ReportGenerator generator = new ReportGenerator(tempDir, templateService);

        AgentConfig config = new AgentConfig(
            "security", "Security", "model", "system", "instruction", null, List.of("SQL"), List.of()
        );
        ReviewResult result = ReviewResult.builder()
            .agentConfig(config)
            .repository("owner/repo")
            .content("review body")
            .success(true)
            .timestamp(Instant.now())
            .build();

        Path output = generator.generateReport(result);

        assertThat(output).exists();
        assertThat(output.getFileName().toString()).isEqualTo("security-report.md");
        String content = Files.readString(output);
        assertThat(content).contains("Security");
        assertThat(content).contains("review body");
    }

    @Test
    @DisplayName("複数のレビュー結果からレポートを一括生成できる")
    void generateReportsCreatesMultipleFiles() throws Exception {
        TemplateService templateService = new TemplateService(
            new TemplateConfig(null, null, null, null, null, null, null, null, null)
        );
        ReportGenerator generator = new ReportGenerator(tempDir, templateService);

        var config1 = new AgentConfig("security", "Security", "model", "sys", "inst", null, List.of(), List.of());
        var config2 = new AgentConfig("quality", "Quality", "model", "sys", "inst", null, List.of(), List.of());
        var result1 = ReviewResult.builder().agentConfig(config1).repository("r").content("body1").success(true).build();
        var result2 = ReviewResult.builder().agentConfig(config2).repository("r").content("body2").success(true).build();

        List<Path> paths = generator.generateReports(List.of(result1, result2));

        assertThat(paths).hasSize(2);
        assertThat(paths)
            .extracting(path -> path.getFileName().toString())
            .containsExactlyInAnyOrder("security-report.md", "quality-report.md");
        assertThat(paths).allSatisfy(p -> assertThat(p).exists());
    }

    @Test
    @DisplayName("一部レポート生成失敗時も成功分のパスを返す")
    void generateReportsReturnsSuccessfulPathsOnPartialFailure() throws Exception {
        TemplateService templateService = new TemplateService(
            new TemplateConfig(null, null, null, null, null, null, null, null, null)
        );

        // Use a read-only subdirectory to force IOExceptions after the first report
        Path readOnlyDir = tempDir.resolve("readonly");
        Files.createDirectories(readOnlyDir);
        ReportGenerator generator = new ReportGenerator(readOnlyDir, templateService);

        var config = new AgentConfig("sec", "Sec", "model", "sys", "inst", null, List.of(), List.of());
        var result = ReviewResult.builder().agentConfig(config).repository("r").content("body").success(true).build();

        // First call should succeed
        List<Path> paths = generator.generateReports(List.of(result));
        assertThat(paths).hasSize(1);
    }
}
