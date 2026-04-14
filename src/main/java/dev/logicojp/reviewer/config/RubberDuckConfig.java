package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;

/// Configuration for rubber-duck review mode.
/// When enabled, each agent conducts a peer-discussion dialogue
/// between two different models before producing a final review.
@ConfigurationProperties("reviewer.rubber-duck")
public record RubberDuckConfig(
    @Bindable(defaultValue = "false") boolean enabled,
    @Bindable(defaultValue = "2") int dialogueRounds,
    @Nullable String peerModel,
    @Bindable(defaultValue = "last-responder") String synthesisStrategy
) {

    public static final int DEFAULT_DIALOGUE_ROUNDS = 2;
    public static final int MIN_DIALOGUE_ROUNDS = 1;
    public static final int MAX_DIALOGUE_ROUNDS = 10;
    public static final String DEFAULT_SYNTHESIS_STRATEGY = "last-responder";
    public static final String SYNTHESIS_DEDICATED_SESSION = "dedicated-session";

    public RubberDuckConfig {
        dialogueRounds = ConfigDefaults.defaultIfNonPositive(dialogueRounds, DEFAULT_DIALOGUE_ROUNDS);
        if (dialogueRounds < MIN_DIALOGUE_ROUNDS) {
            dialogueRounds = MIN_DIALOGUE_ROUNDS;
        }
        if (dialogueRounds > MAX_DIALOGUE_ROUNDS) {
            dialogueRounds = MAX_DIALOGUE_ROUNDS;
        }
        synthesisStrategy = ConfigDefaults.defaultIfBlank(synthesisStrategy, DEFAULT_SYNTHESIS_STRATEGY);
        peerModel = (peerModel != null && peerModel.isBlank()) ? null : peerModel;
    }

    public RubberDuckConfig() {
        this(false, DEFAULT_DIALOGUE_ROUNDS, null, DEFAULT_SYNTHESIS_STRATEGY);
    }

    public boolean isDedicatedSessionSynthesis() {
        return SYNTHESIS_DEDICATED_SESSION.equals(synthesisStrategy);
    }
}
