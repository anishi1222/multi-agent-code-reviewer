package dev.logicojp.reviewer.target;

import org.assertj.core.api.Assertions;
import dev.logicojp.reviewer.config.ReviewerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


@DisplayName("LocalFileProvider")
class LocalFileProviderTest {

    @Nested
    @DisplayName("コンストラクタ")
    class Constructor {

        @Test
        @DisplayName("nullのベースディレクトリは例外をスローする")
        void throwsForNullDirectory() {
            Assertions.assertThatThrownBy(() -> new LocalFileProvider(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("有効なディレクトリでインスタンス化できる")
        void createsWithValidDirectory(@TempDir Path tempDir) {
            var provider = new LocalFileProvider(tempDir);
            Assertions.assertThat(provider).isNotNull();
        }
    }

    @Nested
    @DisplayName("collectAndGenerate")
    class CollectAndGenerate {

        @Test
        @DisplayName("空のディレクトリでは'no source files'を返す")
        void emptyDirectoryReturnsNoFiles(@TempDir Path tempDir) {
            var provider = new LocalFileProvider(tempDir);
            var result = provider.collectAndGenerate();

            Assertions.assertThat(result.fileCount()).isZero();
            Assertions.assertThat(result.totalSizeBytes()).isZero();
            Assertions.assertThat(result.reviewContent()).contains("no source files");
        }

        @Test
        @DisplayName("ソースファイルを収集してコンテンツを生成する")
        void collectsSourceFiles(@TempDir Path tempDir) throws IOException {
            Files.writeString(tempDir.resolve("Main.java"), "public class Main {}");
            Files.writeString(tempDir.resolve("App.java"), "public class App {}");

            var provider = new LocalFileProvider(tempDir);
            var result = provider.collectAndGenerate();

            Assertions.assertThat(result.fileCount()).isEqualTo(2);
            Assertions.assertThat(result.totalSizeBytes()).isPositive();
            Assertions.assertThat(result.reviewContent()).contains("Main.java");
            Assertions.assertThat(result.reviewContent()).contains("App.java");
        }

        @Test
        @DisplayName("無視ディレクトリ内のファイルは除外する")
        void skipsIgnoredDirectories(@TempDir Path tempDir) throws IOException {
            Path nodeModules = tempDir.resolve("node_modules");
            Files.createDirectory(nodeModules);
            Files.writeString(nodeModules.resolve("lib.js"), "module.exports = {};");
            Files.writeString(tempDir.resolve("index.js"), "console.log('hello');");

            var provider = new LocalFileProvider(tempDir);
            var result = provider.collectAndGenerate();

            Assertions.assertThat(result.reviewContent()).contains("index.js");
            Assertions.assertThat(result.reviewContent()).doesNotContain("lib.js");
        }

        @Test
        @DisplayName("存在しないディレクトリではUncheckedIOExceptionをスローする")
        void nonExistentDirectoryReturnsEmpty(@TempDir Path tempDir) {
            Path nonExistent = tempDir.resolve("does-not-exist");
            Assertions.assertThatThrownBy(() -> new LocalFileProvider(nonExistent))
                    .isInstanceOf(java.io.UncheckedIOException.class);
        }
    }

    @Nested
    @DisplayName("generateReviewContent")
    class GenerateReviewContent {

        @Test
        @DisplayName("nullリストでは'no source files'を返す")
        void nullListReturnsNoFiles(@TempDir Path tempDir) {
            var provider = new LocalFileProvider(tempDir);
            Assertions.assertThat(provider.generateReviewContent(null)).contains("no source files");
        }
    }

    @Nested
    @DisplayName("generateDirectorySummary")
    class GenerateDirectorySummary {

        @Test
        @DisplayName("空のリストでは'No source files'を返す")
        void emptyListReturnsNoFiles(@TempDir Path tempDir) {
            var provider = new LocalFileProvider(tempDir);
            Assertions.assertThat(provider.generateDirectorySummary(java.util.List.of()))
                .contains("No source files");
        }
    }
}
