package dev.logicojp.reviewer.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/// Resolves executable paths from explicit environment variable values
/// or by scanning the system PATH.
public final class CliPathResolver {

    private static final List<Path> TRUSTED_DIRECTORIES = List.of(
        Path.of("/usr/bin"),
        Path.of("/usr/local/bin"),
        Path.of("/usr/local/Cellar"),
        Path.of("/usr/local/Caskroom"),
        Path.of("/bin"),
        Path.of("/opt/homebrew/bin"),
        Path.of("/opt/homebrew/Cellar"),
        Path.of("/opt/homebrew/Caskroom")
    );

    private CliPathResolver() {
    }

    public static Optional<Path> resolveExplicitExecutable(String envValue, String... allowedNames) {
        if (envValue == null || envValue.isBlank()) {
            return Optional.empty();
        }

        Path explicitPath = Path.of(envValue.trim()).toAbsolutePath().normalize();
        if (!Files.isExecutable(explicitPath)) {
            return Optional.empty();
        }

        if (!hasAllowedName(explicitPath, allowedNames)) {
            return Optional.empty();
        }

        try {
            Path realPath = explicitPath.toRealPath();
            if (!hasAllowedName(realPath, allowedNames)) {
                return Optional.empty();
            }
            return Optional.of(realPath);
        } catch (IOException | SecurityException _) {
            return Optional.empty();
        }
    }

    public static Optional<Path> findExecutableInPath(String... candidateNames) {
        String pathEnv = System.getenv("PATH");
        return findExecutableInPathValue(pathEnv, candidateNames);
    }

    public static Optional<Path> findTrustedExecutableInPath(String... candidateNames) {
        String pathEnv = System.getenv("PATH");
        return findTrustedExecutableInPathValue(pathEnv, candidateNames);
    }

    public static Optional<Path> findExecutableInPathValue(String pathEnv, String... candidateNames) {
        return findExecutableInPathValue(pathEnv, false, candidateNames);
    }

    public static Optional<Path> findTrustedExecutableInPathValue(String pathEnv, String... candidateNames) {
        return findExecutableInPathValue(pathEnv, true, candidateNames);
    }

    private static Optional<Path> findExecutableInPathValue(String pathEnv,
                                                            boolean requireTrustedDirectory,
                                                            String... candidateNames) {
        if (pathEnv == null || pathEnv.isBlank()) {
            return Optional.empty();
        }

        for (String entry : pathEnv.split(File.pathSeparator)) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Path base = Path.of(entry.trim());
            for (String name : candidateNames) {
                Path candidate = base.resolve(name);
                if (Files.isExecutable(candidate)) {
                    try {
                        Path realPath = candidate.toRealPath();
                        if (hasAllowedName(realPath, candidateNames)
                            && (!requireTrustedDirectory || isInTrustedDirectory(realPath))) {
                            return Optional.of(realPath);
                        }
                    } catch (IOException | SecurityException _) {
                        // Ignore invalid candidate and continue searching PATH
                    }
                }
            }
        }

        return Optional.empty();
    }

    /// Revalidates a previously resolved executable path immediately before process execution.
    /// This reduces TOCTOU exposure by ensuring the current real path still matches the
    /// path that was validated earlier and still has an allowed executable name.
    public static Optional<Path> revalidateExecutionPath(String validatedPath, String... allowedNames) {
        if (validatedPath == null || validatedPath.isBlank()) {
            return Optional.empty();
        }
        Path normalized = Path.of(validatedPath.trim()).toAbsolutePath().normalize();
        if (!Files.isExecutable(normalized)) {
            return Optional.empty();
        }
        try {
            Path realPath = normalized.toRealPath();
            if (!hasAllowedName(realPath, allowedNames)) {
                return Optional.empty();
            }
            if (!realPath.equals(normalized)) {
                return Optional.empty();
            }
            return Optional.of(realPath);
        } catch (IOException | SecurityException _) {
            return Optional.empty();
        }
    }

    public static boolean isInTrustedDirectory(Path executablePath) {
        if (executablePath == null) {
            return false;
        }
        Path parent = executablePath.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            return false;
        }
        return TRUSTED_DIRECTORIES.stream().anyMatch(parent::startsWith);
    }

    private static boolean hasAllowedName(Path path, String... allowedNames) {
        String fileName = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return Arrays.stream(allowedNames).anyMatch(name -> {
            String lowerName = name.toLowerCase(java.util.Locale.ROOT);
            return fileName.equals(lowerName)
                || fileName.equals(lowerName + ".exe")
                || fileName.equals(lowerName + ".cmd");
        });
    }
}
