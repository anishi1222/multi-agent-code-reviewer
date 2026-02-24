package dev.logicojp.reviewer.util;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Locale;

/// Utility for working with {@link StructuredTaskScope} in preview JDK releases.
///
/// JDK 25's {@code StructuredTaskScope} requires {@code join()} to be called
/// from the owner thread and does not provide a built-in timeout variant.
/// This utility implements timeout by scheduling an interrupt on the owner
/// thread after the deadline expires.
public final class StructuredConcurrencyUtils {

    private StructuredConcurrencyUtils() {
    }

    /// Joins the given scope with a wall-clock timeout.
    /// Must be called from the thread that opened the scope (owner thread).
    ///
    /// Implements timeout by scheduling an interrupt on the calling thread.
    /// If the interrupt fires due to timeout, a {@link TimeoutException} is thrown
    /// instead of {@link InterruptedException}.
    public static <T> void joinWithTimeout(StructuredTaskScope<T, ?> scope,
                                           long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException {
        Thread ownerThread = Thread.currentThread();
        var timedOut = new AtomicBoolean(false);
        var completed = new AtomicBoolean(false);

        Thread timeoutThread = startTimeoutThread(ownerThread, timedOut, completed, timeout, unit);

        try {
            scope.join();
        } catch (InterruptedException e) {
            if (timedOut.get()) {
                Thread.interrupted();
                throw timeoutException(timeout, unit);
            }
            throw e;
        } finally {
            completed.set(true);
            timeoutThread.interrupt();
        }
    }

    private static Thread startTimeoutThread(Thread ownerThread,
                                             AtomicBoolean timedOut,
                                             AtomicBoolean completed,
                                             long timeout,
                                             TimeUnit unit) {
        return Thread.ofVirtual().name("join-timeout").start(() -> {
            try {
                Thread.sleep(unit.toMillis(timeout));
                timedOut.set(true);
                if (!completed.get()) {
                    ownerThread.interrupt();
                }
            } catch (InterruptedException _) {
                // Timeout cancelled — scope.join() completed in time
            }
        });
    }

    private static TimeoutException timeoutException(long timeout, TimeUnit unit) {
        return new TimeoutException("Join timed out after " + timeout + " " + unit.name().toLowerCase(Locale.ROOT));
    }
}
