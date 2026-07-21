package dev.logicojp.reviewer.agent;

import com.github.copilot.rpc.McpServerConfig;
import dev.logicojp.reviewer.config.PromptBudgetConfig;
import dev.logicojp.reviewer.config.RubberDuckConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.sanitize.ContentSanitizer;
import dev.logicojp.reviewer.service.TemplateService;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Executes a rubber-duck peer-discussion dialogue between two models for a single agent.
///
/// Creates two Copilot sessions (Session A with the agent's model, Session B with the peer model)
/// and orchestrates a multi-round dialogue. The orchestrator relays each session's output
/// to the other session as input for the next turn. After all rounds complete, a synthesis
/// prompt is sent to produce the final unified review.
final class RubberDuckDialogueExecutor {

    private static final Logger logger = LoggerFactory.getLogger(RubberDuckDialogueExecutor.class);

    private final AgentConfig config;
    private final RubberDuckConfig rubberDuckConfig;
    private final RubberDuckPromptBuilder promptBuilder;
    private final RubberDuckSessionFactory sessionFactory;
    private final RubberDuckDialogueRunner dialogueRunner;
    private final ReviewResultFactory reviewResultFactory;
    private final SynthesisStrategy synthesisStrategy;
    private final PromptBudgetConfig promptBudgetConfig;

    RubberDuckDialogueExecutor(AgentConfig config,
                               ReviewContext ctx,
                               RubberDuckConfig rubberDuckConfig,
                               TemplateService templateService) {
        this(config, rubberDuckConfig,
            new RubberDuckPromptBuilder(config, ctx, templateService),
            new SdkRubberDuckSessionFactory(config, ctx),
            new ReviewResultFactory(),
            ctx.promptBudgetConfig());
    }

    RubberDuckDialogueExecutor(AgentConfig config,
                               RubberDuckConfig rubberDuckConfig,
                               RubberDuckPromptBuilder promptBuilder,
                               RubberDuckSessionFactory sessionFactory,
                               ReviewResultFactory reviewResultFactory) {
        this(
            config,
            rubberDuckConfig,
            promptBuilder,
            sessionFactory,
            reviewResultFactory,
            new PromptBudgetConfig()
        );
    }

    RubberDuckDialogueExecutor(AgentConfig config,
                               RubberDuckConfig rubberDuckConfig,
                               RubberDuckPromptBuilder promptBuilder,
                               RubberDuckSessionFactory sessionFactory,
                               ReviewResultFactory reviewResultFactory,
                               PromptBudgetConfig promptBudgetConfig) {
        this.config = Objects.requireNonNull(config);
        this.rubberDuckConfig = Objects.requireNonNull(rubberDuckConfig);
        this.promptBuilder = Objects.requireNonNull(promptBuilder);
        this.sessionFactory = Objects.requireNonNull(sessionFactory);
        this.dialogueRunner = new RubberDuckDialogueRunner(config, promptBuilder);
        this.reviewResultFactory = Objects.requireNonNull(reviewResultFactory);
        this.promptBudgetConfig = Objects.requireNonNull(promptBudgetConfig);
        this.synthesisStrategy = resolveSynthesisStrategy();
    }

    /// Executes the rubber-duck dialogue and returns a single unified ReviewResult.
    ReviewResult execute(ReviewTarget target,
                         String instruction,
                         String localSourceContent,
                         Map<String, McpServerConfig> mcpServers) {
        String peerModel = resolvePeerModel();
        int rounds = resolveDialogueRounds();
        String language = config.language();

        logger.info("Agent {}: starting rubber-duck dialogue (model={}, peer={}, rounds={}, lang={})",
            config.name(), config.model(), peerModel, rounds, language);

        try {
            validatePeerModel(peerModel);
            try (var sessionA = sessionFactory.create(
                    config.model(), promptBuilder.buildSystemPromptA(), mcpServers, "A");
                 var sessionB = sessionFactory.create(
                     peerModel, promptBuilder.buildSystemPromptB(), mcpServers, "B")) {

                List<DialogueRound> dialogueRounds = dialogueRunner.conduct(
                    sessionA, sessionB, instruction, localSourceContent, peerModel, rounds);

                String synthesisResult = synthesize(sessionB, dialogueRounds);

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

    private String synthesize(RubberDuckSession sessionB,
                               List<DialogueRound> rounds) throws Exception {
        String synthesisPrompt = synthesisStrategy.buildSynthesisPrompt(rounds, config, promptBudgetConfig);
        logger.debug("Agent {}: synthesizing dialogue results", config.name());

        return switch (synthesisStrategy) {
            case SynthesisStrategy.LastResponder _ ->
                sessionB.send(synthesisPrompt);
            case SynthesisStrategy.DedicatedSession _ -> {
                try (var synthSession = sessionFactory.create(
                    config.model(), promptBuilder.buildSystemPromptA(), null, "synthesis")) {
                    yield synthSession.send(synthesisPrompt);
                }
            }
        };
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
        return config.effectiveDialogueRounds(rubberDuckConfig);
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
        String synthesisTemplate = promptBuilder.synthesisTemplate();
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

}
