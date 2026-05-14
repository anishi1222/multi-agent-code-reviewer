package dev.logicojp.reviewer.agent;

import com.github.copilot.sdk.generated.AssistantMessageEvent;
import com.github.copilot.sdk.generated.AssistantMessageEvent.AssistantMessageEventData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Phase 3b SDK-based sender — verifies the new {@code sendAndAwait} contract:
/// returns the assistant content from the SDK's {@code sendAndWait} when present,
/// or falls back to the most recent non-blank {@code AssistantMessageEvent} content
/// captured via the side listener.
@DisplayName("ReviewSessionMessageSender")
class ReviewSessionMessageSenderTest {

    private static final long DEFAULT_TIMEOUT_MS = 1_000L;

    @Nested
    @DisplayName("正常系")
    class HappyPath {

        @Test
        @DisplayName("sendAndWaitが返した最終AssistantMessageEventの内容を返す")
        void returnsFinalAssistantContent() throws Exception {
            var sender = new ReviewSessionMessageSender("security");

            String result = sender.sendAndAwait(
                "PROMPT",
                DEFAULT_TIMEOUT_MS,
                (_, _) -> assistantEvent("FINAL"),
                _ -> noOpCloseable());

            assertThat(result).isEqualTo("FINAL");
        }

        @Test
        @DisplayName("購読した側でも同じ内容が見えるが優先順位は最終結果")
        void prefersFinalResultOverSideListener() throws Exception {
            var sender = new ReviewSessionMessageSender("security");

            String result = sender.sendAndAwait(
                "PROMPT",
                DEFAULT_TIMEOUT_MS,
                (_, _) -> assistantEvent("FINAL"),
                handler -> {
                    handler.accept(assistantEvent("INTERMEDIATE"));
                    return noOpCloseable();
                });

            assertThat(result).isEqualTo("FINAL");
        }
    }

    @Nested
    @DisplayName("最終空応答 → fallback")
    class EmptyFinalFallback {

        @Test
        @DisplayName("最終応答が空のときは購読で観測した最新非空コンテンツを返す")
        void returnsAccumulatedContentWhenFinalIsBlank() throws Exception {
            var sender = new ReviewSessionMessageSender("security");

            String result = sender.sendAndAwait(
                "PROMPT",
                DEFAULT_TIMEOUT_MS,
                (_, _) -> assistantEvent(""),
                handler -> {
                    handler.accept(assistantEvent("PARTIAL"));
                    handler.accept(assistantEvent("LATEST"));
                    return noOpCloseable();
                });

            assertThat(result).isEqualTo("LATEST");
        }

