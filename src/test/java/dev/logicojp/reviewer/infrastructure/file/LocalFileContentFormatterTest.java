package dev.logicojp.reviewer.infrastructure.file;

import dev.logicojp.reviewer.domain.review.LocalFileSelectionConfig;
import dev.logicojp.reviewer.infrastructure.config.LocalFileConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalFileContentFormatter")
class LocalFileContentFormatterTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("ファイル内容を言語付きコードブロックで整形する")
    void generatesReviewContentWithLanguageCodeFence() {
        var formatter = new LocalFileContentFormatter(4096);
        var content = new StringBuilder();

        formatter.appendFileBlock(content, "Main.java", "class Main {}");

        assertThat(content).contains("### Main.java");
        assertThat(content).contains("```java");
        assertThat(content).contains("class Main {}");
    }

    @Test
    @DisplayName("空の入力ではno source filesメッセージを返す")
    void returnsNoSourceFilesMessageForEmptyInput() {
        var provider = new LocalFileProvider();
        var result = provider.collectAndFormat(tempDir, selectionConfig(new LocalFileConfig()));

        assertThat(provider.formatContent(List.of())).isEqualTo("(no source files found)");
        assertThat(result.directorySummary()).isEqualTo("No source files found in: " + tempDir);
    }

    @Test
    @DisplayName("件数・総サイズ・一覧を含む要約を生成する")
    void generatesDirectorySummaryWithFileList() throws IOException {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/Main.java"), "class Main {}");
        var provider = new LocalFileProvider();

        var summary = provider.collectAndFormat(tempDir, selectionConfig(new LocalFileConfig())).directorySummary();

        assertThat(summary).contains("Directory: " + tempDir);
        assertThat(summary).contains("Files: 1");
        assertThat(summary).contains("Total size: 13 bytes");
        assertThat(summary).contains("src/Main.java");
    }

    private static LocalFileSelectionConfig selectionConfig(LocalFileConfig config) {
        return LocalFileSelectionConfig.of(
            config.maxFileSize(),
            config.maxTotalSize(),
            config.ignoredDirectories(),
            config.sourceExtensions(),
            config.sensitiveFilePatterns(),
            config.sensitiveExtensions()
        );
    }
}
