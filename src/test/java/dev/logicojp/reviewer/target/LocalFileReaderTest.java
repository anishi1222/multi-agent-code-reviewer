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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


@DisplayName("LocalFileReader")
class LocalFileReaderTest {

    @Nested
    @DisplayName("processCandidates")
    class ProcessCandidates {

        @Test
        @DisplayName("maxFileSizeを超えるファイルを除外する")
        void skipsFileLargerThanMaxFileSize(@TempDir Path tempDir) throws IOException {
            Path largeFile = tempDir.resolve("Large.java");
            Files.writeString(largeFile, "0123456789ABCDEF");

            var reader = new LocalFileReader(
                tempDir,
                tempDir.toRealPath(),
                selectionConfig(8, 1_024),
                LoggerFactory.getLogger(LocalFileReader.class)
            );

            var consumed = new ArrayList<String>();
            var result = reader.processCandidates(
                List.of(new LocalFileCandidateCollector.Candidate(largeFile, Files.size(largeFile))),
                (relativePath, content, sizeBytes) -> consumed.add(relativePath)
            );

            Assertions.assertThat(result.fileCount()).isZero();
            Assertions.assertThat(result.totalSize()).isZero();
            Assertions.assertThat(consumed).isEmpty();
        }

        @Test
        @DisplayName("maxTotalSizeを超える候補で処理を停止する")
        void stopsProcessingWhenTotalBudgetExceeded(@TempDir Path tempDir) throws IOException {
            Path first = tempDir.resolve("A.java");
            Path second = tempDir.resolve("B.java");
            Files.writeString(first, "12345678");
            Files.writeString(second, "ABCDEFGH");

            var reader = new LocalFileReader(
                tempDir,
                tempDir.toRealPath(),
                selectionConfig(100, 10),
                LoggerFactory.getLogger(LocalFileReader.class)
            );

            var consumed = new ArrayList<String>();
            var result = reader.processCandidates(
                List.of(
                    new LocalFileCandidateCollector.Candidate(first, Files.size(first)),
                    new LocalFileCandidateCollector.Candidate(second, Files.size(second))
                ),
                (relativePath, content, sizeBytes) -> consumed.add(relativePath)
            );

            Assertions.assertThat(result.fileCount()).isEqualTo(1);
            Assertions.assertThat(result.totalSize()).isEqualTo(Files.size(first));
            Assertions.assertThat(consumed).containsExactly("A.java");
        }

        @Test
        @DisplayName("24件以上の候補は並列ルートで処理される")
        void processesCandidatesInParallelPath(@TempDir Path tempDir) throws IOException {
            var candidates = new ArrayList<LocalFileCandidateCollector.Candidate>();
            for (int i = 0; i < 24; i++) {
                Path file = tempDir.resolve("File" + i + ".java");
                Files.writeString(file, "x");
                candidates.add(new LocalFileCandidateCollector.Candidate(file, Files.size(file)));
            }

            var reader = new LocalFileReader(
                tempDir,
                tempDir.toRealPath(),
                selectionConfig(100, 1_024),
                LoggerFactory.getLogger(LocalFileReader.class)
            );

            var consumed = new ArrayList<String>();
            var result = reader.processCandidates(
                candidates,
                (relativePath, content, sizeBytes) -> consumed.add(relativePath)
            );

            Assertions.assertThat(result.fileCount()).isEqualTo(24);
            Assertions.assertThat(result.totalSize()).isEqualTo(24);
            Assertions.assertThat(consumed).hasSize(24);
        }

        @Test
        @DisplayName("ベース外の候補ファイルは除外する")
        void skipsCandidateOutsideBaseDirectory(@TempDir Path tempDir) throws IOException {
            Path outsideFile = Files.createTempFile("outside", ".java");
            Files.writeString(outsideFile, "class Outside {}");

            var reader = new LocalFileReader(
                tempDir,
                tempDir.toRealPath(),
                selectionConfig(100, 1_024),
                LoggerFactory.getLogger(LocalFileReader.class)
            );

            var consumed = new ArrayList<String>();
            var result = reader.processCandidates(
                List.of(new LocalFileCandidateCollector.Candidate(outsideFile, Files.size(outsideFile))),
                (relativePath, content, sizeBytes) -> consumed.add(relativePath)
            );

            Assertions.assertThat(result.fileCount()).isZero();
            Assertions.assertThat(result.totalSize()).isZero();
            Assertions.assertThat(consumed).isEmpty();

            Files.deleteIfExists(outsideFile);
        }
    }

    private static LocalFileSelectionConfig selectionConfig(long maxFileSize, long maxTotalSize) {
        return new LocalFileSelectionConfig(
            maxFileSize,
            maxTotalSize,
            Set.of("node_modules"),
            Set.of("java", "md", "txt"),
            Set.of("secret", "password", "token", "key"),
            Set.of("pem", "p12", "jks")
        );
    }
}
