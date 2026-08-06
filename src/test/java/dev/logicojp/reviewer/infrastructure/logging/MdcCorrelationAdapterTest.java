package dev.logicojp.reviewer.infrastructure.logging;

import dev.logicojp.reviewer.application.port.outbound.PropagateCorrelationPort;
import dev.logicojp.reviewer.shared.ExecutionCorrelation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Unit tests for the MDC helpers re-homed from {@code util.ExecutionCorrelation}.
///
/// These cover the adapter in isolation; `AgentReviewExecutorTest` and
/// `ReviewExecutionModeRunnerTest` cover it in place, across real thread boundaries.
@DisplayName("MdcCorrelationAdapter")
class MdcCorrelationAdapterTest {

    private static final String KEY = ExecutionCorrelation.EXECUTION_ID_MDC_KEY;

    private final PropagateCorrelationPort adapter = new MdcCorrelationAdapter();

    @BeforeEach
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("execution IDをMDCへバインドする")
    void bindsExecutionId() {
        adapter.bindExecutionId("exec-1");

        assertThat(MDC.get(KEY)).isEqualTo("exec-1");
    }

    @Test
    @DisplayName("nullまたは空白のexecution IDはバインドせず削除する")
    void blankExecutionIdClearsInsteadOfStoringEmpty() {
        MDC.put(KEY, "previous");

        adapter.bindExecutionId("   ");
        assertThat(MDC.get(KEY)).as("blank must remove, not store an empty string").isNull();

        MDC.put(KEY, "previous");
        adapter.bindExecutionId(null);
        assertThat(MDC.get(KEY)).isNull();
    }

    @Test
    @DisplayName("clearExecutionIdはexecution IDのみを削除する")
    void clearRemovesOnlyTheExecutionId() {
        MDC.put(KEY, "exec-1");
        MDC.put("other.key", "keep-me");

        adapter.clearExecutionId();

        assertThat(MDC.get(KEY)).isNull();
        assertThat(MDC.get("other.key")).isEqualTo("keep-me");
    }

    @Test
    @DisplayName("captureContextはライブのMDCから切り離されたコピーを返す")
    void captureReturnsDetachedCopy() {
        MDC.put(KEY, "exec-1");

        Map<String, String> snapshot = adapter.captureContext();
        MDC.put(KEY, "exec-2");

        assertThat(snapshot).containsEntry(KEY, "exec-1");
        assertThat(MDC.get(KEY)).isEqualTo("exec-2");
    }

    @Test
    @DisplayName("MDCが空のときcaptureContextはnullを返す")
    void captureReturnsNullWhenEmpty() {
        assertThat(adapter.captureContext()).isNull();
    }

    @Test
    @DisplayName("callWithContextは別スレッドへコンテキストを移送する")
    void callWithContextCarriesTheContextAcrossThreads() throws Exception {
        adapter.bindExecutionId("exec-1");
        Map<String, String> parent = adapter.captureContext();
        AtomicReference<String> seenInChild = new AtomicReference<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> adapter.callWithContext(parent, () -> {
                seenInChild.set(MDC.get(KEY));
                return null;
            })).get();
        }

        assertThat(seenInChild.get()).isEqualTo("exec-1");
    }

    @Test
    @DisplayName("callWithContextは呼び出し前のコンテキストを復元する")
    void callWithContextRestoresPreviousContext() throws Exception {
        MDC.put(KEY, "outer");

        adapter.callWithContext(Map.of(KEY, "inner"), () -> {
            assertThat(MDC.get(KEY)).isEqualTo("inner");
            return null;
        });

        assertThat(MDC.get(KEY)).isEqualTo("outer");
    }

    @Test
    @DisplayName("supplierが例外を投げてもコンテキストは復元される")
    void restoresContextEvenWhenSupplierThrows() {
        MDC.put(KEY, "outer");

        assertThatThrownBy(() -> adapter.callWithContext(Map.of(KEY, "inner"), () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(KEY))
            .as("a borrowed pool thread must never be left carrying another task's context")
            .isEqualTo("outer");
    }

    @Test
    @DisplayName("nullコンテキストを渡すと実行中のMDCはクリアされる")
    void nullContextClearsDuringTheCall() throws Exception {
        MDC.put(KEY, "outer");

        adapter.callWithContext(null, () -> {
            assertThat(MDC.get(KEY)).isNull();
            return null;
        });

        assertThat(MDC.get(KEY)).isEqualTo("outer");
    }

    @Test
    @DisplayName("callWithCurrentContextは現在のコンテキストを引き継ぐ")
    void callWithCurrentContextUsesTheLiveContext() throws Exception {
        adapter.bindExecutionId("exec-1");

        String seen = adapter.callWithCurrentContext(() -> MDC.get(KEY));

        assertThat(seen).isEqualTo("exec-1");
    }
}
