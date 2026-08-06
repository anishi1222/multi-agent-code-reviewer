package dev.logicojp.reviewer.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for the surviving [ExecutionCorrelation] contract.
///
/// NOTE: the MDC-oriented tests that previously lived here
/// (`putAndClearExecutionId`, `blankExecutionIdRemovesMdcKey`,
/// `callWithCurrentMdcPropagatesContext`, `callWithMdcContext*`) were removed during the
/// layered-architecture migration (T013): `ExecutionCorrelation` moved into the `shared`
/// layer, which forbids SLF4J, so `putExecutionId` / `clearExecutionId` /
/// `captureMdcContext` / `callWithCurrentMdc` / `callWithMdcContext` no longer exist and
/// were never rehomed into an infrastructure adapter. See the T013 artifact for the
/// escalation — this is a production behaviour regression, not merely a test cleanup.
@DisplayName("ExecutionCorrelation")
class ExecutionCorrelationTest {

    @Nested
    @DisplayName("execution ID generation")
    class ExecutionIdGeneration {

        @Test
        @DisplayName("execution IDを生成できる")
        void generatesNonBlankExecutionId() {
            String executionId = ExecutionCorrelation.generateExecutionId();

            assertThat(executionId).isNotNull().isNotBlank();
        }

        @RepeatedTest(20)
        @DisplayName("生成されるexecution IDは呼び出しごとに異なる")
        void generatesDistinctExecutionIds() {
            Set<String> generated = new HashSet<>();
            for (int i = 0; i < 50; i++) {
                generated.add(ExecutionCorrelation.generateExecutionId());
            }

            assertThat(generated).hasSize(50);
        }
    }

    @Nested
    @DisplayName("MDC key contract")
    class MdcKeyContract {

        @Test
        @DisplayName("MDCキーはlogback設定と一致する安定した値である")
        void exposesStableMdcKey() {
            assertThat(ExecutionCorrelation.EXECUTION_ID_MDC_KEY).isEqualTo("execution.id");
        }
    }
}
