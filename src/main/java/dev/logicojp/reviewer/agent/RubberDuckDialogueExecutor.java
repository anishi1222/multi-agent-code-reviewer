package dev.logicojp.reviewer.agent;

import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.SystemMessageMode;
import com.github.copilot.sdk.generated.AssistantMessageEvent;
import com.github.copilot.sdk.generated.SessionErrorEvent;
import com.github.copilot.sdk.generated.SessionIdleEvent;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SystemMessageConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.config.RubberDuckConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.sanitize.ContentSanitizer;
import dev.logicojp.reviewer.service.TemplateService;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.CopilotPermissionHandlers;
import dev.logicojp.reviewer.util.PlaceholderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/// Executes a rubber-duck peer-discussion dialogue between two models for a single agent.
///
/// Creates two Copilot sessions (Session A with the agent's model, Session B with the peer model)
/// and orchestrates a multi-round dialogue. The orchestrator relays each session's output
/// to the other session as input for the next turn. After all rounds complete, a synthesis
/// prompt is sent to produce the final unified review.
final class RubberDuckDialogueExecutor {

    private static final Logger logger = LoggerFactory.getLogger(RubberDuckDialogueExecutor.class);

    private final AgentConfig config;
    private final ReviewContext ctx;
    private final RubberDuckConfig rubberDuckConfig;
    private final TemplateService templateService;
    private final ReviewSessionMessageSender messageSenderA;
    private final ReviewSessionMessageSender messageSenderB;
    private final IdleTimeoutScheduler idleTimeoutScheduler;
    private final ReviewResultFactory reviewResultFactory;
    private final SynthesisStrategy synthesisStrategy;

    RubberDuckDialogueExecutor(AgentConfig config,
                               ReviewContext ctx,
                               RubberDuckConfig rubberDuckConfig,
                               TemplateService templateService) {
        this(config, ctx, rubberDuckConfig, templateService,
            createMessageSender(config, ctx, "A"),
            createMessageSender(config, ctx, "B"),
            IdleTimeoutScheduler.defaultScheduler(),
            new ReviewResultFactory());
    }

    RubberDuckDialogueExecutor(AgentConfig config,
                               ReviewContext ctx,
                               RubberDuckConfig rubberDuckConfig,
                               TemplateService templateService,
                               ReviewSessionMessageSender messageSenderA,
                               ReviewSessionMessageSender messageSenderB,
                               IdleTimeoutScheduler idleTimeoutScheduler,
                               ReviewResultFactory reviewResultFactory) {
        this.config = Objects.requireNonNull(config);
        this.ctx = Objects.requireNonNull(ctx);
        this.rubberDuckConfig = Objects.requireNonNull(rubberDuckConfig);
        this.templateService = Objects.requireNonNull(templateService);
        this.messageSenderA = messageSenderA;
        this.messageSenderB = messageSenderB;
        this.idleTimeoutScheduler = idleTimeoutScheduler;
        this.reviewResultFactory = reviewResultFactory;
        this.synthesisStrategy = resolveSynthesisStrategy();
    }

    /// Executes the rubber-duck dialogue and returns a single unified ReviewResult.
    ReviewResult execute(ReviewTarget target,
                         String instruction,
                         String localSourceContent,
                         Map<String, Object> mcpServers) {
        String peerModel = resolvePeerModel();
        int rounds = resolveDialogueRounds();
        String language = config.language();

        logger.info("Agent {}: starting rubber-duck dialogue (model={}, peer={}, rounds={}, lang={})",
            config.name(), config.model(), peerModel, rounds, language);

        validatePeerModel(peerModel);

        try {
            String systemPromptA = buildSystemPromptA();
            String systemPromptB = buildSystemPromptB();

            SessionConfig sessionConfigA = buildSessionConfig(
                config.model(), systemPromptA, mcpServers, "A");
            SessionConfig sessionConfigB = buildSessionConfig(
                peerModel, systemPromptB, mcpServers, "B");

            try (var sessionA = createSession(sessionConfigA);
                 var sessionB = createSession(sessionConfigB)) {

                List<DialogueRound> dialogueRounds = conductDialogue(
                    sessionA, sessionB, instruction, localSourceContent,
                    peerModel, rounds, language);

                String synthesisResult = synthesize(
                    sessionB, dialogueRounds);

                String sanitized = sanitize(synthesisResult);
                return reviewResultFactory.fromContent(
                    config, target.displayName(), sanitized, mcpServers != null);
            }
        } catch (Exception e) {
            logger.error("Agent {}: rubber-duck dialogue failed: {}",
                config.name(), e.getMessage(), e);
            return reviewResultFactory.fromException(config, target.displayName(), e);
        }
    }

