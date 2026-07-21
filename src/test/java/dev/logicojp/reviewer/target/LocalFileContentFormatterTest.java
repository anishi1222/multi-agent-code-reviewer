package dev.logicojp.reviewer.target;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        var formatter = new LocalFileContentFormatter(tempDir, 4096);
        var files = List.of(new LocalFileProvider.LocalFile("Main.java", "class Main {}", 13));

        String content = formatter.generateReviewContent(files);

        assertThat(content).contains("### Main.java");
        assertThat(content).contains("```java");
        assertThat(content).contains("class Main {}");
    }

    @Test
    @DisplayName("Markdown内のコードフェンスより長い外側フェンスを使用する")
    void usesLongerFenceForMarkdownWithNestedFence() {
        var formatter = new LocalFileContentFormatter(tempDir, 4096);
        var files = List.of(new LocalFileProvider.LocalFile(
            "README.md",
            "Example:\n\n```java\nclass Main {}\n```\n",
            42
        ));

        String content = formatter.generateReviewContent(files);

        assertThat(content).contains("````markdown");
        assertThat(content).contains("\n````\n");
        assertThat(content).contains("```java");
    }

    @Test
    @DisplayName("空の入力ではno source filesメッセージを返す")
    void returnsNoSourceFilesMessageForEmptyInput() {
        var formatter = new LocalFileContentFormatter(tempDir, 4096);

        assertThat(formatter.generateReviewContent(List.of())).isEqualTo("(no source files found)");
        assertThat(formatter.generateDirectorySummary(List.of()))
            .isEqualTo("No source files found in: " + tempDir);
    }

    @Test
    @DisplayName("件数・総サイズ・一覧を含む要約を生成する")
    void generatesDirectorySummaryWithFileList() {
        var formatter = new LocalFileContentFormatter(tempDir, 4096);
        StringBuilder list = new StringBuilder("  - src/Main.java (13 bytes)\n");

        String summary = formatter.generateDirectorySummary(1, 13, list);

        assertThat(summary).contains("Directory: " + tempDir);
        assertThat(summary).contains("Files: 1");
        assertThat(summary).contains("Total size: 13 bytes");
        assertThat(summary).contains("src/Main.java");
    }
}
