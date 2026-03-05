package dev.logicojp.reviewer.target;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalFileCandidateProcessor")
class LocalFileCandidateProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("maxFileSize を超えるファイルをスキップする")
    void skipsFilesExceedingMaxFileSize() throws IOException {
        Path small = tempDir.resolve("small.java");
        Path large = tempDir.resolve("large.java");
        Files.writeString(small, "class Small {}\n");
        Files.writeString(large, "class Large { String v = \"0123456789\"; }\n");

        var candidates = List.of(
            new LocalFileCandidate(large, Files.size(large)),
            new LocalFileCandidate(small, Files.size(small))
        );

        var processor = new LocalFileCandidateProcessor(tempDir, tempDir.toRealPath(), 20, 10_000);
        List<String> processed = new ArrayList<>();

        LocalFileCandidateProcessor.ProcessingResult result = processor.process(
            candidates,
            (relativePath, content, sizeBytes) -> processed.add(relativePath)
        );

        assertThat(processed).containsExactly("small.java");
        assertThat(result.fileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("maxTotalSize 到達時に処理を停止する")
    void stopsWhenMaxTotalSizeReached() throws IOException {
        Path first = tempDir.resolve("a.java");
        Path second = tempDir.resolve("b.java");
        Files.writeString(first, "1234567890\n");
        Files.writeString(second, "1234567890\n");

        long firstSize = Files.size(first);
        long secondSize = Files.size(second);
        var candidates = List.of(
            new LocalFileCandidate(first, firstSize),
            new LocalFileCandidate(second, secondSize)
        );

        var processor = new LocalFileCandidateProcessor(tempDir, tempDir.toRealPath(), 10_000, firstSize);
        List<String> processed = new ArrayList<>();

        LocalFileCandidateProcessor.ProcessingResult result = processor.process(
            candidates,
            (relativePath, content, sizeBytes) -> processed.add(relativePath)
        );

        assertThat(processed).containsExactly("a.java");
        assertThat(result.fileCount()).isEqualTo(1);
        assertThat(result.totalSize()).isEqualTo(firstSize);
    }

    @Test
    @DisplayName("収集時サイズが過小でも読み取り時の実サイズでmaxFileSize超過を検出する")
    void skipsWhenActualSizeExceedsLimitAtReadTime() throws IOException {
        Path target = tempDir.resolve("target.java");
        Files.writeString(target, "012345678901234567890123456789");

        var candidates = List.of(
            new LocalFileCandidate(target, 10)
        );

        var processor = new LocalFileCandidateProcessor(tempDir, tempDir.toRealPath(), 20, 10_000);
        List<String> processed = new ArrayList<>();

        LocalFileCandidateProcessor.ProcessingResult result = processor.process(
            candidates,
            (relativePath, content, sizeBytes) -> processed.add(relativePath)
        );

        assertThat(processed).isEmpty();
        assertThat(result.fileCount()).isZero();
        assertThat(result.totalSize()).isZero();
    }

    @Test
    @DisplayName("UTF-8マルチバイト文字でもバイト数基準でサイズ計上する")
    void countsUtf8BytesNotCharacters() throws IOException {
        Path target = tempDir.resolve("utf8.java");
        String content = "あいうえお\n";
        Files.writeString(target, content);

        long bytes = Files.size(target);
        var candidates = List.of(new LocalFileCandidate(target, bytes));

        var processor = new LocalFileCandidateProcessor(tempDir, tempDir.toRealPath(), 64, 64);
        List<Long> processedSizes = new ArrayList<>();

        LocalFileCandidateProcessor.ProcessingResult result = processor.process(
            candidates,
            (relativePath, fileContent, sizeBytes) -> processedSizes.add(sizeBytes)
        );

        assertThat(result.fileCount()).isEqualTo(1);
        assertThat(result.totalSize()).isEqualTo(bytes);
        assertThat(processedSizes).containsExactly(bytes);
    }
}
