package dev.logicojp.reviewer.infrastructure.copilot;

import com.github.copilot.CopilotSession;
import com.github.copilot.SystemMessageMode;
import com.github.copilot.rpc.McpHttpServerConfig;
import com.github.copilot.rpc.McpServerConfig;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.SystemMessageConfig;
import dev.logicojp.reviewer.application.port.outbound.McpServerSpec;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.infrastructure.config.ModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        SessionConfig sessionConfig = buildSessionConfig(agentConfig, systemPrompt, mcpServers, sessionTag);
        CopilotSession session = copilotService.getClient()
            .createSession(sessionConfig)
            .get(agentTimeoutMinutes, TimeUnit.MINUTES);
        ReviewSessionMessageSender sender = new ReviewSessionMessageSender(
            agentConfig.name() + "-" + sessionTag);
        long timeoutMs = TimeUnit.MINUTES.toMillis(agentTimeoutMinutes);
        return new SdkSession(session, sender, timeoutMs);
    }

    private SessionConfig buildSessionConfig(AgentConfig agentConfig,
                                              String systemPrompt,
                                              List<McpServerSpec> mcpServers,
                                              String sessionTag) {
        String model = agentConfig.model();
        var sessionConfig = new SessionConfig()
            .setModel(model)
            .setSessionId(buildSessionId(agentConfig.name(), sessionTag))
            .setOnPermissionRequest(CopilotPermissionHandlers.DENY_ALL)
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt));

        if (mcpServers != null && !mcpServers.isEmpty()) {
            Map<String, McpServerConfig> sdkServers = mcpServers.stream()
                .collect(Collectors.toMap(
                    McpServerSpec::name,
                    spec -> (McpServerConfig) new McpHttpServerConfig()
                        .setUrl(spec.url())
                        .setHeaders(spec.headers())
                        .setTools(spec.tools())
                ));
            sessionConfig.setMcpServers(sdkServers);
        }

        String effort = ModelConfig.resolveReasoningEffort(model, null);
        if (effort != null) {
            sessionConfig.setReasoningEffort(effort);
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
