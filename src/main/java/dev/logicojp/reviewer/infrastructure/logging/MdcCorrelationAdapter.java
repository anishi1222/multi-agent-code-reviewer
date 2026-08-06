package dev.logicojp.reviewer.infrastructure.logging;

import dev.logicojp.reviewer.application.port.outbound.PropagateCorrelationPort;
import dev.logicojp.reviewer.shared.ExecutionCorrelation;
import jakarta.inject.Singleton;
import org.slf4j.MDC;

import java.util.Map;

/// SLF4J MDC-backed implementation of {@link PropagateCorrelationPort}.
///
/// This adapter re-homes the MDC propagation helpers that previously lived as static
/// methods on {@code util.ExecutionCorrelation}. They were dropped during the layer
/// migration because the application layer may not import a logging framework; moving
/// them behind a port restores the capability without reintroducing that dependency.
///
/// The logback configuration renders `%X{execution.id}` in every pattern, so binding
/// the key here is what makes agent/pass log lines attributable to a CLI invocation.
@Singleton
public final class MdcCorrelationAdapter implements PropagateCorrelationPort {

    @Override
    public void bindExecutionId(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            MDC.remove(ExecutionCorrelation.EXECUTION_ID_MDC_KEY);
            return;
        }
        MDC.put(ExecutionCorrelation.EXECUTION_ID_MDC_KEY, executionId);
    }

    @Override
    public void clearExecutionId() {
        MDC.remove(ExecutionCorrelation.EXECUTION_ID_MDC_KEY);
    }

    @Override
    public Map<String, String> captureContext() {
        // MDC.getCopyOfContextMap() already returns a defensive copy (or null when empty).
        return MDC.getCopyOfContextMap();
    }

    @Override
    public <T> T callWithContext(Map<String, String> context, ExecutionCorrelation.CheckedSupplier<T> supplier)
        throws Exception {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            applyContext(context);
            return supplier.get();
        } finally {
            // Restore unconditionally: pooled and virtual-carrier threads must never be
            // handed back to the executor carrying another task's context.
            applyContext(previous);
        }
    }

    private static void applyContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(context);
    }
}
