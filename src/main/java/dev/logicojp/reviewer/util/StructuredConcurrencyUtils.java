package dev.logicojp.reviewer.util;

import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/// Utility for working with {@link StructuredTaskScope} in preview JDK releases.
///
/// JDK 27's {@code StructuredTaskScope} requires {@code join()} to be called
/// from the owner thread and does not provide a built-in timeout variant.
/// This utility implements timeout by scheduling an interrupt on the owner
/// thread after the deadline expires.
public final class StructuredConcurrencyUtils {

    private StructuredConcurrencyUtils() {
    }

    /// Opens a scope that waits for all subtasks without fail-fast cancellation.
    ///
    /// Using {@code allUntil(subtask -> false)} preserves the previous behavior
    /// where callers inspect each subtask state after join completes.
    public static <T> StructuredTaskScope<T, List<StructuredTaskScope.Subtask<T>>, RuntimeException> openAwaitAllScope() {
        return StructuredTaskScope.open(StructuredTaskScope.Joiner.allUntil(subtask -> false));
    }

    /// Joins the given scope with a wall-clock timeout.
    /// Must be called from the thread that opened the scope (owner thread).
    ///
    /// Implements timeout by scheduling an interrupt on the calling thread.
    /// If the interrupt fires due to timeout, a {@link TimeoutException} is thrown
    /// instead of {@link InterruptedException}.
    ///
    /// @param scope the structured task scope to join
    /// @param timeout the maximum time to wait
    /// @param unit the time unit for the timeout
    /// @throws InterruptedException if the current thread is interrupted (non-timeout)
    /// @throws TimeoutException if the join does not complete within the timeout
    public static <T, R, R_X extends Throwable> void joinWithTimeout(StructuredTaskScope<T, R, R_X> scope,
                                                                      long timeout,
                                                                      TimeUnit unit)
            throws InterruptedException, TimeoutException, R_X {
        Thread ownerThread = Thread.currentThread();
        var timedOut = new AtomicBoolean(false);

        // Schedule an interrupt on the owner thread after the timeout
        Thread timeoutThread = startTimeoutThread(ownerThread, timedOut, timeout, unit);

        try {
            scope.join();
        } catch (InterruptedException e) {
            if (timedOut.get()) {
                // Clear the interrupt flag since we are converting to TimeoutException
                Thread.interrupted();
                throw timeoutException(timeout, unit);
            }
            throw e;
        } finally {
            timeoutThread.interrupt();
        }
    }

    private static Thread startTimeoutThread(Thread ownerThread,
                                             AtomicBoolean timedOut,
                                             long timeout,
                                             TimeUnit unit) {
        return Thread.ofVirtual().name("join-timeout").start(() -> {
            try {
                Thread.sleep(unit.toMillis(timeout));
                timedOut.set(true);
                ownerThread.interrupt();
            } catch (InterruptedException _) {
                // Timeout cancelled — scope.join() completed in time
            }
        });
    }

    private static TimeoutException timeoutException(long timeout, TimeUnit unit) {
        return new TimeoutException("Join timed out after " + timeout + " " + unit.name().toLowerCase());
    }
}
