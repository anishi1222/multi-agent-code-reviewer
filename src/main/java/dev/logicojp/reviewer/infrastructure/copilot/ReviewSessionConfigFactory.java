package dev.logicojp.reviewer.infrastructure.copilot;

import com.github.copilot.SystemMessageMode;
import com.github.copilot.rpc.McpHttpServerConfig;
import com.github.copilot.rpc.McpServerConfig;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.SystemMessageConfig;
import dev.logicojp.reviewer.application.port.outbound.McpServerSpec;
import dev.logicojp.reviewer.application.port.outbound.SessionRequest;
import dev.logicojp.reviewer.infrastructure.config.ModelConfig;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Builds SDK {@link SessionConfig} from a domain {@link SessionRequest}.
///
/// Converts {@code McpServerSpec} domain DTOs to SDK {@code McpHttpServerConfig}.
@Singleton
final class ReviewSessionConfigFactory {

    private static final Logger logger = LoggerFactory.getLogger(ReviewSessionConfigFactory.class);
    private static final Pattern UNSUPPORTED_CHARS = Pattern.compile("[^A-Za-z0-9._-]");

    SessionConfig create(SessionRequest request,
                         String systemPrompt,
                         String invocationTimestamp,
                         int currentPass,
                         int totalPasses) {
        String agentName = request.agentConfig().name();
        String model = request.agentConfig().model();
        var sessionConfig = new SessionConfig()
            .setModel(model)
            .setSessionId(buildSessionId(agentName, invocationTimestamp, currentPass, totalPasses))
            .setOnPermissionRequest(CopilotPermissionHandlers.DENY_ALL)
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt));

        applyMcpServers(sessionConfig, request.mcpServers());
        applyReasoningEffort(request, model, sessionConfig);
        return sessionConfig;
    }

    private void applyMcpServers(SessionConfig sessionConfig, List<McpServerSpec> mcpSpecs) {
        if (mcpSpecs == null || mcpSpecs.isEmpty()) return;
        Map<String, McpServerConfig> sdkServers = mcpSpecs.stream()
            .collect(Collectors.toMap(
                McpServerSpec::name,
                spec -> (McpServerConfig) new McpHttpServerConfig()
                    .setUrl(spec.url())
                    .setHeaders(spec.headers())
                    .setTools(spec.tools())
            ));
        sessionConfig.setMcpServers(sdkServers);
    }

    private void applyReasoningEffort(SessionRequest request, String model, SessionConfig sessionConfig) {
        String requestedEffort = request.parameters().get("reasoningEffort");
        String effort = ModelConfig.resolveReasoningEffort(model, requestedEffort);
        if (effort != null) {
            logger.info("Setting reasoning effort '{}' for model: {}", effort, model);
            sessionConfig.setReasoningEffort(effort);
        }
    }

    private String buildSessionId(String agentName, String invocationTimestamp, int currentPass, int totalPasses) {
        int normalizedTotal = Math.max(1, totalPasses);
        int normalizedCurrent = Math.min(Math.max(1, currentPass), normalizedTotal);
        return "%s_%dof%d_%s".formatted(
            sanitize(agentName), normalizedCurrent, normalizedTotal, sanitize(invocationTimestamp));
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return UNSUPPORTED_CHARS.matcher(value).replaceAll("-");
    }
}
