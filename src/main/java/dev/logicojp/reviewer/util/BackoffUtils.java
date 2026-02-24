package dev.logicojp.reviewer.util;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/// Utility methods for retry backoff with jitter and retryability checks.
public final class BackoffUtils {

    private BackoffUtils() {
    }

    public static void sleepWithJitter(int attempt, long baseMs, long maxMs) throws InterruptedException {
        int safeShift = Math.min(Math.max(0, attempt - 1), 62);
        long exponentialMs = Math.min(baseMs << safeShift, maxMs);
        long halfMs = Math.max(1L, exponentialMs / 2L);
        long jitteredMs = halfMs + ThreadLocalRandom.current().nextLong(halfMs + 1L);
        Thread.sleep(jitteredMs);
    }

    public static void sleepWithJitterQuietly(int attempt, long baseMs, long maxMs) {
        try {
            sleepWithJitter(attempt, baseMs, maxMs);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    /// Determines if an error message indicates a transient failure eligible for retry.
    public static boolean isRetryableMessage(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("timeout")
            || lower.contains("timed out")
            || lower.contains("rate")
            || lower.contains("429")
            || lower.contains("tempor")
            || lower.contains("network")
            || lower.contains("connection")
            || lower.contains("unavailable");
    }
}