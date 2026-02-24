package dev.logicojp.reviewer.target;

import org.slf4j.Logger;

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

/// Collects candidate source files from a directory tree.
final class LocalFileCandidateCollector {

    record Candidate(Path path, long size) {}

    private final Path baseDirectory;
    private final Path realBaseDirectory;
    private final LocalFileSelectionConfig selectionConfig;
    private final Logger logger;

    LocalFileCandidateCollector(Path baseDirectory,
                                Path realBaseDirectory,
                                LocalFileSelectionConfig selectionConfig,
                                Logger logger) {
        this.baseDirectory = baseDirectory;
        this.realBaseDirectory = realBaseDirectory;
        this.selectionConfig = selectionConfig;
        this.logger = logger;
    }

    List<Candidate> collectCandidates() throws IOException {
        List<Candidate> candidates = new ArrayList<>();
        Files.walkFileTree(baseDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(baseDirectory)
                    && selectionConfig.ignoredDirectories()
                    .contains(dir.getFileName().toString().toLowerCase(Locale.ROOT))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isCollectableCandidate(file, attrs)) {
                    candidates.add(new Candidate(file, attrs.size()));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        candidates.sort(Comparator.comparing(Candidate::path));
        return candidates;
    }

    private boolean isCollectableCandidate(Path file, BasicFileAttributes attrs) {
        if (!attrs.isRegularFile() || attrs.isSymbolicLink()) return false;
        if (!isWithinBaseDirectory(file, attrs)) return false;
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return isSourceFile(fileName) && isNotSensitiveFile(fileName);
    }

    private boolean isSourceFile(String fileName) {
        if (isSpecialSourceFilename(fileName)) return true;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) return false;
        String ext = fileName.substring(dotIndex + 1);
        return selectionConfig.sourceExtensions().contains(ext);
    }

    private boolean isSpecialSourceFilename(String fileName) {
        return fileName.equals("makefile") || fileName.equals("dockerfile")
            || fileName.equals("rakefile") || fileName.equals("gemfile");
    }

    private boolean isWithinBaseDirectory(Path path, BasicFileAttributes attrs) {
        if (!attrs.isSymbolicLink()) return true;
        try {
            Path realPath = path.toRealPath();
            return realPath.startsWith(realBaseDirectory);
        } catch (IOException e) {
            logger.debug("Cannot resolve real path for {}: {}", path, e.getMessage());
            return false;
        }
    }

    private boolean isNotSensitiveFile(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0) {
            String ext = fileName.substring(dotIndex + 1);
            if (selectionConfig.sensitiveExtensions().contains(ext)) return false;
        }
        for (String pattern : selectionConfig.sensitiveFilePatterns()) {
            if (fileName.contains(pattern)) return false;
        }
        return true;
    }
}
