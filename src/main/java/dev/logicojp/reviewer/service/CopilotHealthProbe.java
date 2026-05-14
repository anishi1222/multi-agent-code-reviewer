package dev.logicojp.reviewer.service;

import com.github.copilot.sdk.ConnectionState;
import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.GetAuthStatusResponse;
import com.github.copilot.sdk.json.GetStatusResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Health probe that exercises the Copilot SDK client directly instead of
/// spawning a CLI subprocess. Provides a cheap synchronous probe based on
/// {@link CopilotClient#getState()} and richer JSON-RPC probes via
/// {@link CopilotClient#getStatus()} / {@link CopilotClient#getAuthStatus()}
/// for diagnostic commands such as `review doctor`.
@Singleton
public class CopilotHealthProbe {

    private static final Logger logger = LoggerFactory.getLogger(CopilotHealthProbe.class);

    private final CopilotTimeoutResolver timeoutResolver;

    @Inject
    public CopilotHealthProbe(CopilotTimeoutResolver timeoutResolver) {
        this.timeoutResolver = timeoutResolver;
    }

    /// Cheap synchronous health probe.
    ///
    /// Returns {@code true} when the underlying SDK client reports
    /// {@link ConnectionState#CONNECTED}. No JSON-RPC roundtrip is performed.
    public boolean isClientHealthy(CopilotClient client) {
        if (client == null) {
            return false;
        }
        ConnectionState state;
        try {
            state = client.getState();
        } catch (RuntimeException e) {
            logger.debug("Copilot client getState() raised {}: {}",
                e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
        return state == ConnectionState.CONNECTED;
    }

    /// Returns the underlying SDK connection state, or {@code null} when the
    /// client is uninitialized or the call raises an exception.
    public ConnectionState getConnectionState(CopilotClient client) {
        if (client == null) {
            return null;
        }
        try {
            return client.getState();
        } catch (RuntimeException e) {
            logger.debug("Copilot client getState() raised {}: {}",
                e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /// Detailed probe used by diagnostics. Performs the SDK `status` RPC.
    /// Throws {@link CopilotCliException} on timeout, transport error, or
    /// non-success response.
    public GetStatusResponse fetchStatus(CopilotClient client) throws InterruptedException {
        requireClient(client);
        long timeoutSeconds = timeoutResolver.resolveSdkStatusTimeoutSeconds();
        return awaitFuture(client.getStatus(), timeoutSeconds,
            "Copilot SDK status request timed out after ",
            "Copilot SDK status request failed: ");
    }

    /// Detailed probe used by diagnostics. Performs the SDK `auth status` RPC.
    /// Throws {@link CopilotCliException} on timeout, transport error, or
    /// non-success response.
    public GetAuthStatusResponse fetchAuthStatus(CopilotClient client) throws InterruptedException {
        requireClient(client);
        long timeoutSeconds = timeoutResolver.resolveSdkAuthStatusTimeoutSeconds();
        return awaitFuture(client.getAuthStatus(), timeoutSeconds,
            "Copilot SDK auth status request timed out after ",
            "Copilot SDK auth status request failed: ");
    }

    private static void requireClient(CopilotClient client) {
        if (client == null) {
            throw new IllegalStateException(
                "CopilotClient is not initialized. Call CopilotService.initializeOrThrow() first.");
        }
    }

    static <T> T awaitFuture(CompletableFuture<T> future,
                             long timeoutSeconds,
                             String timeoutPrefix,
                             String failurePrefix) throws InterruptedException {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new CopilotCliException(timeoutPrefix + timeoutSeconds + "s. "
                + "Verify GitHub Copilot CLI installation and authentication.", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String detail = cause != null && cause.getMessage() != null
                ? cause.getMessage()
                : e.getMessage();
            throw new CopilotCliException(failurePrefix + detail, cause != null ? cause : e);
        }
    }
}
