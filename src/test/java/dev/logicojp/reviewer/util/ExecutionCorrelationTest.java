package dev.logicojp.reviewer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExecutionCorrelation")
class ExecutionCorrelationTest {

    @Nested
    @DisplayName("execution ID MDC operations")
    class ExecutionIdMdcOperations {

        @Test
        @DisplayName("execution IDをMDCに設定してクリアできる")
        void putAndClearExecutionId() {
            ExecutionCorrelation.putExecutionId("exec-1");
            assertThat(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY)).isEqualTo("exec-1");

            ExecutionCorrelation.clearExecutionId();
            assertThat(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY)).isNull();
        }

        @Test
        @DisplayName("空文字のexecution IDはMDCキーを削除する")
        void blankExecutionIdRemovesMdcKey() {
            ExecutionCorrelation.putExecutionId("exec-1");
            ExecutionCorrelation.putExecutionId(" ");
            assertThat(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY)).isNull();
        }
    }

    @Nested
    @DisplayName("MDC context propagation")
    class MdcContextPropagation {

        @Test
        @DisplayName("callWithCurrentMdcは別スレッド実行中にMDCを引き継ぐ")
        void callWithCurrentMdcPropagatesContext() throws Exception {
            AtomicReference<String> captured = new AtomicReference<>();

            try {
                ExecutionCorrelation.putExecutionId("exec-propagate");
                ExecutionCorrelation.callWithCurrentMdc(() -> {
                    captured.set(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY));
                    return null;
                });
                assertThat(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY)).isEqualTo("exec-propagate");
            } finally {
                ExecutionCorrelation.clearExecutionId();
            }

            assertThat(captured.get()).isEqualTo("exec-propagate");
            assertThat(MDC.get(ExecutionCorrelation.EXECUTION_ID_MDC_KEY)).isNull();
        }
    }
}
