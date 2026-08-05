package dev.logicojp.reviewer.infrastructure.auth;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

/// Shared retry policy helpers (transient-fault detection and backoff calculation).
public final class RetryPolicyUtils {

    private RetryPolicyUtils() {
    }

    public static long computeBackoffWithJitter(long baseBackoffMs, long maxBackoffMs, int attempt) {
        long boundedBaseMs = Math.min(baseBackoffMs << Math.max(0, attempt - 1), maxBackoffMs);
        long jitterMs = ThreadLocalRandom.current().nextLong((boundedBaseMs / 2) + 1);
        return Math.min(boundedBaseMs + jitterMs, maxBackoffMs);
    }

    public static boolean shouldRetry(int attempt, int totalAttempts, boolean retryable) {
        return retryable && attempt < totalAttempts;
    }

    public static void sleepWithBackoff(long backoffBaseMs, long backoffMaxMs, int attempt) {
        long backoffMs = computeBackoffWithJitter(backoffBaseMs, backoffMaxMs, attempt);
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean isTransientException(Throwable throwable) {
        Throwable rootCause = unwrap(throwable);
        if (rootCause instanceof TimeoutException) return true;
        if (rootCause instanceof IOException) return true;
        if (rootCause instanceof InterruptedException) return false;
        String msg = rootCause != null ? rootCause.getMessage() : null;
        if (msg == null) return false;
        String lower = msg.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("connection reset") || lower.contains("timeout") || lower.contains("unavailable")
            || lower.contains("stream closed") || lower.contains("broken pipe");
    }

    private static Throwable unwrap(Throwable t) {
        if (t instanceof ExecutionException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }
}