    private List<DialogueRound> conductDialogue(CopilotSession sessionA,
                                                 CopilotSession sessionB,
                                                 String instruction,
                                                 String localSourceContent,
                                                 String peerModel,
                                                 int rounds,
                                                 String language) throws Exception {
        List<DialogueRound> completedRounds = new ArrayList<>(rounds);

        // Round 1: Session A performs initial review
        String initialPrompt = buildInitialPrompt(instruction, localSourceContent, language);
        logger.debug("Agent {}: Round 1 - Session A initial review", config.name());
        String contentA = sendMessage(sessionA, messageSenderA, initialPrompt);

        // Round 1: Session B responds to A's review
        String peerPrompt = buildPeerReviewPrompt(contentA, language);
        logger.debug("Agent {}: Round 1 - Session B peer review", config.name());
        String contentB = sendMessage(sessionB, messageSenderB, peerPrompt);

        completedRounds.add(new DialogueRound(1, config.model(), safeContent(contentA), peerModel, safeContent(contentB)));

        // Subsequent rounds: back-and-forth counter-arguments
        for (int round = 2; round <= rounds; round++) {
            logger.debug("Agent {}: Round {} - Session A counter", config.name(), round);
            String counterPromptA = buildCounterPrompt(contentB, language);
            contentA = sendMessage(sessionA, messageSenderA, counterPromptA);

            logger.debug("Agent {}: Round {} - Session B counter", config.name(), round);
            String counterPromptB = buildCounterPrompt(contentA, language);
            contentB = sendMessage(sessionB, messageSenderB, counterPromptB);

            completedRounds.add(new DialogueRound(round, config.model(), safeContent(contentA), peerModel, safeContent(contentB)));
        }

        logger.info("Agent {}: completed {} dialogue rounds", config.name(), rounds);
        return completedRounds;
    }

    private String synthesize(CopilotSession sessionB,
                               List<DialogueRound> rounds) throws Exception {
        String synthesisPrompt = synthesisStrategy.buildSynthesisPrompt(rounds, config);
        logger.debug("Agent {}: synthesizing dialogue results", config.name());

        return switch (synthesisStrategy) {
            case SynthesisStrategy.LastResponder _ ->
                sendMessage(sessionB, messageSenderB, synthesisPrompt);
            case SynthesisStrategy.DedicatedSession _ -> {
                SessionConfig synthConfig = buildSessionConfig(
                    config.model(), buildSystemPromptA(), null, "synthesis");
                try (var synthSession = createSession(synthConfig)) {
                    yield sendMessage(synthSession,
                        createMessageSender(config, ctx, "synth"), synthesisPrompt);
                }
            }
        };
    }

    // --- Session management ---

    private CopilotSession createSession(SessionConfig sessionConfig) throws Exception {
        return ctx.client().createSession(sessionConfig)
            .get(ctx.timeoutConfig().timeoutMinutes(), TimeUnit.MINUTES);
    }