        @Test
        @DisplayName("最終応答もfallbackも空ならnullを返す")
        void returnsNullWhenBothEmpty() throws Exception {
            var sender = new ReviewSessionMessageSender("security");

            String result = sender.sendAndAwait(
                "PROMPT",
                DEFAULT_TIMEOUT_MS,
                (_, _) -> assistantEvent(null),
                _ -> noOpCloseable());

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("空白のみの中間eventはfallbackとして採用しない")
        void ignoresBlankOnlyIntermediates() throws Exception {
            var sender = new ReviewSessionMessageSender("security");

            String result = sender.sendAndAwait(
                "PROMPT",
                DEFAULT_TIMEOUT_MS,
                (_, _) -> assistantEvent(""),
                handler -> {
                    handler.accept(assistantEvent("   "));
                    handler.accept(assistantEvent(null));
                    return noOpCloseable();
                });

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("失敗系")
    class Failures {

        @Test
        @DisplayName("TimeoutExceptionはfallbackがあれば返す")
        void returnsAccumulatedContentOnTimeout() throws Exception {
            var sender = new ReviewSessionMessageSender("security");

            String result = sender.sendAndAwait(
                "PROMPT",
                100,
                (_, _) -> {
                    throw new TimeoutException("simulated");
                },
                handler -> {
                    handler.accept(assistantEvent("PARTIAL"));
                    return noOpCloseable();
                });

            assertThat(result).isEqualTo("PARTIAL");
        }

        @Test
        @DisplayName("TimeoutExceptionかつfallbackなしなら例外をそのまま伝播")
        void throwsTimeoutWhenNoAccumulated() {
            var sender = new ReviewSessionMessageSender("security");

            assertThatThrownBy(() -> sender.sendAndAwait(
                "PROMPT",
                100,
                (_, _) -> {
                    throw new TimeoutException("simulated");
                },
                _ -> noOpCloseable()))
                .isInstanceOf(TimeoutException.class);
        }

        @Test
        @DisplayName("ExecutionExceptionもfallbackがあれば回復")
        void returnsAccumulatedContentOnExecutionException() throws Exception {
            var sender = new ReviewSessionMessageSender("security");

            String result = sender.sendAndAwait(
                "PROMPT",
                DEFAULT_TIMEOUT_MS,
                (_, _) -> {
                    throw new ExecutionException("simulated", new IllegalStateException("inner"));
                },
                handler -> {
                    handler.accept(assistantEvent("PARTIAL"));
                    return noOpCloseable();
                });

            assertThat(result).isEqualTo("PARTIAL");
        }

        @Test
        @DisplayName("購読は完了時にcloseされる")
        void closesSubscriptionOnSuccess() throws Exception {
            var sender = new ReviewSessionMessageSender("security");
            var closed = new AtomicBoolean();

            sender.sendAndAwait(
                "PROMPT",
                DEFAULT_TIMEOUT_MS,
                (_, _) -> assistantEvent("OK"),
                _ -> () -> closed.set(true));

            assertThat(closed).isTrue();
        }

        @Test
        @DisplayName("送信例外時でも購読はcloseされる")
        void closesSubscriptionOnFailure() {
            var sender = new ReviewSessionMessageSender("security");
            var closed = new AtomicBoolean();

            assertThatThrownBy(() -> sender.sendAndAwait(
                "PROMPT",
                DEFAULT_TIMEOUT_MS,
                (_, _) -> {
                    throw new TimeoutException("simulated");
                },
                _ -> () -> closed.set(true)))
                .isInstanceOf(TimeoutException.class);

            assertThat(closed).isTrue();
        }

        @Test
        @DisplayName("InterruptedExceptionは伝播する（fallbackなし）")
        void propagatesInterrupt() {
            var sender = new ReviewSessionMessageSender("security");

            assertThatThrownBy(() -> sender.sendAndAwait(
                "PROMPT",
                DEFAULT_TIMEOUT_MS,
                (_, _) -> {
                    throw new InterruptedException("simulated");
                },
                _ -> noOpCloseable()))
                .isInstanceOf(InterruptedException.class);
        }
    }

    @Nested
    @DisplayName("購読の挙動")
    class SubscriptionBehavior {

        @Test
        @DisplayName("nullイベントや空dataイベントはfallbackに採用しない")
        void ignoresNullEvents() throws Exception {
            var sender = new ReviewSessionMessageSender("security");
            var deliveredHandler = new AtomicReference<Consumer<AssistantMessageEvent>>();

            String result = sender.sendAndAwait(
                "PROMPT",
                DEFAULT_TIMEOUT_MS,
                (_, _) -> {
                    Consumer<AssistantMessageEvent> handler = deliveredHandler.get();
                    handler.accept(null);
                    handler.accept(new AssistantMessageEvent());
                    return assistantEvent("");
                },
                handler -> {
                    deliveredHandler.set(handler);
                    return noOpCloseable();
                });

            assertThat(result).isNull();
        }
    }

    private static AssistantMessageEvent assistantEvent(String content) {
        var event = new AssistantMessageEvent();
        event.setData(new AssistantMessageEventData(
            "msg-id", content, null, null, null, null, null, null, null, null, null));
        return event;
    }

    private static Closeable noOpCloseable() {
        return () -> {
        };
    }
}
