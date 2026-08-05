package dev.logicojp.reviewer.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionCorrelationTest {

    @Test
    void generateExecutionId_returnsNonBlankUuid() {
        String id = ExecutionCorrelation.generateExecutionId();
        assertNotNull(id);
        assertFalse(id.isBlank());
        // UUID format: 8-4-4-4-12 hex chars separated by dashes
        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
            "Expected UUID format but got: " + id);
    }

    @Test
    void generateExecutionId_returnsUniqueValues() {
        String id1 = ExecutionCorrelation.generateExecutionId();
        String id2 = ExecutionCorrelation.generateExecutionId();
        assertNotEquals(id1, id2, "Each call should produce a unique ID");
    }

    @Test
    void executionIdMdcKey_isConstantNonBlank() {
        assertNotNull(ExecutionCorrelation.EXECUTION_ID_MDC_KEY);
        assertFalse(ExecutionCorrelation.EXECUTION_ID_MDC_KEY.isBlank());
    }

    @Test
    void checkedSupplier_wrapsCallable() throws Exception {
        ExecutionCorrelation.CheckedSupplier<String> supplier = () -> "hello";
        assertEquals("hello", supplier.get());
    }

    @Test
    void checkedSupplier_propagatesException() {
        ExecutionCorrelation.CheckedSupplier<String> failing = () -> {
            throw new Exception("test-failure");
        };
        assertThrows(Exception.class, failing::get);
    }
}
