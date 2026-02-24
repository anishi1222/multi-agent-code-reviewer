package dev.logicojp.reviewer.target;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/// Reads candidate files with size budget enforcement and optional parallelism.
final class LocalFileContentReader {

    private static final int PARALLEL_PROCESSING_THRESHOLD = 24;

    @FunctionalInterface
    interface FileConsumer {
        void accept(String relativePath, String content, long sizeBytes);
    }

    record ProcessingResult(long totalSize, int fileCount) {}

    private record ReadResult(boolean exceededLimit, String content, long sizeBytes) {
        static ReadResult included(String content, long sizeBytes) {
            return new ReadResult(false, content, sizeBytes);
        }

        static ReadResult exceeded() {
            return new ReadResult(true, null, 0);
        }
    }

    private record ProcessedCandidate(boolean included, boolean stopProcessing,
                                      String relativePath, String content, long size) {
        static ProcessedCandidate included(String relativePath, String content, long size) {
            return new ProcessedCandidate(true, false, relativePath, content, size);
        }

        static ProcessedCandidate skip() {
            return new ProcessedCandidate(false, false, null, null, 0);
        }

        static ProcessedCandidate stop() {
            return new ProcessedCandidate(false, true, null, null, 0);
        }
    }

    private record CandidateSelection(List<LocalFileCandidateCollector.Candidate> candidates) {}

    private final Path baseDirectory;
    private final Path realBaseDirectory;
    private final LocalFileSelectionConfig selectionConfig;
    private final Logger logger;

    LocalFileContentReader(Path baseDirectory,
                           Path realBaseDirectory,
                           LocalFileSelectionConfig selectionConfig,
                           Logger logger) {
        this.baseDirectory = baseDirectory;
        this.realBaseDirectory = realBaseDirectory;
        this.selectionConfig = selectionConfig;
        this.logger = logger;
    }

    ProcessingResult processCandidates(List<LocalFileCandidateCollector.Candidate> candidates, FileConsumer consumer) {
        if (candidates.size() >= PARALLEL_PROCESSING_THRESHOLD) {
            return processCandidatesParallel(candidates, consumer);
        }
        return processCandidatesSequential(candidates, consumer);
    }

    private ProcessingResult processCandidatesSequential(List<LocalFileCandidateCollector.Candidate> candidates,
                                                         FileConsumer consumer) {
        long totalSize = 0;
        int fileCount = 0;
        for (LocalFileCandidateCollector.Candidate candidate : candidates) {
            ProcessedCandidate processed = processCandidate(candidate, totalSize);
            if (processed.stopProcessing()) break;
            if (!processed.included()) continue;
            consumer.accept(processed.relativePath(), processed.content(), processed.size());
            totalSize += processed.size();
            fileCount++;
        }
        return new ProcessingResult(totalSize, fileCount);
    }

