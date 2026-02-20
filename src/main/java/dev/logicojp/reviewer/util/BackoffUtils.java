package dev.logicojp.reviewer.util;

import java.util.concurrent.ThreadLocalRandom;

/// Utility methods for retry backoff with jitter.
public final class BackoffUtils {

    private BackoffUtils() {
    }

    public static void sleepWithJitter(int attempt, long baseMs, long maxMs) throws InterruptedException {
        long exponentialMs = Math.min(baseMs << Math.max(0, attempt - 1), maxMs);
        long jitteredMs = ThreadLocalRandom.current().nextLong(exponentialMs + 1);
        Thread.sleep(jitteredMs);
    }

    public static void sleepWithJitterQuietly(int attempt, long baseMs, long maxMs) {
        try {
            sleepWithJitter(attempt, baseMs, maxMs);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}