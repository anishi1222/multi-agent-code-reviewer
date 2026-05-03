package dev.logicojp.reviewer.agent;

import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.generated.AssistantMessageEvent;
import com.github.copilot.sdk.generated.SessionErrorEvent;
import com.github.copilot.sdk.generated.SessionIdleEvent;
import org.slf4j.Logger;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// Binds session events to a {@link ContentCollector} in a transport-agnostic way.
final class ReviewSessionEvents {

    @FunctionalInterface
    interface SessionSubscription {
        AutoCloseable subscribe(Consumer<EventData> handler);
    }

    @FunctionalInterface
    interface TypedSessionSubscription<T> {
        AutoCloseable subscribe(Consumer<T> handler);
    }

    @FunctionalInterface
    interface TraceLogger {
        void log(Supplier<String> messageSupplier);
    }

    record EventData(String type, String content, int toolCalls, String errorMessage) {
    }

    private ReviewSessionEvents() {
    }

    /// Registers event listeners on a CopilotSession, wiring them to the content collector.
    /// This convenience method eliminates duplicated event-registration boilerplate
    /// across ReviewAgent and RubberDuckDialogueExecutor.
    static EventSubscriptions registerOnSession(String agentName,
                                                CopilotSession session,
                                                ContentCollector collector,
                                                Logger logger) {
        return register(
            agentName,
            collector,
            handler -> session.on(event -> handler.accept(
                new EventData(event.getType(), null, 0, null)
            )),
            handler -> session.on(AssistantMessageEvent.class, event -> {
                var data = event.getData();
                int toolCalls = data.toolRequests() != null ? data.toolRequests().size() : 0;
                handler.accept(new EventData("assistant", data.content(), toolCalls, null));
            }),
            handler -> session.on(SessionIdleEvent.class, _ ->
                handler.accept(new EventData("idle", null, 0, null))),
            handler -> session.on(SessionErrorEvent.class, event -> {
                var data = event.getData();
                handler.accept(new EventData(
                    "error", null, 0, data != null ? data.message() : "session error"));
            }),
            trace -> { if (logger.isTraceEnabled()) logger.trace("{}", trace.get()); }
        );
    }

    static EventSubscriptions register(String agentName,
                                       ContentCollector collector,
                                       SessionSubscription allEvents,
                                       TypedSessionSubscription<EventData> messages,
                                       TypedSessionSubscription<EventData> idle,
                                       TypedSessionSubscription<EventData> error,
                                       TraceLogger traceLogger) {
        var allEventsSub = subscribeAllEvents(agentName, collector, allEvents, traceLogger);
        var messageSub = subscribeMessages(collector, messages);
        var idleSub = subscribeIdle(collector, idle);
        var errorSub = subscribeError(collector, error);

        return new EventSubscriptions(allEventsSub, messageSub, idleSub, errorSub);
    }

    private static AutoCloseable subscribeAllEvents(String agentName,
                                                    ContentCollector collector,
                                                    SessionSubscription allEvents,
                                                    TraceLogger traceLogger) {
        return allEvents.subscribe(event -> {
            collector.onActivity();
            traceLogger.log(() -> "Agent " + agentName + ": event received \u2014 " + event.type());
        });
    }

    private static AutoCloseable subscribeMessages(ContentCollector collector,
                                                   TypedSessionSubscription<EventData> messages) {
        return messages.subscribe(event -> collector.onMessage(event.content(), Math.max(0, event.toolCalls())));
    }

    private static AutoCloseable subscribeIdle(ContentCollector collector,
                                               TypedSessionSubscription<EventData> idle) {
        return idle.subscribe(_ -> collector.onIdle());
    }

    private static AutoCloseable subscribeError(ContentCollector collector,
                                                TypedSessionSubscription<EventData> error) {
        return error.subscribe(event -> collector.onError(errorMessageOrDefault(event)));
    }

    private static String errorMessageOrDefault(EventData event) {
        return event.errorMessage() != null ? event.errorMessage() : "session error";
    }
}
