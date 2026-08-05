package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.port.outbound.RubberDuckRequest;
import dev.logicojp.reviewer.application.port.outbound.RunRubberDuckSessionPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.DialogueRound;
import dev.logicojp.reviewer.domain.agent.ReviewSystemPromptFormatter;
import dev.logicojp.reviewer.domain.resilience.CopilotCliException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Infrastructure implementation of {@link RunRubberDuckSessionPort}.
///
/// Runs a multi-turn rubber-duck dialogue between two Copilot sessions.
/// Returns pure-domain {@link DialogueRound} records.
///
/// No DI annotations — instantiated by {@link ReviewOrchestratorFactory}.
public class RubberDuckDialogueExecutor implements RunRubberDuckSessionPort {

    private static final Logger logger = LoggerFactory.getLogger(RubberDuckDialogueExecutor.class);

    private static final String DEFAULT_COUNTER_PROMPT_PREFIX =
        "The other reviewer provided the following perspective. Please respond constructively:\n\n";

    private final SdkRubberDuckSessionFactory sessionFactory;
    private final ReviewSystemPromptFormatter systemPromptFormatter;

    public RubberDuckDialogueExecutor(SdkRubberDuckSessionFactory sessionFactory,
                                      ReviewSystemPromptFormatter systemPromptFormatter) {
        this.sessionFactory = Objects.requireNonNull(sessionFactory);
        this.systemPromptFormatter = Objects.requireNonNull(systemPromptFormatter);
    }

    @Override
    public List<DialogueRound> executeDialogue(RubberDuckRequest request) {
        AgentConfig agentA = request.agentA();
        AgentConfig agentB = request.agentB();
        String initialPrompt = request.initialPrompt();
        int rounds = request.rounds();

        String systemPromptA = systemPromptFormatter.format(agentA);
        String systemPromptB = systemPromptFormatter.format(agentB);

        logger.info("Starting rubber-duck dialogue: agentA={} (model={}), agentB={} (model={}), rounds={}",
            agentA.name(), agentA.model(), agentB.name(), agentB.model(), rounds);

        try {
            try (var sessionA = sessionFactory.create(agentA, systemPromptA, request.mcpServers(), "A");
                 var sessionB = sessionFactory.create(agentB, systemPromptB, request.mcpServers(), "B")) {
                return conductDialogue(sessionA, sessionB, agentA, agentB, initialPrompt, rounds);
            }
        } catch (CopilotCliException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CopilotCliException("Rubber-duck dialogue interrupted", e);
        } catch (Exception e) {
            throw new CopilotCliException("Rubber-duck dialogue failed: " + e.getMessage(), e);
        }
    }

    private List<DialogueRound> conductDialogue(SdkRubberDuckSessionFactory.RubberDuckSession sessionA,
                                                 SdkRubberDuckSessionFactory.RubberDuckSession sessionB,
                                                 AgentConfig agentA,
                                                 AgentConfig agentB,
                                                 String initialPrompt,
                                                 int rounds) throws Exception {
        List<DialogueRound> completedRounds = new ArrayList<>(rounds);

        logger.debug("Rubber-duck round 1 — session A initial review");
        String contentA = safeContent(sessionA.send(initialPrompt));

        logger.debug("Rubber-duck round 1 — session B peer review");
        String peerPrompt = DEFAULT_COUNTER_PROMPT_PREFIX + contentA;
        String contentB = safeContent(sessionB.send(peerPrompt));

        completedRounds.add(new DialogueRound(1, agentA.model(), contentA, agentB.model(), contentB));

        for (int round = 2; round <= rounds; round++) {
            logger.debug("Rubber-duck round {} — session A counter", round);
            String counterA = DEFAULT_COUNTER_PROMPT_PREFIX + contentB;
            contentA = safeContent(sessionA.send(counterA));

            logger.debug("Rubber-duck round {} — session B counter", round);
            String counterB = DEFAULT_COUNTER_PROMPT_PREFIX + contentA;
            contentB = safeContent(sessionB.send(counterB));

            completedRounds.add(new DialogueRound(round, agentA.model(), contentA, agentB.model(), contentB));
        }

        logger.info("Rubber-duck dialogue completed: {} rounds", rounds);
        return completedRounds;
    }

    private static String safeContent(String content) {
        return content != null ? content : "";
    }
}
