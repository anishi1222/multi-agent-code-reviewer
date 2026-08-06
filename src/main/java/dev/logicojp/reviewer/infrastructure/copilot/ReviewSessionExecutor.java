package dev.logicojp.reviewer.infrastructure.copilot;

import com.github.copilot.CopilotSession;
import com.github.copilot.rpc.SessionConfig;
import dev.logicojp.reviewer.application.port.outbound.RunCopilotSessionPort;
import dev.logicojp.reviewer.application.port.outbound.SessionRequest;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.ReviewSystemPromptFormatter;
import dev.logicojp.reviewer.domain.resilience.CopilotCliException;
import dev.logicojp.reviewer.domain.report.ContentSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/// Infrastructure implementation of {@link RunCopilotSessionPort}.
///
/// Creates a single Copilot session per invocation, sends the review prompt,
/// and returns the assistant response. Relies on {@link CopilotService#getClient()}
/// to obtain the currently-active SDK client.
///
/// No DI annotations — instantiated by {@link ReviewOrchestratorFactory}.
public class ReviewSessionExecutor implements RunCopilotSessionPort {

    private static final Logger logger = LoggerFactory.getLogger(ReviewSessionExecutor.class);
    private static final String FOLLOWUP_PROMPT =
        "Please provide the complete review results in the specified output format.";

    private final CopilotService copilotService;
    private final ReviewSessionConfigFactory sessionConfigFactory;
    private final ReviewSystemPromptFormatter systemPromptFormatter;
    private final long agentTimeoutMinutes;
    private final String invocationTimestamp;

    public ReviewSessionExecutor(CopilotService copilotService,
                                 ReviewSessionConfigFactory sessionConfigFactory,
                                 ReviewSystemPromptFormatter systemPromptFormatter,
                                 long agentTimeoutMinutes,
                                 String invocationTimestamp) {
        this.copilotService = Objects.requireNonNull(copilotService);
        this.sessionConfigFactory = Objects.requireNonNull(sessionConfigFactory);
        this.systemPromptFormatter = Objects.requireNonNull(systemPromptFormatter);
        this.agentTimeoutMinutes = agentTimeoutMinutes > 0 ? agentTimeoutMinutes : 5L;
        this.invocationTimestamp = invocationTimestamp != null ? invocationTimestamp : "unknown";
    }

    @Override
    public String runSession(SessionRequest request) {
        AgentConfig agentConfig = request.agentConfig();
        String systemPrompt = systemPromptFormatter.format(agentConfig);
        String agentName = agentConfig.name();

        int currentPass = parseIntParam(request.parameters().get("currentPass"), 1);
        int totalPasses = parseIntParam(request.parameters().get("totalPasses"), 1);
        String timestamp = request.parameters().getOrDefault("invocationTimestamp", invocationTimestamp);

        SessionConfig sessionConfig = sessionConfigFactory.create(
            request, systemPrompt, timestamp, currentPass, totalPasses);

        long timeoutMs = TimeUnit.MINUTES.toMillis(agentTimeoutMinutes);
        var messageSender = new ReviewSessionMessageSender(agentName);
        var messageFlow = new ReviewMessageFlow(
            agentName,
            FOLLOWUP_PROMPT,
            "",   // no separate local source header; prompt already contains full instruction
            "",   // no separate local result prompt
            256   // small extra capacity for overhead
        );

        try {
            try (var session = copilotService.getClient()
                .createSession(sessionConfig)
                .get(agentTimeoutMinutes, TimeUnit.MINUTES)) {
                return executeWithSession(session, request.prompt(), messageFlow, messageSender, timeoutMs, agentName);
            }
        } catch (CopilotCliException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CopilotCliException("Review session interrupted for agent: " + agentName, e);
        } catch (Exception e) {
            throw new CopilotCliException("Review session failed for agent: " + agentName + ": " + e.getMessage(), e);
        }
    }

    private String executeWithSession(CopilotSession session,
                                      String prompt,
                                      ReviewMessageFlow messageFlow,
                                      ReviewSessionMessageSender sender,
                                      long timeoutMs,
                                      String agentName) throws Exception {
        String content = messageFlow.execute(
            prompt,
            null,  // no separate local source content; prompt already includes it if needed
            p -> sender.sendAndAwait(session, p, timeoutMs)
        );

        if (content == null || content.isBlank()) {
            logger.warn("Agent {}: session returned empty/null content", agentName);
            return "";
        }
        String sanitized = ContentSanitizer.sanitize(content);
        logger.info("Review session completed for agent: {} ({} chars)", agentName, sanitized.length());
        return sanitized;
    }

    private static int parseIntParam(String value, int defaultVal) {
        if (value == null || value.isBlank()) return defaultVal;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
