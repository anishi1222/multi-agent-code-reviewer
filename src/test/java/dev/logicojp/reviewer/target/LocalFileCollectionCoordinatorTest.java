package dev.logicojp.reviewer.target;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;


@DisplayName("LocalFileCollectionCoordinator")
class LocalFileCollectionCoordinatorTest {

    @Nested
    @DisplayName("collectFiles")
    class CollectFiles {

        @Test
        @DisplayName("ベースディレクトリが欠損した場合は空リストを返す")
        void returnsEmptyWhenBaseDirectoryMissing(@TempDir Path tempDir) throws IOException {
            Path baseDir = tempDir.resolve("source");
            Files.createDirectory(baseDir);
            var coordinator = newCoordinator(baseDir);

            Files.delete(baseDir);

            var files = coordinator.collectFiles();
            Assertions.assertThat(files).isEmpty();
        }

        @Test
        @DisplayName("返却リストは不変")
        void returnsImmutableList(@TempDir Path tempDir) throws IOException {
            Path baseDir = tempDir.resolve("source");
            Files.createDirectory(baseDir);
            Files.writeString(baseDir.resolve("Main.java"), "public class Main {}");
            var coordinator = newCoordinator(baseDir);

            var files = coordinator.collectFiles();

            Assertions.assertThat(files).hasSize(1);
            Assertions.assertThatThrownBy(() -> files.add(new LocalFileProvider.LocalFile("x", "y", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("collectAndGenerate")
    class CollectAndGenerate {

        @Test
        @DisplayName("ベースディレクトリが欠損した場合はno source filesを返す")
        void returnsNoSourceFilesWhenBaseDirectoryMissing(@TempDir Path tempDir) throws IOException {
            Path baseDir = tempDir.resolve("source");
            Files.createDirectory(baseDir);
            var coordinator = newCoordinator(baseDir);

            Files.delete(baseDir);

            var result = coordinator.collectAndGenerate();

            Assertions.assertThat(result.fileCount()).isZero();
            Assertions.assertThat(result.totalSizeBytes()).isZero();
            Assertions.assertThat(result.reviewContent()).isEqualTo("(no source files found)");
            Assertions.assertThat(result.directorySummary()).contains("No source files found in:");
        }

        @Test
        @DisplayName("候補収集・読込・整形を統合して結果を返す")
        void collectsReadsAndFormats(@TempDir Path tempDir) throws IOException {
            Path baseDir = tempDir.resolve("source");
            Files.createDirectory(baseDir);
            Files.writeString(baseDir.resolve("Main.java"), "public class Main {}");
            Files.writeString(baseDir.resolve("README.md"), "# note");

            var coordinator = newCoordinator(baseDir);
            var result = coordinator.collectAndGenerate();

            Assertions.assertThat(result.fileCount()).isEqualTo(2);
            Assertions.assertThat(result.totalSizeBytes()).isPositive();
            Assertions.assertThat(result.reviewContent()).contains("Main.java");
            Assertions.assertThat(result.reviewContent()).contains("README.md");
            Assertions.assertThat(result.directorySummary()).contains("Files: 2");
        }
    }

    private static LocalFileCollectionCoordinator newCoordinator(Path baseDirectory) throws IOException {
        var absoluteBase = baseDirectory.toAbsolutePath().normalize();
        var realBase = absoluteBase.toRealPath();
        var config = new LocalFileSelectionConfig(
            1_024,
            8_192,
            Set.of("node_modules"),
            Set.of("java", "md", "txt"),
            Set.of("secret", "password", "token", "key"),
            Set.of("pem", "p12", "jks")
        );

        var collector = new LocalFileCandidateCollector(
            absoluteBase,
            realBase,
            config,
            LoggerFactory.getLogger(LocalFileCandidateCollector.class)
        );
        var reader = new LocalFileReader(
            absoluteBase,
            realBase,
            config,
            LoggerFactory.getLogger(LocalFileReader.class)
        );
        var formatter = new LocalFileContentFormatter(absoluteBase, config);
        return new LocalFileCollectionCoordinator(absoluteBase, collector, reader, formatter);
    }
}
