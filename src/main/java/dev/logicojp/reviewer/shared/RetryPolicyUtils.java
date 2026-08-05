package dev.logicojp.reviewer.shared;

import java.io.IOException;
import java.util.Locale;
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

    /// Determines whether a retry should be attempted.
    public static boolean shouldRetry(int attempt, int totalAttempts, boolean retryable) {
        return retryable && attempt < totalAttempts;
    }

    /// Sleeps for a jittered exponential backoff duration.
    /// Interrupts the current thread if sleep is interrupted.
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

        if (rootCause instanceof TimeoutException) {
            return true;
        }
        if (rootCause instanceof IOException) {
            return true;
        }

        return isTransientMessage(rootCause.getMessage());
    }

    public static boolean isRetryableFailureMessage(String message, String... additionalNonRetryableMarkers) {
        if (message == null || message.isBlank()) {
            return true;
        }
        String lower = message.toLowerCase(Locale.ROOT);

        if (containsAny(lower,
            "unauthorized", "forbidden", "invalid token", "authentication",
            "invalid model", "bad request", "400", "401", "403", "404")) {
            return false;
        }

        return !containsAny(lower, additionalNonRetryableMarkers);
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof ExecutionException executionException && executionException.getCause() != null) {
            return executionException.getCause();
        }
        return throwable;
    }

    private static boolean isTransientMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return containsAny(lower,
            "timeout",
            "temporarily",
            "rate limit",
            "too many requests",
            "429",
            "503",
            "connection reset",
            "network"
        );
    }

    private static boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (marker != null && !marker.isBlank() && value.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
