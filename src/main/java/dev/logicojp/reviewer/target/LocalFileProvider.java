package dev.logicojp.reviewer.target;

import dev.logicojp.reviewer.config.ReviewerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// Collects, filters, reads, and formats local source files for review.
/// Delegates directory walking, file reading, and markdown formatting to
/// dedicated collaborators for improved separation of concerns.
public class LocalFileProvider {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileProvider.class);

    // --- Public records ---

    public record LocalFile(String relativePath, String content, long sizeBytes) {}

    public record CollectionResult(String reviewContent,
                                   String directorySummary,
                                   int fileCount,
                                   long totalSizeBytes) {}

    // --- Fields ---

    private final Path baseDirectory;
    private final Path realBaseDirectory;
    private final LocalFileSelectionConfig selectionConfig;
    private final LocalFileCandidateCollector candidateCollector;
    private final LocalFileContentReader contentReader;
    private final LocalFileContentFormatter contentFormatter;

    // --- Constructors ---

    public LocalFileProvider(Path baseDirectory) {
        this(baseDirectory, new ReviewerConfig.LocalFiles());
    }

    public LocalFileProvider(Path baseDirectory, ReviewerConfig.LocalFiles config) {
        if (baseDirectory == null) {
            throw new IllegalArgumentException("Base directory must not be null");
        }
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
        try {
            this.realBaseDirectory = this.baseDirectory.toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot resolve real path for base directory: " + baseDirectory, e);
        }
        this.selectionConfig = LocalFileSelectionConfig.from(config);
        this.candidateCollector = new LocalFileCandidateCollector(
            this.baseDirectory, this.realBaseDirectory, this.selectionConfig, logger);
        this.contentReader = new LocalFileContentReader(
            this.baseDirectory, this.realBaseDirectory, this.selectionConfig, logger);
        this.contentFormatter = new LocalFileContentFormatter(this.baseDirectory, this.selectionConfig);
    }

    // --- Public API ---

    List<LocalFile> collectFiles() {
        if (isMissingBaseDirectory()) { logMissingBaseDirectory(); return List.of(); }
        List<LocalFile> files = new ArrayList<>();
        try {
            List<LocalFileCandidateCollector.Candidate> candidates = candidateCollector.collectCandidates();
            LocalFileContentReader.ProcessingResult result = contentReader.processCandidates(candidates,
                (relativePath, content, size) -> files.add(new LocalFile(relativePath, content, size)));
            logCollectedFiles(result.fileCount(), result.totalSize());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk directory: " + baseDirectory, e);
        }
        return List.copyOf(files);
    }

    public CollectionResult collectAndGenerate() {
        if (isMissingBaseDirectory()) {
            logMissingBaseDirectory();
            return new CollectionResult("(no source files found)", noSourceFilesSummary(), 0, 0);
        }
        try {
            List<LocalFileCandidateCollector.Candidate> candidates = candidateCollector.collectCandidates();
            int reviewCapacity = contentFormatter.estimateReviewContentCapacity(candidates);
            var reviewContentBuilder = new StringBuilder(reviewCapacity);
            var fileListBuilder = new StringBuilder();
            LocalFileContentReader.ProcessingResult result = contentReader.processCandidates(candidates,
                (relativePath, content, size) -> {
                    contentFormatter.appendFileBlock(reviewContentBuilder, relativePath, content);
                    fileListBuilder.append("  - ").append(relativePath)
                        .append(" (").append(size).append(" bytes)\n");
                });
            long totalSize = result.totalSize();
            int fileCount = result.fileCount();
            logCollectedFiles(fileCount, totalSize);
            String reviewContent = fileCount == 0 ? "(no source files found)" : reviewContentBuilder.toString();
            String directorySummary = contentFormatter.generateDirectorySummary(fileCount, totalSize, fileListBuilder);
            return new CollectionResult(reviewContent, directorySummary, fileCount, totalSize);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk directory: " + baseDirectory, e);
        }
    }

    String generateReviewContent(List<LocalFile> files) {
        return contentFormatter.generateReviewContent(files);
    }

    String generateDirectorySummary(List<LocalFile> files) {
        return contentFormatter.generateDirectorySummary(files);
    }

    private String noSourceFilesSummary() { return "No source files found in: " + baseDirectory; }

    // --- Utility ---

    private boolean isMissingBaseDirectory() { return !Files.isDirectory(baseDirectory); }
    private void logMissingBaseDirectory() { logger.warn("Base directory does not exist or is not a directory: {}", baseDirectory); }
    private void logCollectedFiles(int fileCount, long totalSize) { logger.info("Collected {} source files ({} bytes) from: {}", fileCount, totalSize, baseDirectory); }
}
