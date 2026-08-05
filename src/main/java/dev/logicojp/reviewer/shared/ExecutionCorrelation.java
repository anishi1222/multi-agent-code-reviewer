package dev.logicojp.reviewer.shared;

import java.util.UUID;

/// Utilities for review execution correlation IDs.
///
/// The MDC-propagation methods from the original {@code util.ExecutionCorrelation}
/// are intentionally omitted here: SLF4J MDC is an infrastructure concern.
/// Those methods remain in the original class until the logging infrastructure
/// layer (T010) takes them over.
///
/// Only the pieces that are genuinely shared (UUID generation, the key constant,
/// and the {@link CheckedSupplier} contract) are present in this layer.
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
