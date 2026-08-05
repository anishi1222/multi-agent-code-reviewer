package dev.logicojp.reviewer.shared;

import java.util.UUID;

/// Utilities for review execution correlation IDs.
///
/// This class is deliberately JDK-only. The MDC propagation helpers that the original
/// {@code util.ExecutionCorrelation} carried now live behind
/// {@code application.port.outbound.PropagateCorrelationPort}, implemented by
/// {@code infrastructure.logging.MdcCorrelationAdapter} — SLF4J MDC is an
/// infrastructure concern and must not leak into `shared` or `application`.
///
/// What remains here is what is genuinely shared and framework-free: the MDC key
/// constant (a naming contract, not an SLF4J dependency), UUID generation, and the
/// {@link CheckedSupplier} contract used by the port's call wrappers.
public final class ExecutionCorrelation {

    /// MDC key used to propagate execution IDs across log statements.
    public static final String EXECUTION_ID_MDC_KEY = "execution.id";

    private ExecutionCorrelation() {
    }

    /// Generates a new unique execution ID.
    public static String generateExecutionId() {
        return UUID.randomUUID().toString();
    }

    /// A {@link java.util.concurrent.Callable}-like functional interface that
    /// may throw a checked exception, used for MDC-aware call wrappers.
    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
