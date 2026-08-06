package dev.logicojp.reviewer.application.port.outbound;

import dev.logicojp.reviewer.shared.ExecutionCorrelation;

import java.util.Map;

/// Outbound port for propagating the diagnostic correlation context (notably the
/// execution ID, see {@link ExecutionCorrelation#EXECUTION_ID_MDC_KEY}) across
/// thread boundaries.
///
/// ## Why this is a port
///
/// The review pipeline hands work to virtual threads (`ExecutorService.submit`,
/// `StructuredTaskScope.fork`). Diagnostic context in every mainstream Java logging
/// backend is thread-local, so a child thread starts with an *empty* context and its
/// log lines lose the execution ID that ties them back to the CLI invocation.
/// The application layer therefore needs a way to carry the parent's context into the
/// child — but *how* that context is stored (SLF4J MDC, Log4j2 ThreadContext, JUL, …)
/// is an infrastructure concern the application layer must not know about.
///
/// Declaring the capability here keeps `application.*` free of logging-framework
/// imports (architecture Rule 2 / Rule 5) while preserving the behaviour end to end.
/// The MDC-backed implementation lives in
/// {@code infrastructure.logging.MdcCorrelationAdapter}.
///
/// ## Contract
///
/// Implementations MUST be thread-safe and MUST restore the caller's original context
/// when {@link #callWithContext(Map, ExecutionCorrelation.CheckedSupplier)} returns —
/// whether normally or exceptionally — so that borrowed pool threads are never left
/// polluted with a previous task's context.
public interface PropagateCorrelationPort {

    /// Binds the given execution ID to the current thread's correlation context.
    ///
    /// A null or blank value clears the binding rather than recording an empty ID.
    void bindExecutionId(String executionId);

    /// Removes the execution ID from the current thread's correlation context.
    void clearExecutionId();

    /// Returns a snapshot of the current thread's correlation context, or `null`
    /// when no context is set.
    ///
    /// The returned map is a copy: mutating it must not affect the live context.
    Map<String, String> captureContext();

    /// Runs `supplier` with `context` installed as the correlation context, restoring
    /// the previously installed context afterwards.
    ///
    /// This is the method that actually crosses the thread boundary: capture on the
    /// parent thread, then call this on the child thread.
    ///
    /// @param context  the context to install; `null` or empty clears the context
    /// @param supplier the work to run
    /// @return whatever `supplier` returns
    /// @throws Exception whatever `supplier` throws
    <T> T callWithContext(Map<String, String> context, ExecutionCorrelation.CheckedSupplier<T> supplier)
        throws Exception;

    /// Convenience wrapper that captures the current context and immediately runs
    /// `supplier` under it. Useful when the capture and the call happen on the same
    /// thread and only the restore semantics are wanted.
    default <T> T callWithCurrentContext(ExecutionCorrelation.CheckedSupplier<T> supplier) throws Exception {
        return callWithContext(captureContext(), supplier);
    }
}
