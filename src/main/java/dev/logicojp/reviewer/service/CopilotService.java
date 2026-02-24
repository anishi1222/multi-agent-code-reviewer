package dev.logicojp.reviewer.service;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.CopilotClientOptions;
import dev.logicojp.reviewer.config.CopilotConfig;
import dev.logicojp.reviewer.util.BackoffUtils;
import dev.logicojp.reviewer.util.CliPathResolver;
import dev.logicojp.reviewer.util.SecurityAuditLogger;
import dev.logicojp.reviewer.util.TokenHashUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Service for managing the Copilot SDK client lifecycle.
@Singleton
public class CopilotService {

    private static final Logger logger = LoggerFactory.getLogger(CopilotService.class);

    private static final String CLI_PATH_ENV = "COPILOT_CLI_PATH";
    private static final String[] CLI_CANDIDATES = {"github-copilot", "copilot"};

    private static final String UNRESOLVED_TOKEN_PLACEHOLDER = "${GITHUB_TOKEN}";

    private static final String CLI_INSTALL_AUTH_GUIDANCE =
        "Ensure GitHub Copilot CLI is installed and authenticated";
    private static final String CLIENT_TIMEOUT_ENV_GUIDANCE =
        "or set reviewer.copilot.start-timeout-seconds to a higher value.";
    private static final String PROTOCOL_AUTH_GUIDANCE =
        "and authenticated (for example, run `github-copilot auth login`)";
    private static final int INIT_MAX_ATTEMPTS = 3;
    private static final long INIT_BACKOFF_BASE_MS = 1000L;
    private static final long INIT_BACKOFF_MAX_MS = 5000L;
    private static final int CLI_CHECK_MAX_ATTEMPTS = 3;
    private static final long CLI_CHECK_BACKOFF_BASE_MS = 500L;
    private static final long CLI_CHECK_BACKOFF_MAX_MS = 4000L;

    private final CopilotConfig copilotConfig;

    /// Volatile for safe publication; mutations serialized by synchronized lifecycle methods.
    private volatile CopilotClient client;
    private volatile String initializedTokenFingerprint;

    @Inject
    public CopilotService(CopilotConfig copilotConfig) {
        this.copilotConfig = copilotConfig;
    }

    @PostConstruct
    void initializeAtStartup() {
        try {
            initializeOrThrow(copilotConfig.githubToken());
        } catch (CopilotCliException e) {
            logger.warn("Copilot client not available at startup: {}. "
                + "Initialization will be retried when a review command is executed.", e.getMessage());
        }
    }

