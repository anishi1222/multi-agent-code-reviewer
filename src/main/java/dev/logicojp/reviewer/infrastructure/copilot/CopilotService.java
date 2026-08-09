package dev.logicojp.reviewer.infrastructure.copilot;

import com.github.copilot.CopilotClient;
import com.github.copilot.rpc.CopilotClientOptions;
import dev.logicojp.reviewer.application.port.outbound.ManageCopilotClientPort;
import dev.logicojp.reviewer.domain.resilience.CopilotCliException;
import dev.logicojp.reviewer.infrastructure.auth.CopilotCliPathResolver;
import dev.logicojp.reviewer.infrastructure.config.CopilotConfig;
import dev.logicojp.reviewer.infrastructure.logging.SecurityAuditLogger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Infrastructure implementation of {@link ManageCopilotClientPort}.
///
/// Manages the full lifecycle of the Copilot SDK {@link CopilotClient}:
/// - eager {@code @PostConstruct} initialization using OAuth credentials
/// - explicit {@code start(token)} path (deprecated, retained for compatibility)
/// - {@code @PreDestroy} shutdown
///
/// Also provides the non-port {@link #getClient()} method used by other
/// infrastructure adapters ({@link ReviewSessionExecutor},
/// {@link RubberDuckDialogueExecutor}, {@link AiSummaryClient}).
@Singleton
public class CopilotService implements ManageCopilotClientPort {

    private static final Logger logger = LoggerFactory.getLogger(CopilotService.class);
    private static final String DEFAULT_SDK_LOG_LEVEL = "warning";
    private static final Set<String> SUPPORTED_SDK_LOG_LEVELS =
        Set.of("none", "error", "warning", "info", "debug", "all", "default");
    private static final String SDK_LOG_LEVEL_ENV = "COPILOT_SDK_LOG_LEVEL";

    private final CopilotCliPathResolver cliPathResolver;
    private final CopilotHealthProbe healthProbe;
    private final CopilotConfig copilotConfig;
    private final CopilotStartupErrorFormatter startupErrorFormatter;
    private final CopilotClientStarter clientStarter;

    /// {@code volatile} provides safe publication for lock-free reads in
    /// {@link #getClient()} / {@link #isHealthy()}.
    /// Mutations are serialized by synchronized lifecycle methods.
    private volatile CopilotClient client;

    @Inject
    public CopilotService(CopilotCliPathResolver cliPathResolver,
                          CopilotHealthProbe healthProbe,
                          CopilotConfig copilotConfig,
                          CopilotStartupErrorFormatter startupErrorFormatter,
                          CopilotClientStarter clientStarter) {
        this.cliPathResolver = Objects.requireNonNull(cliPathResolver);
        this.healthProbe = Objects.requireNonNull(healthProbe);
        this.copilotConfig = Objects.requireNonNull(copilotConfig);
        this.startupErrorFormatter = Objects.requireNonNull(startupErrorFormatter);
        this.clientStarter = Objects.requireNonNull(clientStarter);
    }

    /// Attempts eager initialization during bean startup using OAuth device-flow credentials.
    @PostConstruct
    void initializeAtStartup() {
        try {
            initialize();
        } catch (CopilotCliException e) {
            logger.debug("Skipping eager Copilot initialization at startup: {}", e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Copilot eager initialization interrupted");
        }
    }

    @PreDestroy
    void shutdownOnDestroy() {
        shutdown();
    }

    @Override
    @Deprecated(forRemoval = true, since = "2026.03")
    public synchronized void start(String token) {
        if (token != null && !token.isBlank()) {
            logger.warn("start(String) is deprecated and ignores token input. "
                + "Use OAuth login via `gh auth login` instead.");
        }
        try {
            initialize();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CopilotCliException("Copilot client start interrupted", e);
        }
    }

    @Override
    public synchronized void stop() {
        shutdown();
    }

    @Override
    public boolean isHealthy() {
        return healthProbe.isClientHealthy(client);
    }

    /// Returns the {@link CopilotClient} for use by infrastructure adapters.
    ///
    /// @throws CopilotCliException if the client is not initialized or unhealthy
    public CopilotClient getClient() {
        CopilotClient c = client;
        if (c == null) {
            throw new CopilotCliException(startupErrorFormatter.buildClientNotInitializedMessage());
        }
        return c;
    }

    private synchronized void initialize() throws InterruptedException {
        if (client != null && healthProbe.isClientHealthy(client)) {
            return;
        }
        String resolvedPath = cliPathResolver.resolveCliPath();
        String sdkLogLevel = resolveSdkLogLevel();
        logger.debug("Initializing Copilot client: cliPath={}, sdkLogLevel={}", resolvedPath, sdkLogLevel);

        CopilotClientOptions options = buildOptions(resolvedPath, sdkLogLevel);
        CopilotClient newClient = new CopilotClient(options);
        CopilotClientStarter.StartableClient startable = timeoutSec -> {
            try {
                newClient.start().get(timeoutSec, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                throw e;
            }
        };

        try {
            clientStarter.start(startable, copilotConfig.startTimeoutSeconds(), startupErrorFormatter);
        } catch (CopilotCliException e) {
            newClient.close();
            SecurityAuditLogger.log("authentication", "copilot.start", "Copilot client start failed",
                Map.of("outcome", "failure"));
            throw e;
        }

        client = newClient;
        SecurityAuditLogger.log("authentication", "copilot.start", "Copilot client started",
            Map.of("outcome", "success"));
        logger.info("Copilot client initialized");
    }

    private synchronized void shutdown() {
        CopilotClient c = client;
        if (c == null) return;
        client = null;
        try {
            c.close();
            logger.info("Copilot client stopped");
            SecurityAuditLogger.log("authentication", "copilot.stop", "Copilot client stopped",
                Map.of("outcome", "success"));
        } catch (Exception e) {
            logger.warn("Copilot client close raised: {}", e.getMessage(), e);
        }
    }

    private CopilotClientOptions buildOptions(String resolvedPath, String sdkLogLevel) {
        var options = new CopilotClientOptions()
            .setAutoRestart(true)
            .setUseLoggedInUser(true);
        if (resolvedPath != null && !resolvedPath.isBlank()) {
            options.setCliPath(resolvedPath);
        }
        if (sdkLogLevel != null) {
            options.setLogLevel(sdkLogLevel);
        }
        return options;
    }

    private String resolveSdkLogLevel() {
        String fromEnv = System.getenv(SDK_LOG_LEVEL_ENV);
        if (fromEnv != null && SUPPORTED_SDK_LOG_LEVELS.contains(fromEnv.toLowerCase(Locale.ROOT))) {
            return fromEnv.toLowerCase(Locale.ROOT);
        }
        return DEFAULT_SDK_LOG_LEVEL;
    }
}
