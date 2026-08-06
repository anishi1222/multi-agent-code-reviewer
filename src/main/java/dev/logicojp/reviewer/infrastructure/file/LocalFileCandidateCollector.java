package dev.logicojp.reviewer.infrastructure.file;

import dev.logicojp.reviewer.domain.review.LocalFileCandidate;
import dev.logicojp.reviewer.domain.review.LocalFileSelectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/// Collects candidate source files from a local directory tree.
final class LocalFileCandidateCollector {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileCandidateCollector.class);

    private final Path baseDirectory;
    private final Path realBaseDirectory;
    private final LocalFileSelectionConfig config;

    LocalFileCandidateCollector(Path baseDirectory, Path realBaseDirectory, LocalFileSelectionConfig config) {
        this.baseDirectory = baseDirectory;
        this.realBaseDirectory = realBaseDirectory;
        this.config = config;
    }

    List<LocalFileCandidate> collectCandidateFiles() throws IOException {
        List<LocalFileCandidate> candidates = new ArrayList<>();
        Files.walkFileTree(baseDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(baseDirectory)
                    && config.ignoredDirectories().contains(dir.getFileName().toString().toLowerCase(Locale.ROOT))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isCollectableCandidate(file, attrs)) {
                    candidates.add(new LocalFileCandidate(file, attrs.size()));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        candidates.sort(Comparator.comparing(LocalFileCandidate::path));
        return candidates;
    }

    private boolean isCollectableCandidate(Path file, BasicFileAttributes attrs) {
        if (!attrs.isRegularFile() || attrs.isSymbolicLink()) return false;
        if (!isWithinBaseDirectory(file, attrs)) return false;
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return isSourceFile(fileName) && isNotSensitiveFile(fileName);
    }

    private boolean isSourceFile(String fileName) {
        if (fileName.equals("makefile") || fileName.equals("dockerfile")
            || fileName.equals("rakefile") || fileName.equals("gemfile")) return true;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) return false;
        String ext = fileName.substring(dotIndex + 1);
        return config.sourceExtensions().contains(ext);
    }

    private boolean isNotSensitiveFile(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && config.sensitiveExtensions().contains(fileName.substring(dotIndex + 1))) return false;
        for (String pattern : config.sensitiveFilePatterns()) {
            if (fileName.contains(pattern)) return false;
        }
        return true;
    }

    private boolean isWithinBaseDirectory(Path path, BasicFileAttributes attrs) {
        if (!attrs.isSymbolicLink()) return true;
        try {
            return path.toRealPath().startsWith(realBaseDirectory);
        } catch (IOException e) {
            logger.debug("Cannot resolve real path for {}: {}", path, e.getMessage());
            return false;
        }
    }
}
