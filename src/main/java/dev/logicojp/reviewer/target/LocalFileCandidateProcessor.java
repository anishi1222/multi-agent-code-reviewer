package dev.logicojp.reviewer.target;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class LocalFileCandidateProcessor {

    @FunctionalInterface
    interface FileConsumer {
        void accept(String relativePath, String content, long sizeBytes);
    }

    record ProcessingResult(long totalSize, int fileCount) {
    }

    private static final Logger logger = LoggerFactory.getLogger(LocalFileCandidateProcessor.class);

    private final Path baseDirectory;
    private final Path realBaseDirectory;
    private final long maxFileSize;
    private final long maxTotalSize;

    LocalFileCandidateProcessor(Path baseDirectory,
                                Path realBaseDirectory,
                                long maxFileSize,
                                long maxTotalSize) {
        this.baseDirectory = baseDirectory;
        this.realBaseDirectory = realBaseDirectory;
        this.maxFileSize = maxFileSize;
        this.maxTotalSize = maxTotalSize;
    }

    ProcessingResult process(List<LocalFileCandidate> candidates, FileConsumer consumer) {
        long totalSize = 0;
        int fileCount = 0;
        byte[] readBuffer = new byte[8192];

        for (LocalFileCandidate candidate : candidates) {
            ProcessedCandidate processed = processCandidate(candidate, totalSize, readBuffer);
            if (processed.stopProcessing()) {
                break;
            }
            if (!processed.included()) {
                continue;
            }
            consumer.accept(processed.relativePath(), processed.content(), processed.size());
            totalSize += processed.size();
            fileCount++;
        }

        return new ProcessingResult(totalSize, fileCount);
    }

    private ProcessedCandidate processCandidate(LocalFileCandidate candidate,
                                                long totalSize,
                                                byte[] readBuffer) {
        Path path = candidate.path();
        long size = candidate.size();
        if (isTooLarge(size)) {
            logSkippedLargeFile(path, size);
            return ProcessedCandidate.skip();
        }
        if (wouldExceedTotalSize(totalSize, size)) {
            logTotalSizeLimitReached(totalSize);
            return ProcessedCandidate.stop();
        }

        try {
            if (Files.isSymbolicLink(path)) {
                logger.warn("File became symbolic link after collection, skipping: {}", path);
                return ProcessedCandidate.skip();
            }

            Path realPath = path.toRealPath();
            if (!realPath.startsWith(realBaseDirectory)) {
                logger.warn("File escaped base directory (possible race), skipping: {}", path);
                return ProcessedCandidate.skip();
            }

            long remainingBudget = maxTotalSize - totalSize;
            long readLimit = Math.min(maxFileSize, remainingBudget);
            if (readLimit <= 0) {
                logTotalSizeLimitReached(totalSize);
                return ProcessedCandidate.stop();
            }

            ReadResult readResult = readUtf8WithLimit(realPath, readLimit, size, readBuffer);
            if (readResult.exceededLimit()) {
                if (maxFileSize <= remainingBudget) {
                    logger.warn("File size exceeded limit during read (possible race), skipping: {}", path);
                    return ProcessedCandidate.skip();
                }
                logTotalSizeLimitReached(totalSize);
                return ProcessedCandidate.stop();
            }

            String content = readResult.content();
            String relativePath = toRelativePath(path);
            return ProcessedCandidate.included(relativePath, content, readResult.sizeBytes());
        } catch (IOException e) {
            logger.warn("Failed to read file {}: {}", candidate.path(), e.getMessage(), e);
            return ProcessedCandidate.skip();
        }
    }

    private ReadResult readUtf8WithLimit(Path path,
                                         long maxBytes,
                                         long expectedSize,
                                         byte[] readBuffer) throws IOException {
        // Most candidates already pass the size guard before this method is called.
        // In that common case, avoid ByteArrayOutputStream re-allocation/copy churn.
        if (expectedSize <= maxBytes) {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            long actualSize = Files.size(path);
            if (actualSize > maxBytes) {
                return ReadResult.exceeded();
            }
            return ReadResult.included(content, actualSize);
        }

        int initialCapacity = (int) Math.min(expectedSize, maxBytes);
        try (InputStream inputStream = Files.newInputStream(path);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream(Math.max(initialCapacity, 32))) {
            long totalRead = 0;
            int read;
            while ((read = inputStream.read(readBuffer)) != -1) {
                totalRead += read;
                if (totalRead > maxBytes) {
                    return ReadResult.exceeded();
                }
                outputStream.write(readBuffer, 0, read);
            }

            String content = outputStream.toString(StandardCharsets.UTF_8);
            return ReadResult.included(content, totalRead);
        }
    }

    private String toRelativePath(Path path) {
        return baseDirectory.relativize(path).toString().replace('\\', '/');
    }

    private boolean isTooLarge(long size) {
        return size > maxFileSize;
    }

    private boolean wouldExceedTotalSize(long totalSize, long fileSize) {
        return totalSize + fileSize > maxTotalSize;
    }

    private void logSkippedLargeFile(Path path, long size) {
        logger.debug("Skipping large file ({} bytes): {}", size, path);
    }

    private void logTotalSizeLimitReached(long totalSize) {
        logger.warn("Total content size limit reached ({} bytes). Stopping collection.", totalSize);
    }

    private record ReadResult(boolean exceededLimit, String content, long sizeBytes) {
        private static ReadResult included(String content, long sizeBytes) {
            return new ReadResult(false, content, sizeBytes);
        }

        private static ReadResult exceeded() {
            return new ReadResult(true, null, 0);
        }
    }

    private record ProcessedCandidate(boolean included,
                                      boolean stopProcessing,
                                      String relativePath,
                                      String content,
                                      long size) {
        private static ProcessedCandidate included(String relativePath, String content, long size) {
            return new ProcessedCandidate(true, false, relativePath, content, size);
        }

        private static ProcessedCandidate skip() {
            return new ProcessedCandidate(false, false, null, null, 0);
        }

        private static ProcessedCandidate stop() {
            return new ProcessedCandidate(false, true, null, null, 0);
        }
    }
}
