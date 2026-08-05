package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.domain.resilience.CopilotCliException;
import dev.logicojp.reviewer.shared.RetryPolicyUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Boundedness proof for the second consumer of the consolidated `RetryPolicyUtils`.
///
/// The T014.1 union merge gave this class the markers that previously lived only in the
/// `shared` copy (`temporarily`, `rate limit`, `too many requests`, `429`, `503`,
/// `network`). A permanent startup failure whose message collides with one of those
/// markers is now retried where it used to fail fast, so these tests confirm the retry
/// stays inside `MAX_START_ATTEMPTS` and that the error still reaches the caller.
///
/// This is the CLI startup path, so a regression here would be directly user-visible.
///
/// Note: [CopilotClientStarter] sleeps for real between attempts (no injectable clock), so
/// the exhausting test below intentionally costs a few seconds of wall time.
@DisplayName("CopilotClientStarter — retry bound under widened transient markers")
class CopilotClientStarterRetryBoundTest {

    private final CopilotClientStarter starter = new CopilotClientStarter();
    private final CopilotStartupErrorFormatter formatter = new CopilotStartupErrorFormatter();

    @Test
    @DisplayName("拡大マーカーに誤一致した恒久的な起動失敗もMAX_START_ATTEMPTSで打ち切られる")
    void widenedMarkerCannotExceedMaxStartAttempts() {
        // A permanent authorization failure that collides with the newly gained "network"
        // marker — previously non-transient for this consumer, now retried.
        var cause = new IllegalStateException("network policy denies access to this repository");
        var failure = new ExecutionException(cause);

        assertThat(RetryPolicyUtils.isTransientException(failure))
            .as("precondition: this consumer only gained this classification via the union merge")
            .isTrue();

        var client = new CountingStartableClient(failure);

        assertThatThrownBy(() -> starter.start(client, 30, formatter))
            .as("the startup failure must still be surfaced, not swallowed by the retry loop")
            .isInstanceOf(CopilotCliException.class);

        assertThat(client.attempts.get())
            .as("MAX_START_ATTEMPTS is a hard ceiling — the widening cannot create a retry loop")
            .isEqualTo(3);
        assertThat(client.closed.get())
            .as("the client must still be closed once the budget is exhausted")
            .isTrue();
    }

    @Test
    @DisplayName("マーカー非一致の恒久的な起動失敗は1回で即失敗する(拡大コストの対比)")
    void unmarkedStartupFailureStillFailsFast() {
        var failure = new ExecutionException(new IllegalStateException("copilot binary is not installed"));

        assertThat(RetryPolicyUtils.isTransientException(failure))
            .as("precondition: this permanent failure matches no marker")
            .isFalse();

        var client = new CountingStartableClient(failure);

        assertThatThrownBy(() -> starter.start(client, 30, formatter))
            .isInstanceOf(CopilotCliException.class);

        assertThat(client.attempts.get())
            .as("a non-transient startup failure must not consume the retry budget")
            .isEqualTo(1);
    }

    private static final class CountingStartableClient implements CopilotClientStarter.StartableClient {
        private final ExecutionException failure;
        private final AtomicInteger attempts = new AtomicInteger();
        private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();

        private CountingStartableClient(ExecutionException failure) {
            this.failure = failure;
        }

        @Override
        public void start(long timeoutSeconds) throws ExecutionException {
            attempts.incrementAndGet();
            throw failure;
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
