package dev.logicojp.reviewer.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/// Manages checkpoint retention after a review run.
final class CheckpointLifecycleManager {

    private static final Logger logger = LoggerFactory.getLogger(CheckpointLifecycleManager.class);
    private static final DateTimeFormatter CHECKPOINT_TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    void handle(Path checkpointDir, boolean keepCheckpoints, LocalDateTime invocationTime) {
        Path normalizedDir = checkpointDir.normalize();
        if (!Files.isDirectory(normalizedDir)) return;

        if (keepCheckpoints) {
            archiveCheckpoints(normalizedDir, invocationTime);
        } else {
            deleteCheckpointFiles(normalizedDir);
        }
    }

    private void archiveCheckpoints(Path checkpointDir, LocalDateTime invocationTime) {
        String timestamp = invocationTime.format(CHECKPOINT_TIMESTAMP_FORMATTER);
        Path archiveDir = checkpointDir.resolve(timestamp);
        try {
            Files.createDirectories(archiveDir);
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(checkpointDir, Files::isRegularFile)) {
                for (Path file : entries) {
                    Files.move(file, archiveDir.resolve(file.getFileName()));
                }
            }
            logger.info("Checkpoints archived to {}", archiveDir);
        } catch (IOException e) {
            logger.warn("Failed to archive checkpoints: {}", e.getMessage());
        }
    }

    private void deleteCheckpointFiles(Path checkpointDir) {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(checkpointDir, Files::isRegularFile)) {
            for (Path file : entries) {
                Files.deleteIfExists(file);
            }
            logger.debug("Checkpoint files deleted from {}", checkpointDir);
        } catch (IOException e) {
            logger.warn("Failed to delete checkpoint files: {}", e.getMessage());
        }
    }
}
