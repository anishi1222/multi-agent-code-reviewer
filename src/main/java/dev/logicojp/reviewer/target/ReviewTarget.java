package dev.logicojp.reviewer.target;

import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/// Represents the target to review — either a GitHub repository or a local directory.
///
/// Uses Java 21+ sealed interface with record patterns for exhaustive pattern matching:
/// ```java
/// return switch (target) {
///     case ReviewTarget.LocalTarget(Path directory) -> handleLocal(directory);
///     case ReviewTarget.GitHubTarget(String repository) -> handleGitHub(repository);
/// };
/// ```
public sealed interface ReviewTarget permits ReviewTarget.LocalTarget, ReviewTarget.GitHubTarget {

    Pattern REPOSITORY_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+$");
    String LOCAL_ROOT_SUBPATH = "local-root";

    /// A local directory target.
    /// @param directory The absolute path to the local directory to review
    record LocalTarget(Path directory) implements ReviewTarget {}

    /// A GitHub repository target.
    /// @param repository The repository identifier in "owner/repo" format
    record GitHubTarget(String repository) implements ReviewTarget {}

    /// Creates a GitHub repository target.
    /// @param repository Repository in "owner/repo" format
    /// @return A new GitHubTarget
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
    /// @param directory Path to the local directory
    /// @return A new LocalTarget
    static ReviewTarget local(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Directory must not be null");
        }
        return new LocalTarget(directory);
    }

    /// Returns a human-readable display name for the target.
    /// For GitHub targets, returns the "owner/repo" string.
    /// For local targets, returns the directory name or path.
    default String displayName() {
        return switch (this) {
            case GitHubTarget(String repository) -> repository;
            case LocalTarget(Path directory) -> directory.getFileName() != null
                ? directory.getFileName().toString()
                : directory.toString();
        };
    }

    /// Returns true if this target is a local directory.
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
    /// For GitHub targets, returns "owner/repo" (e.g. "anishi1222/multi-agent-code-reviewer-java").
    /// For local targets, returns the directory name.
    /// @throws IllegalArgumentException if the repository name contains path traversal characters
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
