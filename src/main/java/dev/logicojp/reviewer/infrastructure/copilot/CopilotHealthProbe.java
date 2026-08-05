package dev.logicojp.reviewer.infrastructure.copilot;

import com.github.copilot.ConnectionState;
import com.github.copilot.CopilotClient;
import com.github.copilot.rpc.GetAuthStatusResponse;
import com.github.copilot.rpc.GetStatusResponse;
import dev.logicojp.reviewer.domain.resilience.CopilotCliException;
import dev.logicojp.reviewer.infrastructure.config.CopilotConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Health probe that exercises the Copilot SDK client directly.
///
/// Replaces brownfield {@code service.CopilotHealthProbe} with:
/// - {@link CopilotConfig} instead of {@code CopilotTimeoutResolver}
/// - {@code domain.resilience.CopilotCliException} instead of {@code service.CopilotCliException}
/// - DI-compatible with Micronaut Singleton
@Singleton
public class CopilotHealthProbe {

    private static final Logger logger = LoggerFactory.getLogger(CopilotHealthProbe.class);

    private final CopilotConfig copilotConfig;

    @Inject
    public CopilotHealthProbe(CopilotConfig copilotConfig) {
        this.copilotConfig = Objects.requireNonNull(copilotConfig);
    }

    /// Cheap synchronous health probe — no JSON-RPC roundtrip.
    public boolean isClientHealthy(CopilotClient client) {
        if (client == null) return false;
        try {
            return client.getState() == ConnectionState.CONNECTED;
        } catch (RuntimeException e) {
            logger.debug("getState() raised {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    /// Returns the current SDK connection state.
    public ConnectionState getConnectionState(CopilotClient client) {
        if (client == null) return null;
        try {
            return client.getState();
        } catch (RuntimeException e) {
            logger.debug("getState() raised {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /// Detailed probe — performs the SDK `status` RPC.
    public GetStatusResponse fetchStatus(CopilotClient client) throws InterruptedException {
        requireClient(client);
        long timeout = copilotConfig.cliHealthcheckSeconds();
        return awaitFuture(client.getStatus(), timeout,
            "Copilot SDK status request timed out after ",
            "Copilot SDK status request failed: ");
    }

    /// Detailed probe — performs the SDK `auth status` RPC.
    public GetAuthStatusResponse fetchAuthStatus(CopilotClient client) throws InterruptedException {
        requireClient(client);
        long timeout = copilotConfig.cliAuthcheckSeconds();
        return awaitFuture(client.getAuthStatus(), timeout,
            "Copilot SDK auth-status request timed out after ",
            "Copilot SDK auth-status request failed: ");
    }

    private <T> T awaitFuture(java.util.concurrent.CompletableFuture<T> future,
                               long timeoutSeconds, String timeoutMsg, String failMsg)
        throws InterruptedException {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new CopilotCliException(timeoutMsg + timeoutSeconds + "s", e);
        } catch (ExecutionException e) {
            throw new CopilotCliException(failMsg + (e.getCause() != null
                ? e.getCause().getMessage() : e.getMessage()), e);
        }
    }

    private void requireClient(CopilotClient client) {
        if (client == null) {
            throw new CopilotCliException("Copilot client is not initialized");
        }
    }
}
