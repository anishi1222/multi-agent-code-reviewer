package dev.logicojp.reviewer.agent;

import com.github.copilot.sdk.SystemMessageMode;
import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SystemMessageConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

final class ReviewSessionConfigFactory {

    private static final Logger logger = LoggerFactory.getLogger(ReviewSessionConfigFactory.class);

    SessionConfig create(AgentConfig config,
                         ReviewContext ctx,
                         String systemPrompt,
                         Map<String, Object> mcpServers) {
        var sessionConfig = new SessionConfig()
            .setModel(config.model())
            .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt));

        applyMcpServers(sessionConfig, mcpServers);
        applyReasoningEffort(config, ctx, sessionConfig);
        return sessionConfig;
    }

    private void applyMcpServers(SessionConfig sessionConfig, Map<String, Object> mcpServers) {
        if (mcpServers != null) {
            sessionConfig.setMcpServers(mcpServers);
        }
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
}