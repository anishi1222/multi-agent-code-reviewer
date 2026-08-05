package dev.logicojp.reviewer.infrastructure.file;

import dev.logicojp.reviewer.domain.review.LocalFileCandidate;
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

@DisplayName("LocalFileCandidateCollector")
class LocalFileCandidateCollectorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("無視ディレクトリとセンシティブファイルを除外して候補を収集する")
    void collectsCandidatesWithFilters() throws IOException {
        Files.writeString(tempDir.resolve("Main.java"), "class Main {}\n");
        Files.writeString(tempDir.resolve("README.md"), "# Readme\n");
        Files.writeString(tempDir.resolve(".env"), "SECRET=1\n");

        Path ignored = tempDir.resolve(".git");
        Files.createDirectories(ignored);
        Files.writeString(ignored.resolve("Ignored.java"), "class Ignored {}\n");

        LocalFileConfig config = new LocalFileConfig();
        LocalFileCandidateCollector collector = new LocalFileCandidateCollector(
            tempDir.toAbsolutePath().normalize(),
            tempDir.toRealPath(),
            selectionConfig(config)
        );

        List<LocalFileCandidate> candidates = collector.collectCandidateFiles();

        assertThat(candidates)
            .extracting(candidate -> tempDir.relativize(candidate.path()).toString().replace('\\', '/'))
            .containsExactly("Main.java", "README.md");
    }

    @Test
    @DisplayName("候補はパス昇順でソートされる")
    void sortsCandidatesByPath() throws IOException {
        Files.writeString(tempDir.resolve("b.java"), "class B {}\n");
        Files.writeString(tempDir.resolve("a.java"), "class A {}\n");

        LocalFileConfig config = new LocalFileConfig();
        LocalFileCandidateCollector collector = new LocalFileCandidateCollector(
            tempDir.toAbsolutePath().normalize(),
            tempDir.toRealPath(),
            selectionConfig(config)
        );

        List<LocalFileCandidate> candidates = collector.collectCandidateFiles();

        assertThat(candidates)
            .extracting(candidate -> tempDir.relativize(candidate.path()).toString().replace('\\', '/'))
            .containsExactly("a.java", "b.java");
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
