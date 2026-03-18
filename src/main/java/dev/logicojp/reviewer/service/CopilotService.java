package dev.logicojp.reviewer.service;

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

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Service for managing the Copilot SDK client lifecycle.
@Singleton
public class CopilotService {
    
    private static final Logger logger = LoggerFactory.getLogger(CopilotService.class);
    private static final long DEFAULT_START_TIMEOUT_SECONDS = 60;

    private final CopilotCliPathResolver cliPathResolver;
    private final CopilotCliHealthChecker cliHealthChecker;
    private final CopilotConfig copilotConfig;
    private final CopilotStartupErrorFormatter startupErrorFormatter;
    private final CopilotClientStarter clientStarter;
    /// `volatile` provides safe publication for lock-free reads in `getClient()/isInitialized()`.
    /// Mutations are serialized by synchronized lifecycle methods (`initialize`, `shutdown`).
    private volatile CopilotClient client;

    @Inject
    public CopilotService(CopilotCliPathResolver cliPathResolver,
                          CopilotCliHealthChecker cliHealthChecker,
                          CopilotConfig copilotConfig,
                          CopilotStartupErrorFormatter startupErrorFormatter,
                          CopilotClientStarter clientStarter) {
        this.cliPathResolver = cliPathResolver;
        this.cliHealthChecker = cliHealthChecker;
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
    @Deprecated(forRemoval = false, since = "2026.03")
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

    private CopilotClientOptions buildClientOptions() throws InterruptedException {
        CopilotClientOptions options = new CopilotClientOptions();
        String cliPath = cliPathResolver.resolveCliPath();
        applyCliPathOption(options, cliPath);
        cliHealthChecker.verifyCliHealthy(cliPath);
        applyAuthOptions(options);
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

    /// Checks whether the Copilot client is initialized and responsive enough for API use.
    /// This performs a lightweight health verification via CLI probes.
    public boolean isHealthy() {
        CopilotClient localClient = client;
        if (localClient == null) {
            return false;
        }
        try {
            String cliPath = cliPathResolver.resolveCliPath();
            cliHealthChecker.verifyCliHealthy(cliPath);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Copilot client health check interrupted", e);
            return false;
        } catch (RuntimeException e) {
            logger.warn("Copilot client health check failed: {}", e.getMessage());
            return false;
        }
    }

    /// Re-initializes the client only when it is currently unhealthy.
    public synchronized void ensureHealthyOrReinitialize() throws InterruptedException {
        if (isHealthy()) {
            return;
        }
        logger.warn("Copilot client is unhealthy; attempting re-initialization");
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
