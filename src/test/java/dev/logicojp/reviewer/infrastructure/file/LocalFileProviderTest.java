package dev.logicojp.reviewer.infrastructure.file;

import dev.logicojp.reviewer.domain.review.LocalFileCandidate;
import dev.logicojp.reviewer.domain.review.LocalFileSelectionConfig;
import dev.logicojp.reviewer.infrastructure.config.LocalFileConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalFileProvider")
class LocalFileProviderTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("collect")
    class CollectFiles {

        @Test
        @DisplayName("Javaファイルを収集できる")
        void collectsJavaFiles() throws IOException {
            Files.writeString(tempDir.resolve("Main.java"), "public class Main {}");
            LocalFileProvider provider = new LocalFileProvider();
            List<LocalFileCandidate> files = provider.collect(tempDir, defaultSelectionConfig());
            assertThat(files).hasSize(1);
            assertThat(relativePath(files.getFirst())).isEqualTo("Main.java");
        }

        @Test
        @DisplayName("無視ディレクトリ内のファイルを除外する")
        void skipsIgnoredDirectories() throws IOException {
            Path gitDir = tempDir.resolve(".git");
            Files.createDirectories(gitDir);
            Files.writeString(gitDir.resolve("config.java"), "class X {}");
            Files.writeString(tempDir.resolve("App.java"), "class App {}");
            LocalFileProvider provider = new LocalFileProvider();
            List<LocalFileCandidate> files = provider.collect(tempDir, defaultSelectionConfig());
            assertThat(files).hasSize(1);
            assertThat(relativePath(files.getFirst())).isEqualTo("App.java");
        }

        @Test
        @DisplayName("センシティブファイルを除外する")
        void excludesSensitiveFiles() throws IOException {
            Files.writeString(tempDir.resolve(".env"), "SECRET=abc");
            Files.writeString(tempDir.resolve("id_rsa.pem"), "key");
            Files.writeString(tempDir.resolve("Main.java"), "class Main {}");
            LocalFileProvider provider = new LocalFileProvider();
            List<LocalFileCandidate> files = provider.collect(tempDir, defaultSelectionConfig());
            assertThat(files).hasSize(1);
            assertThat(relativePath(files.getFirst())).isEqualTo("Main.java");
        }

        @Test
        @DisplayName("追加のセンシティブファイルパターンが機能する")
        void excludesAdditionalSensitivePatterns() throws IOException {
            Files.writeString(tempDir.resolve("application-dev.yml"), "db: secret");
            Files.writeString(tempDir.resolve(".env.local"), "LOCAL_KEY=abc");
            Files.writeString(tempDir.resolve(".env.production"), "PROD_KEY=xyz");
            Files.writeString(tempDir.resolve("Main.java"), "class Main {}");
            LocalFileSelectionConfig config = LocalFileSelectionConfig.of(
                1024,
                4096,
                LocalFileConfig.DEFAULT_IGNORED_DIRECTORIES,
                List.of("java", "yml", "local", "production"),
                List.of("application-dev", ".env.local", ".env.production"),
                List.of("pem")
            );
            LocalFileProvider provider = new LocalFileProvider();
            List<LocalFileCandidate> files = provider.collect(tempDir, config);
            assertThat(files).hasSize(1);
            assertThat(relativePath(files.getFirst())).isEqualTo("Main.java");
        }

        @Test
        @DisplayName("ソースファイル拡張子に一致しないファイルを除外する")
        void excludesNonSourceFiles() throws IOException {
            Files.writeString(tempDir.resolve("image.png"), "binary");
            Files.writeString(tempDir.resolve("App.java"), "class App {}");
            LocalFileProvider provider = new LocalFileProvider();
            List<LocalFileCandidate> files = provider.collect(tempDir, defaultSelectionConfig());
            assertThat(files).hasSize(1);
        }

        @Test
        @DisplayName("空のディレクトリからは空リストを返す")
        void emptyDirectoryReturnsEmpty() {
            LocalFileProvider provider = new LocalFileProvider();
            List<LocalFileCandidate> files = provider.collect(tempDir, defaultSelectionConfig());
            assertThat(files).isEmpty();
        }

        @Test
        @DisplayName("サブディレクトリ内のファイルも収集する")
        void collectsFromSubdirectories() throws IOException {
            Path subDir = tempDir.resolve("src/main");
            Files.createDirectories(subDir);
            Files.writeString(subDir.resolve("App.java"), "class App {}");
            LocalFileProvider provider = new LocalFileProvider();
            List<LocalFileCandidate> files = provider.collect(tempDir, defaultSelectionConfig());
            assertThat(files).hasSize(1);
            assertThat(relativePath(files.getFirst())).contains("src/main/App.java");
        }

        @Test
        @DisplayName("設定で許可した拡張子のみを収集する")
        void collectsOnlyConfiguredExtensions() throws IOException {
            Files.writeString(tempDir.resolve("readme.txt"), "ok");
            Files.writeString(tempDir.resolve("Main.java"), "class Main {}");

            LocalFileSelectionConfig config = LocalFileSelectionConfig.of(
                1024,
                4096,
                List.of(".git"),
                List.of("txt"),
                List.of(".env"),
                List.of("pem")
            );
            LocalFileProvider provider = new LocalFileProvider();
            List<LocalFileCandidate> files = provider.collect(tempDir, config);

            assertThat(files).hasSize(1);
            assertThat(relativePath(files.getFirst())).isEqualTo("readme.txt");
        }
    }

    @Nested
    @DisplayName("formatContent")
    class GenerateReviewContent {

        @Test
        @DisplayName("空のファイルリストからはno source filesメッセージを返す")
        void emptyFilesReturnsNoSourceMessage() {
            LocalFileProvider provider = new LocalFileProvider();
            String content = provider.formatContent(List.of());
            assertThat(content).contains("no source files found");
        }

        @Test
        @DisplayName("nullのファイルリストからはno source filesメッセージを返す")
        void nullFilesReturnsNoSourceMessage() {
            LocalFileProvider provider = new LocalFileProvider();
            String content = provider.formatContent(null);
            assertThat(content).contains("no source files found");
        }

        @Test
        @DisplayName("ファイルコンテンツをMarkdownコードブロックでラップする")
        void wrapsInCodeBlocks() throws IOException {
            Path mainFile = tempDir.resolve("Main.java");
            Files.writeString(mainFile, "public class Main {}");
            LocalFileProvider provider = new LocalFileProvider();
            String content = provider.formatContent(List.of(new LocalFileCandidate(mainFile, Files.size(mainFile))));
            assertThat(content).contains("### Main.java");
            assertThat(content).contains("```java");
            assertThat(content).contains("public class Main {}");
        }
    }

    @Nested
    @DisplayName("collectAndFormat directorySummary")
    class GenerateDirectorySummary {

        @Test
        @DisplayName("空のファイルリストからはno source filesメッセージを返す")
        void emptyFilesReturnsMessage() {
            LocalFileProvider provider = new LocalFileProvider();
            String summary = provider.collectAndFormat(tempDir, defaultSelectionConfig()).directorySummary();
            assertThat(summary).contains("No source files found");
        }

        @Test
        @DisplayName("ファイル数とサイズ情報を含む")
        void includesCountAndSize() throws IOException {
            Files.writeString(tempDir.resolve("App.java"), "class App {}");
            LocalFileProvider provider = new LocalFileProvider();
            String summary = provider.collectAndFormat(tempDir, defaultSelectionConfig()).directorySummary();
            assertThat(summary).contains("Files: 1");
            assertThat(summary).contains("12 bytes");
        }
    }

    @Nested
    @DisplayName("入力検証")
    class ConstructorTests {

        @Test
        @DisplayName("nullのディレクトリは空リストを返す")
        void nullBaseDirectoryThrows() {
            LocalFileProvider provider = new LocalFileProvider();
            assertThat(provider.collect(null, defaultSelectionConfig())).isEmpty();
        }
    }

    private LocalFileSelectionConfig defaultSelectionConfig() {
        return selectionConfig(new LocalFileConfig());
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

    private String relativePath(LocalFileCandidate candidate) {
        return tempDir.relativize(candidate.path()).toString().replace('\\', '/');
    }
}
