package dev.logicojp.reviewer.domain.review;

import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/// Represents the target to review — either a GitHub repository or a local directory.
///
/// Sealed interface that enables exhaustive pattern matching:
/// <pre>{@code
/// return switch (target) {
///     case ReviewTarget.LocalTarget(Path directory) -> handleLocal(directory);
///     case ReviewTarget.GitHubTarget(String repository) -> handleGitHub(repository);
/// };
/// }</pre>
public sealed interface ReviewTarget permits ReviewTarget.LocalTarget, ReviewTarget.GitHubTarget {

    Pattern REPOSITORY_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+$");
    String LOCAL_ROOT_SUBPATH = "local-root";

    /// A local directory target.
    record LocalTarget(Path directory) implements ReviewTarget {}

    /// A GitHub repository target.
    record GitHubTarget(String repository) implements ReviewTarget {}

    /// Creates a GitHub repository target.
    static ReviewTarget gitHub(String repository) {
        if (repository == null || repository.isBlank()) {
            throw new IllegalArgumentException("Repository must not be null or blank");
        }
        if (!REPOSITORY_PATTERN.matcher(repository).matches()) {
            throw new IllegalArgumentException(
                "Invalid repository format: " + repository + ". Expected 'owner/repo' format.");
        }
        String[] segments = repository.split("/", 2);
        if (segments.length != 2 || isTraversalSegment(segments[0]) || isTraversalSegment(segments[1])) {
            throw new IllegalArgumentException("Repository name contains path traversal segment: " + repository);
        }
        return new GitHubTarget(repository);
    }

    /// Creates a local directory target.
    static ReviewTarget local(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Directory must not be null");
        }
        return new LocalTarget(directory);
    }

    /// Returns a human-readable display name for the target.
    default String displayName() {
        return switch (this) {
            case GitHubTarget(String repository) -> repository;
            case LocalTarget(Path directory) -> directory.getFileName() != null
                ? directory.getFileName().toString()
                : directory.toString();
        };
    }

    /// Returns {@code true} if this target is a local directory.
    default boolean isLocal() {
        return switch (this) {
            case LocalTarget _ -> true;
            case GitHubTarget _ -> false;
        };
    }

    /// Returns the local path if this is a local target, empty otherwise.
    default Optional<Path> localPath() {
        return switch (this) {
            case LocalTarget(Path directory) -> Optional.of(directory);
            case GitHubTarget(_) -> Optional.empty();
        };
    }

    /// Returns the sub-path to use within the output directory for this target.
    default Path repositorySubPath() {
        return switch (this) {
            case GitHubTarget(String repository) -> {
                Path subPath = Path.of(repository).normalize();
                if (subPath.isAbsolute() || subPath.startsWith("..")) {
                    throw new IllegalArgumentException("Repository name contains path traversal: " + repository);
                }
                yield subPath;
            }
            case LocalTarget(Path directory) -> {
                Path normalizedDirectory = directory.normalize();
                Path fileName = normalizedDirectory.getFileName();
                yield fileName != null ? Path.of(fileName.toString()) : Path.of(LOCAL_ROOT_SUBPATH);
            }
        };
    }

    private static boolean isTraversalSegment(String segment) {
        return ".".equals(segment) || "..".equals(segment);
    }
}
