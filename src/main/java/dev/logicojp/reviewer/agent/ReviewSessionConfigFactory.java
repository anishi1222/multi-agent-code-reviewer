package dev.logicojp.reviewer.agent;

import com.github.copilot.sdk.SystemMessageMode;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SystemMessageConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.util.CopilotPermissionHandlers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Pattern;

final class ReviewSessionConfigFactory {

    private static final Logger logger = LoggerFactory.getLogger(ReviewSessionConfigFactory.class);

    private static final Pattern UNSUPPORTED_CHARS = Pattern.compile("[^A-Za-z0-9._-]");

    SessionConfig create(AgentConfig config,
                         ReviewContext ctx,
                         String systemPrompt,
                         Map<String, Object> mcpServers,
                         int currentPass,
                         int totalPasses) {
        var sessionConfig = new SessionConfig()
            .setModel(config.model())
            .setSessionId(buildSessionId(config.name(), ctx.invocationTimestamp(), currentPass, totalPasses))
            .setOnPermissionRequest(CopilotPermissionHandlers.DENY_ALL)
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt));

        applyMcpServers(sessionConfig, mcpServers);
        applyReasoningEffort(config, ctx, sessionConfig);
        return sessionConfig;
    }

    private void applyMcpServers(SessionConfig sessionConfig, Map<String, Object> mcpServers) {
        if (mcpServers != null) {
            sessionConfig.setMcpServers(castMcpServers(mcpServers));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, com.github.copilot.sdk.json.McpServerConfig> castMcpServers(Map<String, Object> mcpServers) {
        return (Map<String, com.github.copilot.sdk.json.McpServerConfig>) (Map<?, ?>) mcpServers;
    }

    private void applyReasoningEffort(AgentConfig config,
                                      ReviewContext ctx,
                                      SessionConfig sessionConfig) {
        String effort = ModelConfig.resolveReasoningEffort(config.model(), ctx.reasoningEffort());
        if (effort != null) {
            logger.info("Setting reasoning effort '{}' for model: {}", effort, config.model());
            sessionConfig.setReasoningEffort(effort);
        }
    }

    private String buildSessionId(String agentName,
                                  String invocationTimestamp,
                                  int currentPass,
                                  int totalPasses) {
        int normalizedTotalPasses = Math.max(1, totalPasses);
        int normalizedCurrentPass = Math.min(Math.max(1, currentPass), normalizedTotalPasses);

        String safeAgentName = sanitizeSessionToken(agentName);
        String safeTimestamp = sanitizeSessionToken(invocationTimestamp);
        return "%s_%dof%d_%s".formatted(
            safeAgentName,
            normalizedCurrentPass,
            normalizedTotalPasses,
            safeTimestamp
        );
    }

    private String sanitizeSessionToken(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return UNSUPPORTED_CHARS.matcher(value).replaceAll("-");
    }
}