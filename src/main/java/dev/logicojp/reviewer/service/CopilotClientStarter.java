package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.util.RetryPolicyUtils;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/// Starts the Copilot SDK client with retry and exponential backoff.
///
/// Retries are limited because the SDK is configured with
/// {@link com.github.copilot.sdk.json.CopilotClientOptions#setAutoRestart(boolean)
/// setAutoRestart(true)} (see {@link CopilotService}), which handles
/// CLI subprocess restarts after a successful initial connection. This starter
/// only needs to absorb transient *initial* connect failures.
@Singleton
public class CopilotClientStarter {

    interface StartableClient {
        void start(long timeoutSeconds) throws ExecutionException, TimeoutException, InterruptedException;

        void close() throws Exception;
    }

    private static final Logger logger = LoggerFactory.getLogger(CopilotClientStarter.class);
    private static final int MAX_START_ATTEMPTS = 2;
    private static final long START_BACKOFF_BASE_MS = 2_000L;
    private static final long START_BACKOFF_MAX_MS = 15_000L;

    public void start(StartableClient client,
                      long timeoutSeconds,
                      CopilotStartupErrorFormatter startupErrorFormatter)
        throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_START_ATTEMPTS; attempt++) {
            try {
                client.start(timeoutSeconds);
                return;
            } catch (ExecutionException e) {
                if (retryWithBackoffIfNeeded(
                    attempt,
                    RetryPolicyUtils.isTransientException(e),
                    "failed",
                    e.getMessage()
                )) {
                    continue;
                }
                closeQuietly(client);
                throw mapExecutionException(e, startupErrorFormatter);
            } catch (TimeoutException e) {
                if (retryWithBackoffIfNeeded(attempt, true, "timed out", null)) {
                    continue;
                }
                closeQuietly(client);
                throw timeoutDuringStart(timeoutSeconds, startupErrorFormatter, e);
            } catch (InterruptedException e) {
                closeQuietly(client);
                throw e;
            }
        }
    }

    private boolean retryWithBackoffIfNeeded(int attempt,
                                             boolean transientFailure,
                                             String context,
                                             String detail) throws InterruptedException {
        if (!RetryPolicyUtils.shouldRetry(attempt, MAX_START_ATTEMPTS, transientFailure)) {
            return false;
        }

        long backoff = RetryPolicyUtils.computeBackoffWithJitter(
            START_BACKOFF_BASE_MS,
            START_BACKOFF_MAX_MS,
            attempt
        );
        if (detail == null || detail.isBlank()) {
            logger.warn("Copilot client start {} (attempt {}/{}), retrying in {}ms",
                context, attempt, MAX_START_ATTEMPTS, backoff);
        } else {
            logger.warn("Copilot client start {} (attempt {}/{}), retrying in {}ms: {}",
                context, attempt, MAX_START_ATTEMPTS, backoff, detail);
        }
        Thread.sleep(backoff);
        return true;
    }

    private CopilotCliException mapExecutionException(ExecutionException e,
                                                     CopilotStartupErrorFormatter startupErrorFormatter) {
        Throwable cause = e.getCause();
        if (cause instanceof TimeoutException) {
            return new CopilotCliException(startupErrorFormatter.buildProtocolTimeoutMessage(), cause);
        }
        if (cause != null) {
            return new CopilotCliException("Copilot client start failed: " + cause.getMessage(), cause);
        }
        return new CopilotCliException("Copilot client start failed", e);
    }

    private CopilotCliException timeoutDuringStart(long timeoutSeconds,
                                                  CopilotStartupErrorFormatter startupErrorFormatter,
                                                  TimeoutException e) {
        return new CopilotCliException(startupErrorFormatter.buildClientTimeoutMessage(timeoutSeconds), e);
    }

    private void closeQuietly(StartableClient client) {
        try {
            client.close();
        } catch (Exception e) {
            logger.debug("Failed to close Copilot client after startup failure: {}", e.getMessage(), e);
        }
    }
}
