package dev.logicojp.reviewer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CopilotHealthProbe")
class CopilotHealthProbeTest {

    private final CopilotHealthProbe probe = new CopilotHealthProbe(new CopilotTimeoutResolver());

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
    @DisplayName("nullクライアントへのstatus要求はIllegalStateExceptionとなる")
    void fetchStatusRequiresClient() {
        assertThatThrownBy(() -> probe.fetchStatus(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not initialized");
    }

    @Test
    @DisplayName("nullクライアントへのauthStatus要求はIllegalStateExceptionとなる")
    void fetchAuthStatusRequiresClient() {
        assertThatThrownBy(() -> probe.fetchAuthStatus(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not initialized");
    }

    @Test
    @DisplayName("awaitFuture: 完了済みfutureは値を返す")
    void awaitFutureReturnsCompletedValue() throws Exception {
        CompletableFuture<String> future = CompletableFuture.completedFuture("ok");
        String value = CopilotHealthProbe.awaitFuture(future, 5, "timeout: ", "failed: ");

        assertThat(value).isEqualTo("ok");
    }

    @Test
    @DisplayName("awaitFuture: 失敗futureはCopilotCliExceptionに変換する")
    void awaitFutureWrapsExceptionalCompletion() {
        CompletableFuture<String> future = CompletableFuture.failedFuture(new IllegalStateException("boom"));

        assertThatThrownBy(() -> CopilotHealthProbe.awaitFuture(future, 5, "timeout: ", "failed: "))
            .isInstanceOf(CopilotCliException.class)
            .hasMessageContaining("failed: boom");
    }

    @Test
    @DisplayName("awaitFuture: タイムアウトはCopilotCliExceptionに変換しfutureをキャンセルする")
    void awaitFutureTimesOut() {
        CompletableFuture<String> future = new CompletableFuture<>();

        assertThatThrownBy(() -> CopilotHealthProbe.awaitFuture(future, 1, "timeout after ", "failed: "))
            .isInstanceOf(CopilotCliException.class)
            .hasMessageContaining("timeout after 1s");
        assertThat(future.isCancelled()).isTrue();
    }
}
