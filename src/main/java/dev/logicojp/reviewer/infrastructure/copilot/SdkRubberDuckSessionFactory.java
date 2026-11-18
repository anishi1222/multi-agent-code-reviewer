package dev.logicojp.reviewer.infrastructure.copilot;

import com.microsoft.copilot.CopilotSession;
import com.microsoft.copilot.SystemMessageMode;
import com.microsoft.copilot.rpc.CreateSessionOptions;
import com.microsoft.copilot.rpc.SystemMessageConfig;
import dev.logicojp.reviewer.application.port.outbound.McpServerSpec;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.infrastructure.config.ModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/// Creates SDK sessions for rubber-duck dialogue participants.
///
/// Converts {@link McpServerSpec} domain DTOs to SDK {@code McpHttpServerConfig}.
final class SdkRubberDuckSessionFactory {

    interface RubberDuckSession extends AutoCloseable {
        String send(String prompt) throws Exception;
        @Override void close();
    }

    private static final Logger logger = LoggerFactory.getLogger(SdkRubberDuckSessionFactory.class);
    private static final Pattern SESSION_TOKEN_UNSUPPORTED = Pattern.compile("[^A-Za-z0-9._-]");

    private final CopilotService copilotService;
    private final long agentTimeoutMinutes;
    private final String invocationTimestamp;

    SdkRubberDuckSessionFactory(CopilotService copilotService,
                                 long agentTimeoutMinutes,
                                 String invocationTimestamp) {
        this.copilotService = Objects.requireNonNull(copilotService);
        this.agentTimeoutMinutes = agentTimeoutMinutes > 0 ? agentTimeoutMinutes : 5L;
        this.invocationTimestamp = invocationTimestamp != null ? invocationTimestamp : "unknown";
    }

    RubberDuckSession create(AgentConfig agentConfig,
                              String systemPrompt,
                              List<McpServerSpec> mcpServers,
                              String sessionTag) throws Exception {
        CreateSessionOptions sessionConfig = buildSessionConfig(agentConfig, systemPrompt, mcpServers, sessionTag);
        CopilotSession session = copilotService.getClient()
            .createSession(sessionConfig)
            .get(agentTimeoutMinutes, TimeUnit.MINUTES);
        ReviewSessionMessageSender sender = new ReviewSessionMessageSender(
            agentConfig.name() + "-" + sessionTag);
        long timeoutMs = TimeUnit.MINUTES.toMillis(agentTimeoutMinutes);
        return new SdkSession(session, sender, timeoutMs);
    }

    private CreateSessionOptions buildSessionConfig(AgentConfig agentConfig,
                                              String systemPrompt,
                                              List<McpServerSpec> mcpServers,
                                              String sessionTag) {
        String model = agentConfig.model();
        var sessionConfig = new CreateSessionOptions()
            .setModel(model)
            .setSessionId(buildSessionId(agentConfig.name(), sessionTag))
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt));
        sessionConfig.getPermissions().setOnRequest(CopilotPermissionHandlers.DENY_ALL);
        ReviewSessionConfigFactory.applyMcpServers(sessionConfig, mcpServers);

        String effort = ModelConfig.resolveReasoningEffort(model, null);
        if (effort != null) {
            sessionConfig.getInference().setReasoningEffort(effort);
        }
        return sessionConfig;
    }

    private String buildSessionId(String agentName, String sessionTag) {
        return "%s_rubber-duck_%s_%s".formatted(
            sanitize(agentName), sanitize(sessionTag), sanitize(invocationTimestamp));
    }

    static String sanitize(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return SESSION_TOKEN_UNSUPPORTED.matcher(value).replaceAll("-");
    }

    private record SdkSession(CopilotSession session,
                               ReviewSessionMessageSender sender,
                               long timeoutMs) implements RubberDuckSession {
        @Override
        public String send(String prompt) throws Exception {
            return sender.sendAndAwait(session, prompt, timeoutMs);
        }

        @Override
        public void close() {
            session.close();
        }
    }
}
