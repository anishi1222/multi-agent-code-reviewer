package dev.logicojp.reviewer.service;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.CopilotClientOptions;
import dev.logicojp.reviewer.util.CliPathResolver;
import dev.logicojp.reviewer.util.SecurityAuditLogger;
import dev.logicojp.reviewer.util.TokenHashUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Service for managing the Copilot SDK client lifecycle.
///
/// Merges v1 CopilotCliPathResolver, CopilotCliHealthChecker,
/// CopilotStartupErrorFormatter, CopilotClientStarter, CopilotTimeoutResolver,
/// and CopilotCliException into a single cohesive class.
@Singleton
public class CopilotService {

    /// Exception thrown when the Copilot CLI is not found, not authenticated,
    /// or fails health checks.
    public static final class CliException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        public CliException(String message) {
            super(message);
        }

        public CliException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(CopilotService.class);

    // --- CLI path resolution constants ---
    private static final String CLI_PATH_ENV = "COPILOT_CLI_PATH";
    private static final String[] CLI_CANDIDATES = {"github-copilot", "copilot"};

    // --- Health check constants ---
    private static final long DEFAULT_CLI_HEALTHCHECK_SECONDS = 10;
    private static final String CLI_HEALTHCHECK_ENV = "COPILOT_CLI_HEALTHCHECK_SECONDS";
    private static final String CLI_AUTH_CHECK_ENV = "COPILOT_CLI_AUTHCHECK_SECONDS";
    private static final long DEFAULT_CLI_AUTHCHECK_SECONDS = 15;

    // --- Startup constants ---
    private static final long DEFAULT_START_TIMEOUT_SECONDS = 60;
    private static final String START_TIMEOUT_ENV = "COPILOT_START_TIMEOUT_SECONDS";
    private static final String GITHUB_TOKEN_ENV = "GITHUB_TOKEN";
    private static final String UNRESOLVED_TOKEN_PLACEHOLDER = "${GITHUB_TOKEN}";

    // --- Error message constants ---
    private static final String CLI_INSTALL_AUTH_GUIDANCE =
        "Ensure GitHub Copilot CLI is installed and authenticated";
    private static final String CLIENT_TIMEOUT_ENV_GUIDANCE =
        "or set COPILOT_START_TIMEOUT_SECONDS to a higher value.";
    private static final String PROTOCOL_AUTH_GUIDANCE =
        "and authenticated (for example, run `github-copilot auth login`)";
    private static final int INIT_MAX_ATTEMPTS = 3;
    private static final long INIT_BACKOFF_BASE_MS = 1000L;
    private static final long INIT_BACKOFF_MAX_MS = 5000L;

    /// Volatile for safe publication; mutations serialized by synchronized lifecycle methods.
    private volatile CopilotClient client;
    private volatile String initializedTokenFingerprint;

    // ========================================================================
    // Lifecycle
    // ========================================================================

    /// Attempts eager initialization during bean startup using GITHUB_TOKEN when available.
    @PostConstruct
    void initializeAtStartup() {
        try {
            initializeOrThrow(System.getenv(GITHUB_TOKEN_ENV));
        } catch (CliException e) {
            logger.debug("Skipping eager Copilot initialization at startup: {}", e.getMessage(), e);
        }
    }

    /// Initializes the Copilot client, wrapping checked exceptions as RuntimeException.
    public void initializeOrThrow(String githubToken) {
        try {
            initialize(normalizeToken(githubToken));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            SecurityAuditLogger.log(
                "authentication", "copilot.initialize",
                "Copilot client initialization interrupted",
                Map.of("outcome", "interrupted"));
            throw new CliException("Failed to initialize Copilot service", e);
        } catch (RuntimeException e) {
            SecurityAuditLogger.log(
                "authentication", "copilot.initialize",
                "Copilot client initialization failed",
                Map.of("outcome", "failure"));
            throw e;
        }
    }

