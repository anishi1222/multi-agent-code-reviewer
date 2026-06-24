package dev.logicojp.reviewer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

final class GhAuthTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(GhAuthTokenProvider.class);
    private static final int GH_AUTH_MAX_ATTEMPTS = 3;
    private static final long GH_AUTH_BACKOFF_BASE_MS = 1_000L;
    private static final long GH_AUTH_BACKOFF_MAX_MS = 5_000L;
    private static final Path SAFE_WORKING_DIRECTORY =
        Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
    private static final String[] TOKEN_ENV_VARS = {
        "GITHUB_TOKEN",
        "GH_TOKEN",
        "GH_ENTERPRISE_TOKEN"
    };
    private static final long STREAM_DRAIN_TIMEOUT_SECONDS = 1;

    private final long timeoutSeconds;
    private final Supplier<String> ghPathSupplier;
    private final GhAuthCommand ghAuthCommand;

    @FunctionalInterface
    interface GhAuthCommand {
        Optional<String> run(String ghPath, long timeoutSeconds);
    }

    GhAuthTokenProvider(long timeoutSeconds, GhCliLocator ghCliLocator) {
        this(timeoutSeconds, ghCliLocator::resolve, GhAuthTokenProvider::attemptResolveFromGhAuth);
    }

    GhAuthTokenProvider(long timeoutSeconds,
                        Supplier<String> ghPathSupplier,
                        GhAuthCommand ghAuthCommand) {
        this.timeoutSeconds = timeoutSeconds;
        this.ghPathSupplier = ghPathSupplier;
        this.ghAuthCommand = ghAuthCommand;
    }

    Optional<String> resolve() {
        String ghPath = ghPathSupplier.get();
        if (ghPath == null) {
            logger.warn("gh CLI not found. Install GitHub CLI or set GH_CLI_PATH.");
            return Optional.empty();
        }
        for (int attempt = 1; attempt <= GH_AUTH_MAX_ATTEMPTS; attempt++) {
            Optional<String> result = ghAuthCommand.run(ghPath, timeoutSeconds);
            if (result.isPresent()) {
                return result;
            }
            if (attempt < GH_AUTH_MAX_ATTEMPTS && !sleepBeforeRetry(attempt)) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private boolean sleepBeforeRetry(int attempt) {
        long backoffMs = RetryPolicyUtils.computeBackoffWithJitter(
            GH_AUTH_BACKOFF_BASE_MS,
            GH_AUTH_BACKOFF_MAX_MS,
            attempt
        );
        logger.debug("gh auth token failed (attempt {}/{}), retrying in {}ms",
            attempt, GH_AUTH_MAX_ATTEMPTS, backoffMs);
        try {
            Thread.sleep(backoffMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while backing off gh auth retry", e);
            return false;
        }
    }

    private static Optional<String> attemptResolveFromGhAuth(String ghPath, long timeoutSeconds) {
        Path executionPath = CliPathResolver.revalidateExecutionPath(ghPath, "gh")
            .orElse(null);
        if (executionPath == null) {
            logger.warn("gh CLI path changed or became invalid before execution: {}", ghPath);
            return Optional.empty();
        }
        ProcessBuilder builder = new ProcessBuilder(executionPath.toString(), "auth", "token", "-h", "github.com");
        builder.directory(SAFE_WORKING_DIRECTORY.toFile());
        scrubSensitiveTokenEnvironment(builder);
        try {
            Process process = builder.start();
            InputStream stdoutStream = process.getInputStream();
            InputStream stderrStream = process.getErrorStream();
            CompletableFuture<String> stdout = readStreamAsync(stdoutStream);
            CompletableFuture<String> stderr = readStreamAsync(stderrStream);
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                closeQuietly(stdoutStream);
                closeQuietly(stderrStream);
                stdout.cancel(true);
                stderr.cancel(true);
                logger.warn("gh auth token timed out after {} seconds", timeoutSeconds);
                return Optional.empty();
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.warn("gh auth token failed with exit code {}{}", exitCode, stderrSuffix(stderr));
                return Optional.empty();
            }
            String token = firstNonBlankLine(collectStream(stdout, "stdout"));
            if (token == null) {
                return Optional.empty();
            }
            return Optional.of(token);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while resolving token from gh auth", e);
            return Optional.empty();
        } catch (IOException e) {
            logger.warn("Failed to resolve token from gh auth", e);
            return Optional.empty();
        }
    }

    private static CompletableFuture<String> readStreamAsync(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            try (var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                var buffer = new StringBuilder();
                char[] chars = new char[1024];
                int read;
                while ((read = reader.read(chars)) != -1) {
                    buffer.append(chars, 0, read);
                }
                return buffer.toString();
            } catch (IOException _) {
                return "";
            }
        });
    }

    private static String stderrSuffix(CompletableFuture<String> stderr) {
        String message = firstNonBlankLine(collectStream(stderr, "stderr"));
        return message != null ? " (" + message + ")" : "";
    }

    private static String collectStream(CompletableFuture<String> streamFuture, String streamName) {
        try {
            return streamFuture.get(STREAM_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while reading gh auth {} stream", streamName, e);
            return "";
        } catch (ExecutionException e) {
            logger.warn("Failed to read gh auth {} stream: {}", streamName, e.getMessage());
            return "";
        } catch (TimeoutException e) {
            streamFuture.cancel(true);
            logger.warn("Timed out reading gh auth {} stream", streamName);
            return "";
        }
    }

    static String firstNonBlankLine(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        return Arrays.stream(output.split("\\R"))
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .findFirst()
            .orElse(null);
    }

    private static void scrubSensitiveTokenEnvironment(ProcessBuilder builder) {
        for (String envVar : TOKEN_ENV_VARS) {
            builder.environment().remove(envVar);
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException _) {
            // Best-effort cleanup only.
        }
    }
}