    public void initializeOrThrow(String githubToken) {
        try {
            initialize(normalizeToken(githubToken));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            SecurityAuditLogger.log(
                "authentication", "copilot.initialize",
                "Copilot client initialization interrupted",
                Map.of("outcome", "interrupted"));
            throw new CopilotCliException("Failed to initialize Copilot service", e);
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

        long timeoutSeconds = copilotConfig.startTimeoutSeconds();
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
                    BackoffUtils.sleepWithJitter(attempt, INIT_BACKOFF_BASE_MS, INIT_BACKOFF_MAX_MS);
                }
            }
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
    }

    public CopilotClient getClient() {
        CopilotClient localClient = client;
        if (localClient == null) {
            throw new IllegalStateException("CopilotService not initialized. Call initialize() first.");
        }
        return localClient;
    }

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

    private String resolveCliPath() {
        String explicit = resolveExplicitCliPath();
        if (explicit != null) {
            return explicit;
        }
        return resolveCliPathFromSystemPath();
    }

    private String resolveExplicitCliPath() {
        String explicit = copilotConfig.cliPath();
        if (explicit == null || explicit.isBlank()) {
            return null;
        }
        var explicitPath = CliPathResolver.resolveExplicitExecutable(explicit, CLI_CANDIDATES);
        if (explicitPath.isPresent()) {
            return explicitPath.get().toString();
        }
        Path explicitPathValue = Path.of(explicit.trim()).toAbsolutePath().normalize();
        throw new CopilotCliException("Copilot CLI not found at " + explicitPathValue
            + ". Verify reviewer.copilot.cli-path or " + CLI_PATH_ENV + ".");
    }

    private String resolveCliPathFromSystemPath() {
        var candidate = CliPathResolver.findExecutableInPath(CLI_CANDIDATES);
        if (candidate.isPresent()) {
            return candidate.get().toString();
        }
        throw new CopilotCliException("GitHub Copilot CLI not found in PATH. Install it and ensure "
            + "`github-copilot` or `copilot` is available, or set " + CLI_PATH_ENV + ".");
    }

    private void verifyCliHealthy(String cliPath, boolean tokenProvided) throws InterruptedException {
        if (cliPath == null || cliPath.isBlank()) {
            return;
        }
        runCliCommandWithRetry(
            List.of(cliPath, "--version"),
            copilotConfig.healthcheckSeconds(),
            "Copilot CLI did not respond within ",
            "Copilot CLI exited with code ",
            "Failed to execute Copilot CLI: ",
            "Ensure the CLI is installed and authenticated.");

        if (tokenProvided) {
            logger.info("GITHUB_TOKEN provided — skipping CLI auth status check");
        } else {
            runCliCommandWithRetry(
                List.of(cliPath, "auth", "status"),
                copilotConfig.authcheckSeconds(),
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
                    throw new CopilotCliException(
                        timeoutMessage + timeoutSeconds + "s. " + remediationMessage);
                }
                if (process.exitValue() != 0) {
                    throw new CopilotCliException(
                        exitMessage + process.exitValue() + ". " + remediationMessage);
                }
            } finally {
                drainThread.join();
            }
        } catch (IOException e) {
            throw new CopilotCliException(ioMessage + e.getMessage(), e);
        }
    }

    private void runCliCommandWithRetry(List<String> command, long timeoutSeconds,
                                        String timeoutMessage, String exitMessage,
                                        String ioMessage, String remediationMessage)
        throws InterruptedException {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= CLI_CHECK_MAX_ATTEMPTS; attempt++) {
            try {
                runCliCommand(command, timeoutSeconds, timeoutMessage, exitMessage, ioMessage, remediationMessage);
                return;
            } catch (RuntimeException e) {
                lastFailure = e;
                if (attempt < CLI_CHECK_MAX_ATTEMPTS) {
                    logger.warn("CLI check attempt {}/{} failed for '{}': {}",
                        attempt, CLI_CHECK_MAX_ATTEMPTS, String.join(" ", command), e.getMessage());
                    BackoffUtils.sleepWithJitter(attempt, CLI_CHECK_BACKOFF_BASE_MS, CLI_CHECK_BACKOFF_MAX_MS);
                }
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
    }

    private void startClient(CopilotClient createdClient, long timeoutSeconds)
        throws InterruptedException {
        long effectiveTimeoutSeconds = timeoutSeconds > 0
            ? timeoutSeconds
            : CopilotConfig.DEFAULT_START_TIMEOUT_SECONDS;
        try {
            createdClient.start().get(effectiveTimeoutSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            closeQuietly(createdClient);
            throw mapExecutionException(e);
        } catch (TimeoutException e) {
            closeQuietly(createdClient);
            throw new CopilotCliException(buildClientTimeoutMessage(effectiveTimeoutSeconds), e);
        }
    }

    private CopilotCliException mapExecutionException(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof TimeoutException) {
            return new CopilotCliException(buildProtocolTimeoutMessage(), cause);
        }
        if (cause != null) {
            return new CopilotCliException("Copilot client start failed: " + cause.getMessage(), cause);
        }
        return new CopilotCliException("Copilot client start failed", e);
    }

    private void closeQuietly(CopilotClient c) {
        try {
            c.close();
        } catch (Exception e) {
            logger.debug("Failed to close Copilot client after startup failure: {}", e.getMessage(), e);
        }
    }

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
}