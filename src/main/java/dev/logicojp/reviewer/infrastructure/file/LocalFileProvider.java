package dev.logicojp.reviewer.infrastructure.file;

import dev.logicojp.reviewer.application.port.outbound.CollectLocalSourcePort;
import dev.logicojp.reviewer.domain.review.LocalFileCandidate;
import dev.logicojp.reviewer.domain.review.LocalFileSelectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Infrastructure implementation of {@link CollectLocalSourcePort}.
///
/// Collects source files from a local directory for code review,
/// walking the directory tree and filtering for source files.
///
/// No DI annotations — instantiated by the factory in infrastructure.copilot.
public class LocalFileProvider implements CollectLocalSourcePort {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileProvider.class);

    @Override
    public List<LocalFileCandidate> collect(Path directory, LocalFileSelectionConfig config) {
        if (directory == null || !directory.toFile().isDirectory()) {
            logger.warn("Local source directory not found or not a directory: {}", directory);
            return List.of();
        }
        try {
            Path realBaseDirectory = directory.toRealPath();
            LocalFileCandidateCollector collector =
                new LocalFileCandidateCollector(directory, realBaseDirectory, config);
            List<LocalFileCandidate> candidates = collector.collectCandidateFiles();
            logger.debug("Collected {} candidate files from {}", candidates.size(), directory);
            return candidates;
        } catch (IOException e) {
            logger.error("Failed to collect local files from {}: {}", directory, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public String formatContent(List<LocalFileCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "(no source files found)";
        }
        // We need a baseDirectory for relativePath computation. Derive from the first candidate.
        Path baseDirectory = deriveBaseDirectory(candidates);
        if (baseDirectory == null) {
            return "(no source files found)";
        }
        try {
            Path realBaseDirectory = baseDirectory.toRealPath();
            long maxTotalSize = 2L * 1024 * 1024; // 2 MB default
            long maxFileSize = 256L * 1024;        // 256 KB default
            LocalFileCandidateProcessor processor =
                new LocalFileCandidateProcessor(baseDirectory, realBaseDirectory, maxFileSize, maxTotalSize);
            LocalFileContentFormatter formatter = new LocalFileContentFormatter(maxTotalSize);
            int capacity = formatter.estimateReviewContentCapacity(candidates);
            StringBuilder sb = new StringBuilder(capacity);
            processor.process(candidates, (relPath, content, _) -> formatter.appendFileBlock(sb, relPath, content));
            return sb.toString();
        } catch (IOException e) {
            logger.error("Failed to format local file content: {}", e.getMessage(), e);
            return "(error reading source files)";
        }
    }

    /// Collects and formats in one shot from a directory with explicit config.
    public CollectionResult collectAndFormat(Path directory, LocalFileSelectionConfig config) {
        List<LocalFileCandidate> candidates = collect(directory, config);
        if (candidates.isEmpty()) {
            return new CollectionResult("(no source files found)", noSourceFilesSummary(directory), 0, 0);
        }
        try {
            Path realBaseDirectory = directory.toRealPath();
            LocalFileCandidateProcessor processor = new LocalFileCandidateProcessor(
                directory, realBaseDirectory, config.maxFileSize(), config.maxTotalSize());
            LocalFileContentFormatter formatter = new LocalFileContentFormatter(config.maxTotalSize());

            List<LocalFile> files = new ArrayList<>();
            processor.process(candidates, (relPath, content, size) ->
                files.add(new LocalFile(relPath, content, size)));

            int capacity = formatter.estimateReviewContentCapacity(candidates);
            StringBuilder sb = new StringBuilder(capacity);
            long totalSize = 0;
            for (LocalFile file : files) {
                formatter.appendFileBlock(sb, file.relativePath(), file.content());
                totalSize += file.sizeBytes();
            }
            String summary = buildDirectorySummary(directory, files, totalSize);
            return new CollectionResult(sb.toString(), summary, files.size(), totalSize);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path deriveBaseDirectory(List<LocalFileCandidate> candidates) {
        // Try to find common ancestor directory
        Path first = candidates.get(0).path().getParent();
        return first;
    }

    private String buildDirectorySummary(Path baseDirectory, List<LocalFile> files, long totalSize) {
        if (files.isEmpty()) return noSourceFilesSummary(baseDirectory);
        StringBuilder sb = new StringBuilder();
        sb.append("Directory: ").append(baseDirectory).append("\n");
        sb.append("Files: ").append(files.size()).append("\n");
        sb.append("Total size: ").append(totalSize).append(" bytes\n\nFile list:\n");
        for (LocalFile file : files) {
            sb.append("  - ").append(file.relativePath())
              .append(" (").append(file.sizeBytes()).append(" bytes)\n");
        }
        return sb.toString();
    }

    private String noSourceFilesSummary(Path directory) {
        return "No source files found in: " + directory;
    }

    /// A single collected source file.
    public record LocalFile(String relativePath, String content, long sizeBytes) {}

    /// Combined local-source collection result.
    public record CollectionResult(String reviewContent, String directorySummary, int fileCount, long totalSizeBytes) {}
}
