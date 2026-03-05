package dev.logicojp.reviewer.util;

import dev.logicojp.reviewer.config.ExecutionConfig;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/// Resolves a GitHub token from CLI options, environment, or gh auth.
@Singleton
public final class GitHubTokenResolver {

    private static final Logger logger = LoggerFactory.getLogger(GitHubTokenResolver.class);
    private static final String PLACEHOLDER = "${GITHUB_TOKEN}";
    private static final String STDIN_TOKEN_SENTINEL = "-";
    private static final int MAX_STDIN_TOKEN_BYTES = 256;
    private static final String GH_CLI_PATH_ENV = "GH_CLI_PATH";
    private static final String GITHUB_TOKEN_ENV = "GITHUB_TOKEN";
    private static final long DEFAULT_TIMEOUT_SECONDS = 10;
    private static final int GH_AUTH_MAX_ATTEMPTS = 3;
    private static final long GH_AUTH_BACKOFF_BASE_MS = 1_000L;
    private static final long GH_AUTH_BACKOFF_MAX_MS = 5_000L;
    private static final Path SAFE_WORKING_DIRECTORY =
        Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();

    private final long timeoutSeconds;
    private final String configuredGhCliPath;
    private final String configuredPath;

    GitHubTokenResolver(long timeoutSeconds) {
        this(timeoutSeconds, null, null);
    }

    GitHubTokenResolver(long timeoutSeconds, @Nullable String configuredGhCliPath, @Nullable String configuredPath) {
        this.timeoutSeconds = (timeoutSeconds <= 0) ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
        this.configuredGhCliPath = configuredGhCliPath;
        this.configuredPath = configuredPath;
    }

    @Inject
    public GitHubTokenResolver(ExecutionConfig executionConfig,
                               @Nullable @Value("${GH_CLI_PATH:}") String configuredGhCliPath,
                               @Nullable @Value("${PATH:}") String configuredPath) {
        this(executionConfig.ghAuthTimeoutSeconds(), configuredGhCliPath, configuredPath);
    }

    public GitHubTokenResolver(ExecutionConfig executionConfig) {
        this(executionConfig.ghAuthTimeoutSeconds(), System.getenv(GH_CLI_PATH_ENV), System.getenv("PATH"));
    }

    public Optional<String> resolve(@Nullable String providedToken) {
        String normalized = normalizeToken(providedToken);
        if (normalized != null) {
            return Optional.of(normalized);
        }

        return resolveFromGhAuth();
    }

    private @Nullable String normalizeToken(@Nullable String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (STDIN_TOKEN_SENTINEL.equals(trimmed)) {
            return readTokenFromStdin();
        }
        if (trimmed.isEmpty() || PLACEHOLDER.equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private @Nullable String readTokenFromStdin() {
        try {
            String token = TokenReadUtils.readTrimmedToken(
                () -> {
                    if (System.console() == null) {
                        return null;
                    }
                    return System.console().readPassword("GitHub Token: ");
                },
                System.in::readNBytes,
                MAX_STDIN_TOKEN_BYTES
            );
            // NOTE: The token String remains on the JVM heap until GC.
            // For production use, consider running with -XX:+DisableAttachMechanism
            // and -XX:-HeapDumpOnOutOfMemoryError to reduce heap dump exposure risk.
            return token;
        } catch (IOException e) {
            logger.warn("Failed to read token from stdin", e);
            return null;
        }
    }

    private Optional<String> resolveFromGhAuth() {
        String ghPath = resolveGhCliPath();
        if (ghPath == null) {
            logger.warn("gh CLI not found. Install GitHub CLI or set {}.", GH_CLI_PATH_ENV);
            return Optional.empty();
        }
        for (int attempt = 1; attempt <= GH_AUTH_MAX_ATTEMPTS; attempt++) {
            Optional<String> result = attemptResolveFromGhAuth(ghPath);
            if (result.isPresent()) {
                return result;
            }
            if (attempt < GH_AUTH_MAX_ATTEMPTS) {
                long backoffMs = RetryPolicyUtils.computeBackoffWithJitter(
                    GH_AUTH_BACKOFF_BASE_MS,
                    GH_AUTH_BACKOFF_MAX_MS,
                    attempt
                );
                logger.debug("gh auth token failed (attempt {}/{}), retrying in {}ms",
                    attempt, GH_AUTH_MAX_ATTEMPTS, backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted while backing off gh auth retry", e);
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> attemptResolveFromGhAuth(String ghPath) {
        ProcessBuilder builder = new ProcessBuilder(ghPath, "auth", "token", "-h", "github.com");
        builder.directory(SAFE_WORKING_DIRECTORY.toFile());
        // Avoid propagating parent-process token env to child processes.
        builder.environment().remove(GITHUB_TOKEN_ENV);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    logger.warn("gh auth token timed out after {} seconds", timeoutSeconds);
                    return Optional.empty();
                }
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    logger.warn("gh auth token failed with exit code {}", exitCode);
                    return Optional.empty();
                }
                if (line == null || line.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(line.trim());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while resolving token from gh auth", e);
            return Optional.empty();
        } catch (IOException e) {
            logger.warn("Failed to resolve token from gh auth", e);
            return Optional.empty();
        }
    }

    private String resolveGhCliPath() {
        String explicit = configuredGhCliPath;
        if (explicit != null && !explicit.isBlank()) {
            var explicitPath = CliPathResolver.resolveExplicitExecutable(explicit, "gh");
            if (explicitPath.isPresent()) {
                if (!CliPathResolver.isInTrustedDirectory(explicitPath.get())) {
                    logger.warn("Rejected {} outside trusted directories: {}", GH_CLI_PATH_ENV, explicitPath.get());
                    return null;
                }
                return explicitPath.get().toString();
            }
            Path explicitPathValue = Path.of(explicit.trim()).toAbsolutePath().normalize();
            logger.warn("Invalid {} value: {}", GH_CLI_PATH_ENV, explicitPathValue);
            return null;
        }

        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }
        return CliPathResolver.findExecutableInPath("gh")
            .map(path -> path.toAbsolutePath().normalize().toString())
            .orElse(null);
    }
}