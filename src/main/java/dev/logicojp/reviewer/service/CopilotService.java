package dev.logicojp.reviewer.service;

import com.github.copilot.sdk.ConnectionState;
import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.CopilotClientOptions;
import dev.logicojp.reviewer.config.CopilotConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import dev.logicojp.reviewer.util.SecurityAuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Service for managing the Copilot SDK client lifecycle.
@Singleton
public class CopilotService {

    private static final Logger logger = LoggerFactory.getLogger(CopilotService.class);
    private static final long DEFAULT_START_TIMEOUT_SECONDS = 60;
    private static final String DEFAULT_SDK_LOG_LEVEL = "warning";
    private static final Set<String> SUPPORTED_SDK_LOG_LEVELS =
        Set.of("none", "error", "warning", "info", "debug", "all", "default");
    private static final String SDK_LOG_LEVEL_ENV = "COPILOT_SDK_LOG_LEVEL";

    private final CopilotCliPathResolver cliPathResolver;
    private final CopilotHealthProbe healthProbe;
    private final CopilotConfig copilotConfig;
    private final CopilotStartupErrorFormatter startupErrorFormatter;
    private final CopilotClientStarter clientStarter;
    /// `volatile` provides safe publication for lock-free reads in `getClient()/isInitialized()`.
    /// Mutations are serialized by synchronized lifecycle methods (`initialize`, `shutdown`).
    private volatile CopilotClient client;

    @Inject
    public CopilotService(CopilotCliPathResolver cliPathResolver,
                          CopilotHealthProbe healthProbe,
                          CopilotConfig copilotConfig,
                          CopilotStartupErrorFormatter startupErrorFormatter,
                          CopilotClientStarter clientStarter) {
        this.cliPathResolver = cliPathResolver;
        this.healthProbe = healthProbe;
        this.copilotConfig = copilotConfig;
        this.startupErrorFormatter = startupErrorFormatter;
        this.clientStarter = clientStarter;
    }

    /// Attempts eager initialization during bean startup using OAuth device-flow credentials.
    /// Falls back to lazy/explicit initialization if startup prerequisites are not met.
    @PostConstruct
    void initializeAtStartup() {
        try {
            initializeOrThrow();
        } catch (CopilotCliException e) {
            logger.debug("Skipping eager Copilot initialization at startup: {}", e.getMessage(), e);
        }
    }

    /// Initializes the Copilot client, wrapping checked exceptions as RuntimeException.
    /// Convenience method for callers that cannot handle checked exceptions.
    public void initializeOrThrow() {
        try {
            initialize();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            SecurityAuditLogger.log(
                "authentication",
                "copilot.initialize",
                "Copilot client initialization interrupted",
                Map.of("outcome", "interrupted")
            );
            throw new CopilotCliException("Failed to initialize Copilot service", e);
        } catch (RuntimeException e) {
            SecurityAuditLogger.log(
                "authentication",
                "copilot.initialize",
                "Copilot client initialization failed",
                Map.of("outcome", "failure")
            );
            throw e;
        }
    }

    /// Backward-compatible overload. Token input is intentionally ignored because
    /// Copilot authentication now relies on OAuth device flow credentials.
    @Deprecated(forRemoval = true, since = "2026.03")
    public void initializeOrThrow(String ignoredToken) {
        if (ignoredToken != null && !ignoredToken.isBlank()) {
            logger.warn("initializeOrThrow(String) ignores token input. Use OAuth login via `gh auth login` instead.");
        }
        initializeOrThrow();
    }

    /// Initializes the Copilot client.
    private synchronized void initialize() throws InterruptedException {
        if (client != null) {
            return;
        }

        logger.info("Initializing Copilot client...");
        CopilotClientOptions options = buildClientOptions();
        SecurityAuditLogger.log(
            "authentication",
            "copilot.initialize",
            "Copilot client authentication initiated",
            Map.of(
                "authMethod", "oauth-device-flow",
                "tokenFingerprintPrefix", "none"
            )
        );
        CopilotClient createdClient = new CopilotClient(options);
        long timeoutSeconds = resolveStartTimeoutSeconds();
        startClient(createdClient, timeoutSeconds);
        client = createdClient;
        logger.info("Copilot client initialized");
        SecurityAuditLogger.log(
            "authentication",
            "copilot.initialize",
            "Copilot client authentication completed",
            Map.of("outcome", "success")
        );
    }

    private CopilotClientOptions buildClientOptions() {
        CopilotClientOptions options = new CopilotClientOptions();
        String cliPath = cliPathResolver.resolveCliPath();
        applyCliPathOption(options, cliPath);
        applyAuthOptions(options);
        applyResilienceOptions(options);
        applyLoggingOptions(options);
        return options;
    }