    private ProcessingResult processCandidatesParallel(List<LocalFileCandidateCollector.Candidate> candidates,
                                                       FileConsumer consumer) {
        CandidateSelection selection = selectCandidatesWithinTotalBudget(candidates);
        List<LocalFileCandidateCollector.Candidate> selectedCandidates = selection.candidates();
        if (selectedCandidates.isEmpty()) {
            return new ProcessingResult(0, 0);
        }

        List<Future<ProcessedCandidate>> futures = new ArrayList<>(selectedCandidates.size());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (LocalFileCandidateCollector.Candidate candidate : selectedCandidates) {
                futures.add(executor.submit(() -> processCandidateWithoutTotalBudget(candidate)));
            }

            long totalSize = 0;
            int fileCount = 0;
            for (int i = 0; i < selectedCandidates.size(); i++) {
                var candidate = selectedCandidates.get(i);
                ProcessedCandidate processed = awaitProcessedCandidate(futures.get(i), candidate.path());
                if (processed == null || !processed.included()) {
                    continue;
                }
                if (totalSize + processed.size() > selectionConfig.maxTotalSize()) {
                    logger.warn("Total content size limit reached ({} bytes). Stopping collection.", totalSize);
                    break;
                }

                consumer.accept(processed.relativePath(), processed.content(), processed.size());
                totalSize += processed.size();
                fileCount++;
            }
            return new ProcessingResult(totalSize, fileCount);
        }
    }

    private CandidateSelection selectCandidatesWithinTotalBudget(List<LocalFileCandidateCollector.Candidate> candidates) {
        long selectedTotalSize = 0;
        List<LocalFileCandidateCollector.Candidate> selected = new ArrayList<>(candidates.size());

        for (LocalFileCandidateCollector.Candidate candidate : candidates) {
            long size = candidate.size();
            if (size > selectionConfig.maxFileSize()) {
                logger.debug("Skipping large file ({} bytes): {}", size, candidate.path());
                continue;
            }
            if (selectedTotalSize + size > selectionConfig.maxTotalSize()) {
                logger.warn("Total content size limit reached ({} bytes). Stopping collection.", selectedTotalSize);
                break;
            }
            selected.add(candidate);
            selectedTotalSize += size;
        }

        return new CandidateSelection(List.copyOf(selected));
    }

    private ProcessedCandidate awaitProcessedCandidate(Future<ProcessedCandidate> future, Path path) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while reading file {}", path);
            return null;
        } catch (ExecutionException e) {
            logger.warn("Failed to read file {}: {}", path, e.getMessage(), e);
            return null;
        }
    }

    private ProcessedCandidate processCandidate(LocalFileCandidateCollector.Candidate candidate, long totalSize) {
        Path path = candidate.path();
        long size = candidate.size();
        long maxFileSize = selectionConfig.maxFileSize();
        long maxTotalSize = selectionConfig.maxTotalSize();

        if (size > maxFileSize) {
            logger.debug("Skipping large file ({} bytes): {}", size, path);
            return ProcessedCandidate.skip();
        }
        if (totalSize + size > maxTotalSize) {
            logger.warn("Total content size limit reached ({} bytes). Stopping collection.", totalSize);
            return ProcessedCandidate.stop();
        }
        try {
            return processCandidateRead(path, size, maxFileSize, maxTotalSize - totalSize);
        } catch (IOException e) {
            logger.warn("Failed to read file {}: {}", candidate.path(), e.getMessage(), e);
            return ProcessedCandidate.skip();
        }
    }

    private ProcessedCandidate processCandidateWithoutTotalBudget(LocalFileCandidateCollector.Candidate candidate) {
        Path path = candidate.path();
        long size = candidate.size();
        long maxFileSize = selectionConfig.maxFileSize();

        if (size > maxFileSize) {
            logger.debug("Skipping large file ({} bytes): {}", size, path);
            return ProcessedCandidate.skip();
        }

        try {
            return processCandidateRead(path, size, maxFileSize, maxFileSize);
        } catch (IOException e) {
            logger.warn("Failed to read file {}: {}", candidate.path(), e.getMessage(), e);
            return ProcessedCandidate.skip();
        }
    }

    private ProcessedCandidate processCandidateRead(Path path,
                                                    long size,
                                                    long maxFileSize,
                                                    long readBudget) throws IOException {
        if (Files.isSymbolicLink(path)) {
            logger.warn("File became symbolic link after collection, skipping: {}", path);
            return ProcessedCandidate.skip();
        }
        Path realPath = path.toRealPath();
        if (!realPath.startsWith(realBaseDirectory)) {
            logger.warn("File escaped base directory (possible race), skipping: {}", path);
            return ProcessedCandidate.skip();
        }
        long readLimit = Math.min(maxFileSize, readBudget);
        if (readLimit <= 0) {
            logger.warn("Total content size limit reached. Stopping collection.");
            return ProcessedCandidate.stop();
        }
        ReadResult readResult = readUtf8WithLimit(realPath, readLimit, size);
        if (readResult.exceededLimit()) {
            if (readLimit == maxFileSize) {
                logger.warn("File size exceeded limit during read (possible race), skipping: {}", path);
                return ProcessedCandidate.skip();
            }
            logger.warn("Total content size limit reached. Stopping collection.");
            return ProcessedCandidate.stop();
        }
        String relativePath = baseDirectory.relativize(path).toString().replace('\\', '/');
        return ProcessedCandidate.included(relativePath, readResult.content(), readResult.sizeBytes());
    }

    private ReadResult readUtf8WithLimit(Path path, long maxBytes, long expectedSize) throws IOException {
        int initialCapacity = (int) Math.min(expectedSize, maxBytes);
        try (InputStream inputStream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buf = new byte[8192];
            byte[] result = new byte[Math.max(initialCapacity, 32)];
            int totalRead = 0;
            int read;
            while ((read = inputStream.read(buf)) != -1) {
                if (totalRead + read > maxBytes) return ReadResult.exceeded();
                if (totalRead + read > result.length) {
                    result = Arrays.copyOf(result, (int) Math.min(maxBytes, (long) result.length * 2));
                }
                System.arraycopy(buf, 0, result, totalRead, read);
                totalRead += read;
            }
            return ReadResult.included(new String(result, 0, totalRead, StandardCharsets.UTF_8), totalRead);
        }
    }
}