    private SessionConfig buildSessionConfig(String model,
                                              String systemPrompt,
                                              Map<String, Object> mcpServers,
                                              String sessionTag) {
        var sessionConfig = new SessionConfig()
            .setModel(model)
            .setSessionId(buildSessionId(sessionTag))
            .setOnPermissionRequest(CopilotPermissionHandlers.DENY_ALL)
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt));

        if (mcpServers != null) {
            sessionConfig.setMcpServers(castMcpServers(mcpServers));
        }
        String effort = ModelConfig.resolveReasoningEffort(model, ctx.reasoningEffort());
        if (effort != null) {
            sessionConfig.setReasoningEffort(effort);
        }
        return sessionConfig;
    }

    @SuppressWarnings("unchecked")
    private Map<String, com.github.copilot.sdk.json.McpServerConfig> castMcpServers(Map<String, Object> mcpServers) {
        return (Map<String, com.github.copilot.sdk.json.McpServerConfig>) (Map<?, ?>) mcpServers;
    }

    private String buildSessionId(String sessionTag) {
        return "%s_rubber-duck_%s_%s".formatted(
            sanitizeToken(config.name()),
            sessionTag,
            sanitizeToken(ctx.invocationTimestamp()));
    }

    // --- Prompt construction ---

    private String buildInitialPrompt(String instruction,
                                       String localSourceContent,
                                       String language) {
        String template = loadTemplate("rubber-duck-initial", language);
        var sb = new StringBuilder(template);
        sb.append("\n\n").append(instruction);
        if (localSourceContent != null && !localSourceContent.isBlank()) {
            sb.append("\n\n").append(localSourceContent);
        }
        return sb.toString();
    }

    private String buildPeerReviewPrompt(String peerContent, String language) {
        String template = loadTemplate("rubber-duck-peer-review", language);
        return PlaceholderUtils.replaceDollarPlaceholders(
            template, Map.of("peerReviewContent", safeContent(peerContent)));
    }

    private String buildCounterPrompt(String peerContent, String language) {
        String template = loadTemplate("rubber-duck-counter", language);
        return PlaceholderUtils.replaceDollarPlaceholders(
            template, Map.of("peerReviewContent", safeContent(peerContent)));
    }

    private String buildSystemPromptA() {
        var sb = new StringBuilder();
        if (config.systemPrompt() != null) {
            sb.append(config.systemPrompt());
        }
        sb.append("\n\nYou are participating in a peer-discussion code review. ")
            .append("Engage constructively with the other reviewer's perspective. ");
        if (ctx.outputConstraints() != null) {
            sb.append("\n\n").append(ctx.outputConstraints());
        }
        return sb.toString();
    }

    private String buildSystemPromptB() {
        var sb = new StringBuilder();
        if (config.systemPrompt() != null) {
            sb.append(config.systemPrompt());
        }
        sb.append("\n\nYou are a peer reviewer providing an independent perspective. ")
            .append("Challenge assumptions and offer alternative viewpoints constructively. ");
        if (ctx.outputConstraints() != null) {
            sb.append("\n\n").append(ctx.outputConstraints());
        }
        return sb.toString();
    }

    // --- Template loading ---

    private String loadTemplate(String baseName, String language) {
        String templateName = baseName + "-" + language + ".md";
        try {
            return templateService.loadTemplateContent(templateName);
        } catch (Exception e) {
            logger.debug("Template '{}' not found, falling back to 'ja'", templateName);
            return templateService.loadTemplateContent(baseName + "-ja.md");
        }
    }

    // --- Message sending (reuses existing infrastructure) ---

    private String sendMessage(CopilotSession session,
                               ReviewSessionMessageSender sender,
                               String prompt) throws Exception {
        long idleTimeoutMs = TimeUnit.MINUTES.toMillis(ctx.timeoutConfig().idleTimeoutMinutes());
        long maxTimeoutMs = TimeUnit.MINUTES.toMillis(ctx.timeoutConfig().timeoutMinutes());

        return sender.sendWithActivityTimeout(
            prompt,
            maxTimeoutMs,
            sendPrompt -> session.send(new MessageOptions().setPrompt(sendPrompt)),
            collector -> registerEventListeners(session, collector),
            collector -> {
                var scheduledTask = idleTimeoutScheduler.schedule(
                    ctx.sharedScheduler(), collector, idleTimeoutMs);
                return () -> scheduledTask.cancel(false);
            }
        );
    }

    private EventSubscriptions registerEventListeners(CopilotSession session,
                                                       ContentCollector collector) {
        return ReviewSessionEvents.register(
            config.name(),
            collector,
            handler -> session.on(event -> handler.accept(
                new ReviewSessionEvents.EventData(event.getType(), null, 0, null)
            )),
            handler -> session.on(AssistantMessageEvent.class, event -> {
                var data = event.getData();
                int toolCalls = data.toolRequests() != null ? data.toolRequests().size() : 0;
                handler.accept(new ReviewSessionEvents.EventData(
                    "assistant", data.content(), toolCalls, null));
            }),
            handler -> session.on(SessionIdleEvent.class, _ ->
                handler.accept(new ReviewSessionEvents.EventData("idle", null, 0, null))),
            handler -> session.on(SessionErrorEvent.class, event -> {
                var data = event.getData();
                handler.accept(new ReviewSessionEvents.EventData(
                    "error", null, 0, data != null ? data.message() : "session error"));
            }),
            trace -> { if (logger.isTraceEnabled()) logger.trace("{}", trace.get()); }
        );
    }

    // --- Resolution helpers ---

    private String resolvePeerModel() {
        if (config.peerModel() != null) {
            return config.peerModel();
        }
        if (rubberDuckConfig.peerModel() != null) {
            return rubberDuckConfig.peerModel();
        }
        return config.model();
    }

    private int resolveDialogueRounds() {
        if (config.dialogueRounds() > 0) {
            return config.dialogueRounds();
        }
        return rubberDuckConfig.dialogueRounds();
    }

    private void validatePeerModel(String peerModel) {
        if (peerModel.equals(config.model())) {
            throw new IllegalArgumentException(
                "Agent '%s': rubber-duck mode requires different models but both are '%s'. "
                    .formatted(config.name(), peerModel)
                    + "Set peer-model in the agent definition, application.yml, or via --peer-model CLI option.");
        }
    }

    private SynthesisStrategy resolveSynthesisStrategy() {
        String language = config.language();
        String synthesisTemplate = loadTemplate("rubber-duck-synthesis", language);
        if (rubberDuckConfig.isDedicatedSessionSynthesis()) {
            return new SynthesisStrategy.DedicatedSession(synthesisTemplate);
        }
        return new SynthesisStrategy.LastResponder(synthesisTemplate);
    }

    private String sanitize(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        return ContentSanitizer.sanitize(content);
    }

    private String safeContent(String content) {
        return content != null ? content : "";
    }

    private static ReviewSessionMessageSender createMessageSender(
            AgentConfig config, ReviewContext ctx, String tag) {
        var tuning = ctx.agentTuningConfig();
        return new ReviewSessionMessageSender(
            config.name() + "-" + tag,
            tuning.maxAccumulatedSize(),
            tuning.initialAccumulatedCapacity());
    }

    private static String sanitizeToken(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
