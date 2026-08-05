package dev.logicojp.reviewer.infrastructure.copilot;

import com.github.copilot.CopilotSession;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.rpc.MessageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/// Sends a prompt over a Copilot session and returns the assistant content.
///
/// Phase 3b implementation backed by the SDK's {@link CopilotSession#sendAndWait(MessageOptions, long)}
/// API. Defensive fallback: registers a side listener to record the most recent non-blank
/// content in case the primary {@code sendAndWait} result is blank.
final class ReviewSessionMessageSender {

    @FunctionalInterface
    interface SdkSendAndWait {
        AssistantMessageEvent sendAndWait(MessageOptions options, long timeoutMs)
            throws ExecutionException, InterruptedException, TimeoutException;
    }

    @FunctionalInterface
    interface SdkAssistantMessageSubscription {
        Closeable subscribe(Consumer<AssistantMessageEvent> handler);
    }

    private static final long FUTURE_GET_GRACE_MS = 2_000L;
    private static final Logger logger = LoggerFactory.getLogger(ReviewSessionMessageSender.class);

    private final String agentName;

    ReviewSessionMessageSender(String agentName) {
        this.agentName = agentName;
    }

    String sendAndAwait(CopilotSession session, String prompt, long maxTimeoutMs) throws Exception {
        return sendAndAwait(
            prompt,
            maxTimeoutMs,
            (options, timeoutMs) ->
                session.sendAndWait(options, timeoutMs)
                    .get(timeoutMs + FUTURE_GET_GRACE_MS, TimeUnit.MILLISECONDS),
            handler -> session.on(AssistantMessageEvent.class, handler)
        );
    }

    String sendAndAwait(String prompt,
                        long maxTimeoutMs,
                        SdkSendAndWait sdkSendAndWait,
                        SdkAssistantMessageSubscription subscription) throws Exception {
        var fallback = new AtomicReference<String>();
        try (Closeable ignored = subscription.subscribe(event -> recordFallback(event, fallback))) {
            try {
                AssistantMessageEvent result = sdkSendAndWait.sendAndWait(
                    new MessageOptions().setPrompt(prompt), maxTimeoutMs);
                String primary = extractContent(result);
                if (isUsable(primary)) return primary;
                return useFallbackOrNull(fallback.get(), "primary sendAndWait returned blank");
            } catch (TimeoutException e) {
                String fb = fallback.get();
                if (isUsable(fb)) {
                    logger.warn("Agent {}: max timeout reached ({} ms), returning fallback ({} chars)",
                        agentName, maxTimeoutMs, fb.length());
                    return fb;
                }
                throw e;
            } catch (ExecutionException e) {
                String fb = fallback.get();
                if (isUsable(fb)) {
                    logger.warn("Agent {}: sendAndWait failed ({}), returning fallback ({} chars)",
                        agentName, e.getMessage(), fb.length());
                    return fb;
                }
                throw e;
            }
        }
    }

    private static void recordFallback(AssistantMessageEvent event, AtomicReference<String> sink) {
        if (event == null) return;
        var data = event.getData();
        if (data == null) return;
        String content = data.content();
        if (isUsable(content)) sink.set(content);
    }

    private String useFallbackOrNull(String fallback, String reason) {
        if (isUsable(fallback)) {
            logger.warn("Agent {}: {}, using fallback ({} chars)", agentName, reason, fallback.length());
            return fallback;
        }
        return null;
    }

    private static String extractContent(AssistantMessageEvent event) {
        if (event == null) return null;
        var data = event.getData();
        return data != null ? data.content() : null;
    }

    private static boolean isUsable(String content) {
        return content != null && !content.isBlank();
    }
}
