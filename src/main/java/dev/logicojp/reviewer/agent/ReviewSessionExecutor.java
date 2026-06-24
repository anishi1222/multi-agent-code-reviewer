package dev.logicojp.reviewer.agent;

import com.github.copilot.CopilotSession;
import com.github.copilot.rpc.McpServerConfig;
import com.github.copilot.rpc.SessionConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.sanitize.ContentSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

class ReviewSessionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ReviewSessionExecutor.class);

    private static final String FOLLOWUP_PROMPT =
        "Please provide the complete review results in the specified output format.";

    record Request(
        String displayName,
        String instruction,
        String localSourceContent,
        Map<String, McpServerConfig> mcpServers,
        int currentPass,
        int totalPasses
    ) {
    }

    private final AgentConfig config;
    private final ReviewContext ctx;
    private final ReviewSystemPromptFormatter reviewSystemPromptFormatter;
    private final ReviewSessionMessageSender reviewSessionMessageSender;
    private final ReviewSessionConfigFactory reviewSessionConfigFactory;
    private final ReviewResultFactory reviewResultFactory;
    private final String focusAreasGuidance;
    private final String localSourceHeaderPrompt;
    private final String localReviewResultPrompt;

    ReviewSessionExecutor(AgentConfig config,
                          ReviewContext ctx,
                          ReviewSystemPromptFormatter reviewSystemPromptFormatter,
                          ReviewSessionMessageSender reviewSessionMessageSender,
                          ReviewSessionConfigFactory reviewSessionConfigFactory,
                          ReviewResultFactory reviewResultFactory,
                          String focusAreasGuidance,
                          String localSourceHeaderPrompt,
                          String localReviewResultPrompt) {
        this.config = Objects.requireNonNull(config);
        this.ctx = Objects.requireNonNull(ctx);
        this.reviewSystemPromptFormatter = Objects.requireNonNull(reviewSystemPromptFormatter);
        this.reviewSessionMessageSender = Objects.requireNonNull(reviewSessionMessageSender);
        this.reviewSessionConfigFactory = Objects.requireNonNull(reviewSessionConfigFactory);
        this.reviewResultFactory = Objects.requireNonNull(reviewResultFactory);
        this.focusAreasGuidance = focusAreasGuidance;
        this.localSourceHeaderPrompt = localSourceHeaderPrompt;
        this.localReviewResultPrompt = localReviewResultPrompt;
    }

    ReviewResult execute(Request request) throws Exception {
        SessionConfig sessionConfig = createSessionConfig(request);
        try (var session = ctx.client().createSession(sessionConfig)
            .get(ctx.timeoutConfig().timeoutMinutes(), TimeUnit.MINUTES)) {
            return executeWithSession(request, session);
        }
    }

    ReviewResult executeWithSession(Request request, CopilotSession session) throws Exception {
        String content = sendAndCollectContent(session, request.instruction(), request.localSourceContent());
        ReviewResult result = reviewResultFactory.fromContent(
            config,
            request.displayName(),
            content,
            request.mcpServers() != null
        );
        if (result.success()) {
            logger.info("Review completed for agent: {} (content length: {} chars)",
                config.name(), content.length());
        } else {
            logger.warn("Agent {} returned invalid/empty review output: {}",
                config.name(), result.errorMessage());
        }
        return result;
    }

    SessionConfig createSessionConfig(Request request) {
        return reviewSessionConfigFactory.create(
            config,
            ctx,
            buildSystemPrompt(),
            request.mcpServers(),
            request.currentPass(),
            request.totalPasses()
        );
    }

    private String sendAndCollectContent(CopilotSession session,
                                         String instruction,
                                         String localSourceContent) throws Exception {
        long maxTimeoutMs = TimeUnit.MINUTES.toMillis(ctx.timeoutConfig().timeoutMinutes());

        var messageFlow = new ReviewMessageFlow(
            config.name(),
            FOLLOWUP_PROMPT,
            localSourceHeaderPrompt,
            localReviewResultPrompt,
            ctx.agentTuningConfig().instructionBufferExtraCapacity()
        );

        String content = messageFlow.execute(
            instruction,
            localSourceContent,
            prompt -> sendViaSdk(session, prompt, maxTimeoutMs)
        );

        return sanitizeReviewContent(content);
    }

    private String sanitizeReviewContent(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        return ContentSanitizer.sanitize(content);
    }

    private String sendViaSdk(CopilotSession session, String prompt, long maxTimeoutMs) throws Exception {
        logger.debug("Agent {}: sending prompt via SDK sendAndWait (max: {} min)",
            config.name(), ctx.timeoutConfig().timeoutMinutes());
        return reviewSessionMessageSender.sendAndAwait(session, prompt, maxTimeoutMs);
    }

    private String buildSystemPrompt() {
        return reviewSystemPromptFormatter.format(
            AgentPromptBuilder.buildFullSystemPrompt(config, focusAreasGuidance),
            ctx.outputConstraints()
        );
    }
}
