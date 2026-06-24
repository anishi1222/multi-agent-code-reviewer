package dev.logicojp.reviewer.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class RubberDuckDialogueRunner {

    private static final Logger logger = LoggerFactory.getLogger(RubberDuckDialogueRunner.class);

    private final AgentConfig config;
    private final RubberDuckPromptBuilder promptBuilder;

    RubberDuckDialogueRunner(AgentConfig config, RubberDuckPromptBuilder promptBuilder) {
        this.config = Objects.requireNonNull(config);
        this.promptBuilder = Objects.requireNonNull(promptBuilder);
    }

    List<DialogueRound> conduct(RubberDuckSession sessionA,
                                RubberDuckSession sessionB,
                                String instruction,
                                String localSourceContent,
                                String peerModel,
                                int rounds) throws Exception {
        List<DialogueRound> completedRounds = new ArrayList<>(rounds);

        String initialPrompt = promptBuilder.buildInitialPrompt(instruction, localSourceContent);
        logger.debug("Agent {}: Round 1 - Session A initial review", config.name());
        String contentA = sessionA.send(initialPrompt);

        String peerPrompt = promptBuilder.buildPeerReviewPrompt(contentA);
        logger.debug("Agent {}: Round 1 - Session B peer review", config.name());
        String contentB = sessionB.send(peerPrompt);

        completedRounds.add(new DialogueRound(
            1,
            config.model(),
            RubberDuckPromptBuilder.safeContent(contentA),
            peerModel,
            RubberDuckPromptBuilder.safeContent(contentB)
        ));

        for (int round = 2; round <= rounds; round++) {
            logger.debug("Agent {}: Round {} - Session A counter", config.name(), round);
            String counterPromptA = promptBuilder.buildCounterPrompt(contentB);
            contentA = sessionA.send(counterPromptA);

            logger.debug("Agent {}: Round {} - Session B counter", config.name(), round);
            String counterPromptB = promptBuilder.buildCounterPrompt(contentA);
            contentB = sessionB.send(counterPromptB);

            completedRounds.add(new DialogueRound(
                round,
                config.model(),
                RubberDuckPromptBuilder.safeContent(contentA),
                peerModel,
                RubberDuckPromptBuilder.safeContent(contentB)
            ));
        }

        logger.info("Agent {}: completed {} dialogue rounds", config.name(), rounds);
        return completedRounds;
    }
}
