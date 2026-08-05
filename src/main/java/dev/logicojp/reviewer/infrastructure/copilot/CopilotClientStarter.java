package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.domain.resilience.CopilotCliException;
import dev.logicojp.reviewer.infrastructure.auth.RetryPolicyUtils;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/// Starts the Copilot SDK client with retry and exponential backoff.
///
/// Phase 3a verification confirmed that {@code setAutoRestart(true)} is currently a
/// no-op in SDK {@code 0.3.0-java.2}. We therefore retain three start attempts to
/// absorb transient initial-connect failures.
@Singleton
public class CopilotClientStarter {

    @FunctionalInterface
    interface StartableClient {
        void start(long timeoutSeconds) throws ExecutionException, TimeoutException, InterruptedException;
        default void close() {}
    }

    private static final Logger logger = LoggerFactory.getLogger(CopilotClientStarter.class);
    private static final int MAX_START_ATTEMPTS = 3;
    private static final long START_BACKOFF_BASE_MS = 2_000L;
    private static final long START_BACKOFF_MAX_MS = 15_000L;

    public void start(StartableClient client,
                      long timeoutSeconds,
                      CopilotStartupErrorFormatter startupErrorFormatter) throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_START_ATTEMPTS; attempt++) {
            try {
                client.start(timeoutSeconds);
                return;
            } catch (ExecutionException e) {
                if (retryWithBackoff(attempt, RetryPolicyUtils.isTransientException(e), "failed", e.getMessage())) {
                    continue;
                }
                client.close();
                throw mapExecutionException(e, startupErrorFormatter);
            } catch (TimeoutException e) {
                if (retryWithBackoff(attempt, true, "timed out", null)) {
                    continue;
                }
                client.close();
                throw new CopilotCliException(
                    startupErrorFormatter.buildClientTimeoutMessage(timeoutSeconds), e);
            } catch (InterruptedException e) {
                client.close();
                throw e;
            }
        }
    }

    private boolean retryWithBackoff(int attempt, boolean transient_, String ctx, String detail)
        throws InterruptedException {
        if (!RetryPolicyUtils.shouldRetry(attempt, MAX_START_ATTEMPTS, transient_)) return false;
        long backoff = RetryPolicyUtils.computeBackoffWithJitter(
            START_BACKOFF_BASE_MS, START_BACKOFF_MAX_MS, attempt);
        if (detail == null || detail.isBlank()) {
            logger.warn("Copilot client start {} (attempt {}/{}), retrying in {}ms",
                ctx, attempt, MAX_START_ATTEMPTS, backoff);
        } else {
            logger.warn("Copilot client start {} (attempt {}/{}), retrying in {}ms: {}",
                ctx, attempt, MAX_START_ATTEMPTS, backoff, detail);
        }
        Thread.sleep(backoff);
        return true;
    }

    private CopilotCliException mapExecutionException(ExecutionException e,
                                                       CopilotStartupErrorFormatter fmt) {
        Throwable cause = e.getCause();
        if (cause instanceof TimeoutException) {
            return new CopilotCliException(fmt.buildProtocolTimeoutMessage(), cause);
        }
        return cause != null
            ? new CopilotCliException("Copilot client start failed: " + cause.getMessage(), cause)
            : new CopilotCliException("Copilot client start failed", e);
    }
}
