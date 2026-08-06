package dev.logicojp.reviewer.infrastructure.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportFileWriter")
class ReportFileWriterTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Markdownレポートファイルを書き込める")
    void writeCreatesMarkdownFile() throws Exception {
        ReportFileWriter writer = new ReportFileWriter();

        Path output = writer.write("# Security\n\nreview body", "security-report.md", tempDir);

        assertThat(output).exists();
        assertThat(output.getFileName().toString()).isEqualTo("security-report.md");
        assertThat(Files.readString(output)).contains("Security", "review body");
    }

    @Test
    @DisplayName("複数のレビュー結果からレポートを一括生成できる")
    void generateReportsCreatesMultipleFiles() {
        ReportFileWriter writer = new ReportFileWriter();

        List<Path> paths = List.of(
            writer.write("body1", "security-report.md", tempDir),
            writer.write("body2", "quality-report.md", tempDir)
        );

        assertThat(paths).hasSize(2);
        assertThat(paths)
            .extracting(path -> path.getFileName().toString())
            .containsExactlyInAnyOrder("security-report.md", "quality-report.md");
        assertThat(paths).allSatisfy(path -> assertThat(path).exists());
    }

    @Test
    @DisplayName("timestamp付き出力ディレクトリを作成できる")
    void createOutputDirectoryCreatesTimestampedDirectory() {
        ReportFileWriter writer = new ReportFileWriter();

        Path outputDir = writer.createOutputDirectory(tempDir);

        assertThat(outputDir).isDirectory();
        assertThat(outputDir.getParent()).isEqualTo(tempDir);
        assertThat(outputDir.getFileName().toString())
            .matches("\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}");
    }

    // not ported: ReportFileWriter exposes single-file writes only; partial-success behavior for bulk generation no longer exists here.
}
