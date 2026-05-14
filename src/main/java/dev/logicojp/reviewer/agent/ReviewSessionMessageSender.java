package dev.logicojp.reviewer.agent;

import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.generated.AssistantMessageEvent;
import com.github.copilot.sdk.json.MessageOptions;
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
/// API. The SDK internally listens for {@code SessionIdleEvent} to determine when the assistant
/// has finished responding, replacing the previous custom activity-based scheduler.
///
/// Defensive fallback (per Phase 3a section 4): {@code sendAndWait} returns the
/// **last** {@code AssistantMessageEvent}, which is occasionally blank in real-world
/// runs. We register a lightweight side listener that records the most recent
/// non-blank content so that we can recover from this pathological case without
/// having to re-issue the entire request.
final class ReviewSessionMessageSender {

    /// Extra wall-clock grace added on top of {@code maxTimeoutMs} when blocking on the
    /// {@code sendAndWait} future. This guards against a race where the SDK's internal
    /// scheduled timeout completes the future shortly after {@code Future.get} would
    /// otherwise have given up.
    private static final long FUTURE_GET_GRACE_MS = 2_000L;

    @FunctionalInterface
    interface SdkSendAndWait {
        AssistantMessageEvent sendAndWait(MessageOptions options, long timeoutMs)
            throws ExecutionException, InterruptedException, TimeoutException;
    }

    @FunctionalInterface
    interface SdkAssistantMessageSubscription {
        Closeable subscribe(Consumer<AssistantMessageEvent> handler);
    }

    private static final Logger logger = LoggerFactory.getLogger(ReviewSessionMessageSender.class);

    private final String agentName;

    ReviewSessionMessageSender(String agentName) {
        this.agentName = agentName;
    }

    /// Sends the prompt on the given Copilot session and waits for the assistant
    /// response. Returns the response content, or {@code null} if no usable content
    /// could be obtained (caller should treat {@code null}/blank as "trigger follow-up").
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

    /// Test-friendly overload that decouples the call from {@link CopilotSession}
    /// (which is {@code final} and therefore not directly mockable).
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
                if (isUsable(primary)) {
                    return primary;
                }
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
        if (event == null) {
            return;
        }
        var data = event.getData();
        if (data == null) {
            return;
        }
        String content = data.content();
        if (isUsable(content)) {
            sink.set(content);
        }
    }

    private String useFallbackOrNull(String fallback, String reason) {
        if (isUsable(fallback)) {
            logger.warn("Agent {}: {}, using fallback ({} chars)",
                agentName, reason, fallback.length());
            return fallback;
        }
        return null;
    }

    private static String extractContent(AssistantMessageEvent event) {
        if (event == null) {
            return null;
        }
        var data = event.getData();
        return data != null ? data.content() : null;
    }

    private static boolean isUsable(String content) {
        return content != null && !content.isBlank();
    }
}
