package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.outbound.LoadTemplatePort;
import dev.logicojp.reviewer.application.port.outbound.McpServerSpec;
import dev.logicojp.reviewer.application.port.outbound.RubberDuckRequest;
import dev.logicojp.reviewer.application.port.outbound.RunRubberDuckSessionPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.DialogueRound;
import dev.logicojp.reviewer.domain.agent.RubberDuckPromptBuilder;
import dev.logicojp.reviewer.domain.agent.ReviewTargetInstructionResolver;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.report.ReviewResultFactory;
import dev.logicojp.reviewer.domain.review.ReviewContext;
import dev.logicojp.reviewer.domain.review.ReviewTarget;

import java.util.List;
import java.util.logging.Logger;

/// Executes a rubber-duck dialogue review for a single agent.
///
/// NEW class replacing the old {@code agent.RubberDuckDialogueRunner} +
/// {@code agent.RubberDuckSession} pattern.
///
/// Uses {@link RunRubberDuckSessionPort} (dependency-inversion) instead of
/// the SDK {@code RubberDuckSession} directly. Templates are loaded via
/// {@link LoadTemplatePort} and passed as pre-rendered strings to the pure-domain
/// {@link RubberDuckPromptBuilder}.
///
/// Collaborators (constructor-injected, no DI annotations):
/// - {@link RunRubberDuckSessionPort} — runs the multi-turn dialogue
/// - {@link LoadTemplatePort} — loads rubber-duck prompt templates
/// - {@link ReviewResultFactory} — constructs result objects from dialogue output
public final class RubberDuckDialogueRunner {

    private static final Logger logger = Logger.getLogger(RubberDuckDialogueRunner.class.getName());

    private static final String TEMPLATE_INITIAL_PREFIX = "rubber-duck-initial-";
    private static final String TEMPLATE_FALLBACK_LANG = "ja";

    private final RunRubberDuckSessionPort rubberDuckSession;
    private final LoadTemplatePort loadTemplate;
    private final ReviewResultFactory reviewResultFactory;

    public RubberDuckDialogueRunner(RunRubberDuckSessionPort rubberDuckSession,
                                    LoadTemplatePort loadTemplate,
                                    ReviewResultFactory reviewResultFactory) {
        this.rubberDuckSession = rubberDuckSession;
        this.loadTemplate = loadTemplate;
        this.reviewResultFactory = reviewResultFactory;
    }

    /// Executes a rubber-duck dialogue review.
    ///
    /// @param config     primary agent configuration
    /// @param target     review target
    /// @param context    shared review context
    /// @param rounds     number of dialogue rounds
    /// @param mcpServers MCP server specifications
    /// @return list of results (one per agent dialogue participant, synthesized)
    public List<ReviewResult> run(AgentConfig config,
                                  ReviewTarget target,
                                  ReviewContext context,
                                  int rounds,
                                  List<McpServerSpec> mcpServers) {
        String peerModel = config.peerModel() != null ? config.peerModel() : config.model();
        AgentConfig agentB = config.withModel(peerModel);

        var resolver = new ReviewTargetInstructionResolver(config);
        var resolved = resolver.resolve(target, context.cachedSourceContent());

        var promptBuilder = new RubberDuckPromptBuilder(config, context);
        String initialTemplate = loadTemplateForLanguage(config.language());
        String initialPrompt = promptBuilder.buildInitialPrompt(
            resolved.instruction(), resolved.localSourceContent(), initialTemplate);

        logger.info(() -> "Starting rubber-duck dialogue for agent '" + config.name()
            + "' on '" + target.displayName() + "' (" + rounds + " round(s))");

        var request = new RubberDuckRequest(config, agentB, initialPrompt, rounds, mcpServers);
        var dialogueRounds = rubberDuckSession.executeDialogue(request);

        logger.info(() -> "Rubber-duck dialogue completed for agent '" + config.name()
            + "': " + dialogueRounds.size() + " round(s)");

        return synthesizeResults(config, target, dialogueRounds);
    }

    private List<ReviewResult> synthesizeResults(AgentConfig config,
                                                  ReviewTarget target,
                                                  List<DialogueRound> dialogueRounds) {
        if (dialogueRounds.isEmpty()) {
            return List.of(reviewResultFactory.emptyContentFailure(
                config, target.displayName(), false));
        }
        DialogueRound lastRound = dialogueRounds.getLast();
        String synthesisContent = lastRound.contentA() + "\n\n---\n\n" + lastRound.contentB();
        return List.of(reviewResultFactory.fromContent(config, target.displayName(), synthesisContent, false));
    }

    private String loadTemplateForLanguage(String language) {
        String lang = language != null ? language : TEMPLATE_FALLBACK_LANG;
        String key = TEMPLATE_INITIAL_PREFIX + lang;
        try {
            return loadTemplate.loadRaw(key);
        } catch (Exception e) {
            logger.fine(() -> "Template '" + key + "' not found, falling back to '" + TEMPLATE_FALLBACK_LANG + "'");
            try {
                return loadTemplate.loadRaw(TEMPLATE_INITIAL_PREFIX + TEMPLATE_FALLBACK_LANG);
            } catch (Exception ex) {
                logger.warning(() -> "Fallback template not found: " + ex.getMessage());
                return "";
            }
        }
    }
}
