package dev.logicojp.reviewer.util;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

/// Utility for review execution correlation ID and MDC propagation.
public final class ExecutionCorrelation {

    public static final String EXECUTION_ID_MDC_KEY = "execution.id";

    private ExecutionCorrelation() {
    }

    public static String generateExecutionId() {
        return UUID.randomUUID().toString();
    }

    public static void putExecutionId(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            MDC.remove(EXECUTION_ID_MDC_KEY);
            return;
        }
        MDC.put(EXECUTION_ID_MDC_KEY, executionId);
    }

    public static void clearExecutionId() {
        MDC.remove(EXECUTION_ID_MDC_KEY);
    }

    public static Map<String, String> captureMdcContext() {
        return MDC.getCopyOfContextMap();
    }

    public static <T> T callWithCurrentMdc(CheckedSupplier<T> supplier) throws Exception {
        return callWithMdcContext(captureMdcContext(), supplier);
    }

    public static <T> T callWithMdcContext(Map<String, String> contextMap, CheckedSupplier<T> supplier) throws Exception {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            applyContext(contextMap);
            return supplier.get();
        } finally {
            applyContext(previous);
        }
    }

    private static void applyContext(Map<String, String> contextMap) {
        if (contextMap == null || contextMap.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(contextMap);
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
