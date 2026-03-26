package dev.logicojp.reviewer.report.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.UUID;

/// Shared file helpers for report generation.
public final class ReportFileUtils {

    private static final Set<PosixFilePermission> OWNER_DIRECTORY_PERMISSIONS =
        PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> OWNER_FILE_PERMISSIONS =
        PosixFilePermissions.fromString("rw-------");

    private ReportFileUtils() {
    }

    public static void ensureOutputDirectory(Path outputDirectory) throws IOException {
        if (isMissing(outputDirectory)) {
            createDirectories(outputDirectory);
        }
    }

    private static boolean isMissing(Path outputDirectory) {
        return !Files.exists(outputDirectory);
    }

    private static void createDirectories(Path outputDirectory) throws IOException {
        if (supportsPosix(outputDirectory)) {
            Files.createDirectories(outputDirectory,
                PosixFilePermissions.asFileAttribute(OWNER_DIRECTORY_PERMISSIONS));
            Files.setPosixFilePermissions(outputDirectory, OWNER_DIRECTORY_PERMISSIONS);
            return;
        }
        Files.createDirectories(outputDirectory);
    }

    public static void writeSecureString(Path filePath, String content) throws IOException {
        boolean posix = supportsPosix(filePath);
        Path tempDirectory = filePath.getParent() != null ? filePath.getParent() : Path.of(".");
        Path tempFilePath = createTempFile(tempDirectory, filePath.getFileName().toString(), posix);
        try {
            Files.writeString(tempFilePath, content);
            if (posix) {
                Files.setPosixFilePermissions(tempFilePath, OWNER_FILE_PERMISSIONS);
            }
            moveAtomically(tempFilePath, filePath);
            if (posix) {
                Files.setPosixFilePermissions(filePath, OWNER_FILE_PERMISSIONS);
            }
        } finally {
            Files.deleteIfExists(tempFilePath);
        }
    }

    private static Path createTempFile(Path directory, String baseName, boolean posix) throws IOException {
        String prefix = tempFilePrefix(baseName);
        if (posix && supportsPosix(directory)) {
            return Files.createTempFile(
                directory,
                prefix,
                ".tmp",
                PosixFilePermissions.asFileAttribute(OWNER_FILE_PERMISSIONS)
            );
        }
        return Files.createTempFile(directory, prefix, ".tmp");
    }

    private static String tempFilePrefix(String baseName) {
        String sanitized = (baseName == null || baseName.isBlank()) ? "report" : baseName;
        if (sanitized.length() >= 3) {
            return sanitized;
        }
        return (sanitized + "___").substring(0, 3);
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean supportsPosix(Path path) {
        Path target = path;
        if (!Files.exists(target)) {
            Path parent = path.getParent();
            if (parent != null && Files.exists(parent)) {
                target = parent;
            }
        }
        return Files.getFileAttributeView(target, PosixFileAttributeView.class) != null;
    }
}