    private synchronized void initialize(String githubToken) throws InterruptedException {
        String tokenFingerprint = TokenHashUtils.sha256HexOrNull(githubToken);
        if (client != null && Objects.equals(initializedTokenFingerprint, tokenFingerprint)) {
            return;
        }
        if (client != null) {
            closeCurrentClient();
        }

        logger.info("Initializing Copilot client...");
        CopilotClientOptions options = buildClientOptions(githubToken);
        SecurityAuditLogger.log(
            "authentication", "copilot.initialize",
            "Copilot client authentication initiated",
            Map.of(
                "authMethod", shouldUseToken(githubToken) ? "github-token" : "gh-cli",
                "tokenFingerprintPrefix", shortFingerprint(tokenFingerprint)));

        long timeoutSeconds = resolveEnvTimeout(START_TIMEOUT_ENV, DEFAULT_START_TIMEOUT_SECONDS);
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= INIT_MAX_ATTEMPTS; attempt++) {
            CopilotClient createdClient = new CopilotClient(options);
            try {
                startClient(createdClient, timeoutSeconds);
                client = createdClient;
                initializedTokenFingerprint = tokenFingerprint;
                logger.info("Copilot client initialized");
                SecurityAuditLogger.log(
                    "authentication", "copilot.initialize",
                    "Copilot client authentication completed",
                    Map.of("outcome", "success", "attempt", String.valueOf(attempt)));
                return;
            } catch (RuntimeException e) {
                lastFailure = e;
                closeQuietly(createdClient);
                if (attempt < INIT_MAX_ATTEMPTS) {
                    logger.warn("Copilot client initialization attempt {}/{} failed: {}",
                        attempt, INIT_MAX_ATTEMPTS, e.getMessage());
                    sleepWithJitter(attempt);
                    continue;
                }
            }
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
    }

    /// Gets the initialized CopilotClient.
    public CopilotClient getClient() {
        CopilotClient localClient = client;
        if (localClient == null) {
            throw new IllegalStateException("CopilotService not initialized. Call initialize() first.");
        }
        return localClient;
    }

    /// Checks if the service is initialized.
    public boolean isInitialized() {
        return client != null;
    }

    @PreDestroy
    public synchronized void shutdown() {
        if (client != null) {
            closeCurrentClient();
        }
    }

    private void closeCurrentClient() {
        try {
            logger.info("Shutting down Copilot client...");
            client.close();
            logger.info("Copilot client shut down");
            SecurityAuditLogger.log(
                "authentication", "copilot.shutdown",
                "Copilot client shutdown completed",
                Map.of("outcome", "success"));
        } catch (Exception e) {
            logger.warn("Error shutting down Copilot client: {}", e.getMessage(), e);
            SecurityAuditLogger.log(
                "authentication", "copilot.shutdown",
                "Copilot client shutdown failed",
                Map.of("outcome", "failure"));
        } finally {
            client = null;
            initializedTokenFingerprint = null;
        }
    }

    // ========================================================================
    // Client options & auth
    // ========================================================================

    private CopilotClientOptions buildClientOptions(String githubToken) throws InterruptedException {
        var options = new CopilotClientOptions();
        String cliPath = resolveCliPath();
        if (cliPath != null && !cliPath.isBlank()) {
            options.setCliPath(cliPath);
        }
        boolean useToken = shouldUseToken(githubToken);
        verifyCliHealthy(cliPath, useToken);
        if (useToken) {
            options.setGithubToken(githubToken);
            options.setUseLoggedInUser(Boolean.FALSE);
        } else {
            options.setUseLoggedInUser(Boolean.TRUE);
        }
        return options;
    }

    private boolean shouldUseToken(String githubToken) {
        return githubToken != null
            && !githubToken.isBlank()
            && !githubToken.equals(UNRESOLVED_TOKEN_PLACEHOLDER);
    }

    private String normalizeToken(String githubToken) {
        return shouldUseToken(githubToken) ? githubToken : null;
    }

