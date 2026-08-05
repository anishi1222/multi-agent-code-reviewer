package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.domain.resilience.CopilotCliException;
import dev.logicojp.reviewer.infrastructure.config.CopilotConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CopilotHealthProbe")
class CopilotHealthProbeTest {

    private final CopilotHealthProbe probe = new CopilotHealthProbe(new CopilotConfig(null, null, 60, 10, 15));

    @Test
    @DisplayName("nullクライアントはhealthyではない")
    void nullClientIsNotHealthy() {
        assertThat(probe.isClientHealthy(null)).isFalse();
    }

    @Test
    @DisplayName("nullクライアントの接続状態はnull")
    void nullClientStateIsNull() {
        assertThat(probe.getConnectionState(null)).isNull();
    }

    @Test
    @DisplayName("nullクライアントへのstatus要求はCopilotCliExceptionとなる")
    void fetchStatusRequiresClient() {
        assertThatThrownBy(() -> probe.fetchStatus(null))
            .isInstanceOf(CopilotCliException.class)
            .hasMessageContaining("not initialized");
    }

    @Test
    @DisplayName("nullクライアントへのauthStatus要求はCopilotCliExceptionとなる")
    void fetchAuthStatusRequiresClient() {
        assertThatThrownBy(() -> probe.fetchAuthStatus(null))
            .isInstanceOf(CopilotCliException.class)
            .hasMessageContaining("not initialized");
    }

    @Test
    @DisplayName("awaitFuture: 完了済みfutureは値を返す")
    void awaitFutureReturnsCompletedValue() throws Exception {
        CompletableFuture<String> future = CompletableFuture.completedFuture("ok");
        String value = awaitFuture(future, 5, "timeout: ", "failed: ");

        assertThat(value).isEqualTo("ok");
    }

    @Test
    @DisplayName("awaitFuture: 失敗futureはCopilotCliExceptionに変換する")
    void awaitFutureWrapsExceptionalCompletion() {
        CompletableFuture<String> future = CompletableFuture.failedFuture(new IllegalStateException("boom"));

        assertThatThrownBy(() -> awaitFuture(future, 5, "timeout: ", "failed: "))
            .isInstanceOf(CopilotCliException.class)
            .hasMessageContaining("failed: boom");
    }

    @Test
    @DisplayName("awaitFuture: タイムアウトはCopilotCliExceptionに変換する")
    void awaitFutureTimesOut() {
        CompletableFuture<String> future = new CompletableFuture<>();

        assertThatThrownBy(() -> awaitFuture(future, 0, "timeout after ", "failed: "))
            .isInstanceOf(CopilotCliException.class)
            .hasMessageContaining("timeout after 0s");
    }

    @SuppressWarnings("unchecked")
    private <T> T awaitFuture(CompletableFuture<T> future,
                              long timeoutSeconds,
                              String timeoutMessage,
                              String failureMessage) throws Exception {
        Method method = CopilotHealthProbe.class.getDeclaredMethod(
            "awaitFuture", CompletableFuture.class, long.class, String.class, String.class);
        method.setAccessible(true);
        try {
            return (T) method.invoke(probe, future, timeoutSeconds, timeoutMessage, failureMessage);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }
}
