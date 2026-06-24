package dev.logicojp.reviewer.agent;

import com.github.copilot.CopilotSession;
import com.github.copilot.SystemMessageMode;
import com.github.copilot.rpc.McpServerConfig;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.SystemMessageConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.util.CopilotPermissionHandlers;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

final class SdkRubberDuckSessionFactory implements RubberDuckSessionFactory {

    private static final Pattern SESSION_TOKEN_UNSUPPORTED = Pattern.compile("[^A-Za-z0-9._-]");

    private final AgentConfig config;
    private final ReviewContext ctx;

    SdkRubberDuckSessionFactory(AgentConfig config, ReviewContext ctx) {
        this.config = Objects.requireNonNull(config);
        this.ctx = Objects.requireNonNull(ctx);
    }

    @Override
    public RubberDuckSession create(String model,
                                    String systemPrompt,
                                    Map<String, McpServerConfig> mcpServers,
                                    String sessionTag) throws Exception {
        SessionConfig sessionConfig = buildSessionConfig(model, systemPrompt, mcpServers, sessionTag);
        CopilotSession session = ctx.client().createSession(sessionConfig)
            .get(ctx.timeoutConfig().timeoutMinutes(), TimeUnit.MINUTES);
        ReviewSessionMessageSender sender = createMessageSender(config, sessionTag);
        return new SdkRubberDuckSession(session, sender, messageTimeoutMs());
    }

    SessionConfig buildSessionConfig(String model,
                                     String systemPrompt,
                                     Map<String, McpServerConfig> mcpServers,
                                     String sessionTag) {
        var sessionConfig = new SessionConfig()
            .setModel(model)
            .setSessionId(buildSessionId(sessionTag))
            .setOnPermissionRequest(CopilotPermissionHandlers.DENY_ALL)
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt));

        if (mcpServers != null && !mcpServers.isEmpty()) {
            sessionConfig.setMcpServers(mcpServers);
        }
        String effort = ModelConfig.resolveReasoningEffort(model, ctx.reasoningEffort());
        if (effort != null) {
            sessionConfig.setReasoningEffort(effort);
        }
        return sessionConfig;
    }

    private String buildSessionId(String sessionTag) {
        return "%s_rubber-duck_%s_%s".formatted(
            sanitizeToken(config.name()),
            sanitizeToken(sessionTag),
            sanitizeToken(ctx.invocationTimestamp()));
    }

    private long messageTimeoutMs() {
        return TimeUnit.MINUTES.toMillis(ctx.timeoutConfig().timeoutMinutes());
    }

    private static ReviewSessionMessageSender createMessageSender(AgentConfig config, String tag) {
        return new ReviewSessionMessageSender(config.name() + "-" + tag);
    }

    static String sanitizeToken(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return SESSION_TOKEN_UNSUPPORTED.matcher(value).replaceAll("-");
    }

    private record SdkRubberDuckSession(CopilotSession session,
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