    private static String shortFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return "none";
        }
        return fingerprint.length() <= 12 ? fingerprint : fingerprint.substring(0, 12);
    }

    // ========================================================================
    // CLI path resolution (merged from CopilotCliPathResolver)
    // ========================================================================

    private String resolveCliPath() {
        String explicit = resolveExplicitCliPath();
        if (explicit != null) {
            return explicit;
        }
        return resolveCliPathFromSystemPath();
    }

    private String resolveExplicitCliPath() {
        String explicit = System.getenv(CLI_PATH_ENV);
        if (explicit == null || explicit.isBlank()) {
            return null;
        }
        var explicitPath = CliPathResolver.resolveExplicitExecutable(explicit, CLI_CANDIDATES);
        if (explicitPath.isPresent()) {
            return explicitPath.get().toString();
        }
        Path explicitPathValue = Path.of(explicit.trim()).toAbsolutePath().normalize();
        throw new CliException("Copilot CLI not found at " + explicitPathValue
            + ". Verify " + CLI_PATH_ENV + " or install GitHub Copilot CLI.");
    }

    private String resolveCliPathFromSystemPath() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            throw new CliException("PATH is not set. Install GitHub Copilot CLI and/or set "
                + CLI_PATH_ENV + " to its executable path.");
        }
        var candidate = CliPathResolver.findExecutableInPath(CLI_CANDIDATES);
        if (candidate.isPresent()) {
            return candidate.get().toString();
        }
        throw new CliException("GitHub Copilot CLI not found in PATH. Install it and ensure "
            + "`github-copilot` or `copilot` is available, or set " + CLI_PATH_ENV + ".");
    }

    // ========================================================================
    // CLI health checking (merged from CopilotCliHealthChecker)
    // ========================================================================

    private void verifyCliHealthy(String cliPath, boolean tokenProvided) throws InterruptedException {
        if (cliPath == null || cliPath.isBlank()) {
            return;
        }
        runCliCommand(
            List.of(cliPath, "--version"),
            resolveEnvTimeout(CLI_HEALTHCHECK_ENV, DEFAULT_CLI_HEALTHCHECK_SECONDS),
            "Copilot CLI did not respond within ",
            "Copilot CLI exited with code ",
            "Failed to execute Copilot CLI: ",
            "Ensure the CLI is installed and authenticated.");

        if (tokenProvided) {
            logger.info("GITHUB_TOKEN provided — skipping CLI auth status check");
        } else {
            runCliCommand(
                List.of(cliPath, "auth", "status"),
                resolveEnvTimeout(CLI_AUTH_CHECK_ENV, DEFAULT_CLI_AUTHCHECK_SECONDS),
                "Copilot CLI auth status timed out after ",
                "Copilot CLI auth status failed with code ",
                "Failed to execute Copilot CLI auth status: ",
                "Run `github-copilot auth login` to authenticate.");
        }
    }

    private void runCliCommand(List<String> command, long timeoutSeconds,
                               String timeoutMessage, String exitMessage,
                               String ioMessage, String remediationMessage)
        throws InterruptedException {
        var builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            var drainThread = Thread.ofVirtual().name("cli-drain").start(() -> {
                try (var in = process.getInputStream()) {
                    in.transferTo(OutputStream.nullOutputStream());
                } catch (IOException _) {
                }
            });
            try {
                boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    drainThread.interrupt();
                    throw new CliException(
                        timeoutMessage + timeoutSeconds + "s. " + remediationMessage);
                }
                if (process.exitValue() != 0) {
                    throw new CliException(
                        exitMessage + process.exitValue() + ". " + remediationMessage);
                }
            } finally {
                drainThread.join();
            }
        } catch (IOException e) {
            throw new CliException(ioMessage + e.getMessage(), e);
        }
    }

    // ========================================================================
    // Client starting (merged from CopilotClientStarter)
    // ========================================================================

    private void startClient(CopilotClient createdClient, long timeoutSeconds)
        throws InterruptedException {
        try {
            if (timeoutSeconds > 0) {
                createdClient.start().get(timeoutSeconds, TimeUnit.SECONDS);
            } else {
                createdClient.start().get();
            }
        } catch (ExecutionException e) {
            closeQuietly(createdClient);
            throw mapExecutionException(e);
        } catch (TimeoutException e) {
            closeQuietly(createdClient);
            throw new CliException(buildClientTimeoutMessage(timeoutSeconds), e);
        }
    }

    private CliException mapExecutionException(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof TimeoutException) {
            return new CliException(buildProtocolTimeoutMessage(), cause);
        }
        if (cause != null) {
            return new CliException("Copilot client start failed: " + cause.getMessage(), cause);
        }
        return new CliException("Copilot client start failed", e);
    }

    private void closeQuietly(CopilotClient c) {
        try {
            c.close();
        } catch (Exception e) {
            logger.debug("Failed to close Copilot client after startup failure: {}", e.getMessage(), e);
        }
    }

    // ========================================================================
    // Error formatting (merged from CopilotStartupErrorFormatter)
    // ========================================================================

    private static String buildClientTimeoutMessage(long timeoutSeconds) {
        return "Copilot client start timed out after " + timeoutSeconds + "s. "
            + CLI_INSTALL_AUTH_GUIDANCE + ", "
            + CLIENT_TIMEOUT_ENV_GUIDANCE;
    }

    private static String buildProtocolTimeoutMessage() {
        return "Copilot CLI ping timed out. "
            + CLI_INSTALL_AUTH_GUIDANCE + " "
            + PROTOCOL_AUTH_GUIDANCE + ", "
            + "or set " + CLI_PATH_ENV + " to the correct executable.";
    }

    // ========================================================================
    // Timeout resolution (merged from CopilotTimeoutResolver)
    // ========================================================================

    static long resolveEnvTimeout(String envVar, long defaultValue) {
        String value = System.getenv(envVar);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed >= 0 ? parsed : defaultValue;
        } catch (NumberFormatException _) {
            logger.warn("Invalid {} value: {}. Using default.", envVar, value);
            return defaultValue;
        }
    }

    private static void sleepWithJitter(int attempt) throws InterruptedException {
        long exponentialMs = Math.min(INIT_BACKOFF_BASE_MS << Math.max(0, attempt - 1), INIT_BACKOFF_MAX_MS);
        long jitteredMs = ThreadLocalRandom.current().nextLong(exponentialMs + 1);
        Thread.sleep(jitteredMs);
    }
}
