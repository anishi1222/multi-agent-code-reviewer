package dev.logicojp.reviewer.infrastructure.copilot;

import com.github.copilot.SystemMessageMode;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.SystemMessageConfig;
import dev.logicojp.reviewer.application.port.outbound.GenerateAiSummaryPort;
import dev.logicojp.reviewer.domain.resilience.CopilotCliException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/// Infrastructure implementation of {@link GenerateAiSummaryPort}.
///
/// Generates an AI-powered executive summary by submitting the prompt to
/// a single Copilot session.
///
/// No DI annotations — instantiated by {@link ReviewOrchestratorFactory}.
public class AiSummaryClient implements GenerateAiSummaryPort {

    private static final Logger logger = LoggerFactory.getLogger(AiSummaryClient.class);

    private final CopilotService copilotService;
    private final String model;
    private final String systemPrompt;
    private final long agentTimeoutMinutes;

    public AiSummaryClient(CopilotService copilotService,
                           String model,
                           String systemPrompt,
                           long agentTimeoutMinutes) {
        this.copilotService = Objects.requireNonNull(copilotService);
        this.model = model != null && !model.isBlank() ? model : "claude-sonnet-4.5";
        this.systemPrompt = systemPrompt;
        this.agentTimeoutMinutes = agentTimeoutMinutes > 0 ? agentTimeoutMinutes : 5L;
    }

    @Override
    public Optional<String> generate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            logger.warn("AiSummaryClient: empty prompt — skipping generation");
            return Optional.empty();
        }
        try {
            SessionConfig sessionConfig = buildSessionConfig();
            long timeoutMs = TimeUnit.MINUTES.toMillis(agentTimeoutMinutes);
            try (var session = copilotService.getClient()
                .createSession(sessionConfig)
                .get(agentTimeoutMinutes, TimeUnit.MINUTES)) {
                var response = session
                    .sendAndWait(new MessageOptions().setPrompt(prompt), timeoutMs)
                    .get(agentTimeoutMinutes, TimeUnit.MINUTES);
                if (response == null || response.getData() == null) {
                    logger.warn("AiSummaryClient: null response from Copilot");
                    return Optional.empty();
                }
                String content = response.getData().content();
                if (content == null || content.isBlank()) {
                    logger.warn("AiSummaryClient: blank content from Copilot");
                    return Optional.empty();
                }
                logger.info("AiSummaryClient: generated summary ({} chars)", content.length());
                return Optional.of(content);
            }
        } catch (CopilotCliException e) {
            logger.warn("AiSummaryClient: Copilot client unavailable: {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("AiSummaryClient: interrupted");
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("AiSummaryClient: summary generation failed: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private SessionConfig buildSessionConfig() {
        var sessionConfig = new SessionConfig()
            .setModel(model)
            .setOnPermissionRequest(CopilotPermissionHandlers.DENY_ALL);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sessionConfig.setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt));
        }
        return sessionConfig;
    }
}
