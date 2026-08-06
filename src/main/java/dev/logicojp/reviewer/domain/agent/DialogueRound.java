package dev.logicojp.reviewer.domain.agent;

/// Captures one round of a rubber-duck peer dialogue between two models.
///
/// @param roundNumber 1-based round index
/// @param modelA      the primary model identifier
/// @param contentA    the primary model's contribution this round
/// @param modelB      the peer model identifier
/// @param contentB    the peer model's contribution this round
public record DialogueRound(
    int roundNumber,
    String modelA,
    String contentA,
    String modelB,
    String contentB
) {

    public DialogueRound {
        if (roundNumber < 1) {
            throw new IllegalArgumentException("roundNumber must be >= 1");
        }
    }
}
