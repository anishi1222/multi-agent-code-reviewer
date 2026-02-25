package dev.logicojp.reviewer.agent;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;


@DisplayName("ContentCollector")
class ContentCollectorTest {

    @Nested
    @DisplayName("onMessage + onIdle")
    class MessageAndIdle {

        @Test
        @DisplayName("最後のメッセージ内容を返す")
        void returnsLastMessageOnIdle() throws Exception {
            var collector = new ContentCollector("test-agent");
            collector.onMessage("First message", 0);
            collector.onMessage("Last message", 0);
            collector.onIdle();

            String result = collector.awaitResult(1000);
            Assertions.assertThat(result).isEqualTo("Last message");
        }

        @Test
        @DisplayName("メッセージがない場合はnullを返す")
        void returnsNullWhenNoMessages() throws Exception {
            var collector = new ContentCollector("test-agent");
            collector.onIdle();

            String result = collector.awaitResult(1000);
            Assertions.assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("onError")
    class OnError {

        @Test
        @DisplayName("エラーメッセージで例外を完了する")
        void completesWithException() {
            var collector = new ContentCollector("test-agent");
            collector.onError("Something failed");

            Assertions.assertThatThrownBy(() -> collector.awaitResult(1000))
                .hasCauseInstanceOf(SessionEventException.class)
                .hasMessageContaining("Something failed");
        }
    }

    @Nested
    @DisplayName("onIdleTimeout")
    class OnIdleTimeout {

        @Test
        @DisplayName("蓄積コンテンツがある場合はそれを返す")
        void returnsAccumulatedOnTimeout() throws Exception {
            var collector = new ContentCollector("test-agent");
            collector.onMessage("Partial content", 0);
            collector.onIdleTimeout(5000, 5000);

            String result = collector.awaitResult(1000);
            Assertions.assertThat(result).isEqualTo("Partial content");
        }

        @Test
        @DisplayName("コンテンツがない場合はTimeoutExceptionで完了する")
        void throwsTimeoutWhenEmpty() {
            var collector = new ContentCollector("test-agent");
            collector.onIdleTimeout(5000, 5000);

            Assertions.assertThatThrownBy(() -> collector.awaitResult(1000))
                .hasCauseInstanceOf(TimeoutException.class);
        }
    }

    @Nested
    @DisplayName("getAccumulatedContent")
    class GetAccumulatedContent {

        @Test
        @DisplayName("蓄積された全メッセージを連結して返す")
        void returnsAccumulated() {
            var collector = new ContentCollector("test-agent");
            collector.onMessage("A", 0);
            collector.onMessage("B", 0);

            Assertions.assertThat(collector.getAccumulatedContent()).isEqualTo("AB");
        }

        @Test
        @DisplayName("メッセージなしの場合は空文字列を返す")
        void emptyWhenNoMessages() {
            var collector = new ContentCollector("test-agent");
            Assertions.assertThat(collector.getAccumulatedContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getElapsedSinceLastActivity")
    class ElapsedSinceLastActivity {

        @Test
        @DisplayName("最後のアクティビティからの経過時間を返す")
        void returnsElapsed() {
            AtomicLong clock = new AtomicLong(1000L);
            var collector = new ContentCollector("test-agent", clock::get);

            clock.set(1500L);
            Assertions.assertThat(collector.getElapsedSinceLastActivity()).isEqualTo(500L);

            collector.onActivity();
            Assertions.assertThat(collector.getElapsedSinceLastActivity()).isZero();
        }
    }
}