    private void startClient(CopilotClient createdClient,
                             long timeoutSeconds) throws InterruptedException {
        clientStarter.start(new CopilotClientStarter.StartableClient() {
            @Override
            public void start(long timeoutSeconds) throws ExecutionException, TimeoutException, InterruptedException {
                long effectiveTimeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_START_TIMEOUT_SECONDS;
                createdClient.start().get(effectiveTimeoutSeconds, TimeUnit.SECONDS);
            }

            @Override
            public void close() {
                createdClient.close();
            }
        }, timeoutSeconds, startupErrorFormatter);
    }

    private long resolveStartTimeoutSeconds() {
        return copilotConfig.startTimeoutSeconds();
    }

    private void applyCliPathOption(CopilotClientOptions options, String cliPath) {
        if (cliPath != null && !cliPath.isBlank()) {
            options.setCliPath(cliPath);
        }
    }

    private void applyAuthOptions(CopilotClientOptions options) {
        options.setUseLoggedInUser(Boolean.TRUE);
    }

    /// Forward-compatible flag for SDK-side process supervision. Phase 3a
    /// verification confirmed this is currently a **no-op** in SDK
    /// `0.3.0-java.2` (the field is set but never consumed by `CliServerManager`
    /// or `CopilotClient`). We keep the call so that future SDK releases that
    /// implement supervision will benefit automatically. CLI subprocess crash
    /// recovery is handled by {@link #ensureHealthyOrReinitialize()}.
    private void applyResilienceOptions(CopilotClientOptions options) {
        options.setAutoRestart(true);
    }

    /// Maps the {@code COPILOT_SDK_LOG_LEVEL} environment variable to the SDK
    /// log level option, defaulting to {@value #DEFAULT_SDK_LOG_LEVEL} so that
    /// noisy CLI subprocess logs do not flood the console even when our app
    /// logger is in debug mode.
    private void applyLoggingOptions(CopilotClientOptions options) {
        options.setLogLevel(resolveSdkLogLevel());
    }

    private String resolveSdkLogLevel() {
        String configured = System.getenv(SDK_LOG_LEVEL_ENV);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_SDK_LOG_LEVEL;
        }
        Optional<String> normalized = normalizeSdkLogLevel(configured);
        if (normalized.isEmpty()) {
            logger.warn("Unsupported {} value '{}', falling back to '{}'. Supported: {}",
                SDK_LOG_LEVEL_ENV, configured, DEFAULT_SDK_LOG_LEVEL, SUPPORTED_SDK_LOG_LEVELS);
            return DEFAULT_SDK_LOG_LEVEL;
        }
        return normalized.get();
    }

    static Optional<String> normalizeSdkLogLevel(String configured) {
        String normalized = configured.trim().toLowerCase(Locale.ROOT);
        String canonical = switch (normalized) {
            case "warn" -> "warning";
            case "off" -> "none";
            case "trace" -> "debug";
            default -> normalized;
        };
        if (!SUPPORTED_SDK_LOG_LEVELS.contains(canonical)) {
            return Optional.empty();
        }
        return Optional.of(canonical);
    }

    /// Gets the Copilot client. Must call initialize() first.
    /// @return The initialized CopilotClient
    /// @throws IllegalStateException if not initialized
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

    /// Checks whether the Copilot client is initialized and currently in
    /// {@link ConnectionState#CONNECTED}. This probe is synchronous and
    /// performs no JSON-RPC roundtrip, so it is safe to call frequently.
    public boolean isHealthy() {
        return healthProbe.isClientHealthy(client);
    }

    /// Re-initializes the client only when it is currently unhealthy. Uses the
    /// cheap {@link #isHealthy()} probe first; if that reports healthy, no
    /// further action is taken.
    public synchronized void ensureHealthyOrReinitialize() throws InterruptedException {
        if (isHealthy()) {
            return;
        }
        ConnectionState lastState = healthProbe.getConnectionState(client);
        logger.warn("Copilot client is unhealthy (state={}); attempting re-initialization",
            lastState == null ? "uninitialized" : lastState);
        closeCurrentClient();
        initialize();
    }

    /// Shuts down the Copilot client.
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
                "authentication",
                "copilot.shutdown",
                "Copilot client shutdown completed",
                Map.of("outcome", "success")
            );
        } catch (Exception e) {
            logger.warn("Error shutting down Copilot client: {}", e.getMessage(), e);
            SecurityAuditLogger.log(
                "authentication",
                "copilot.shutdown",
                "Copilot client shutdown failed",
                Map.of("outcome", "failure")
            );
        } finally {
            client = null;
        }
    }
}
