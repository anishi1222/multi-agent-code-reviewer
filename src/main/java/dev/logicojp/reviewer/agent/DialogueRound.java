package dev.logicojp.reviewer.agent;

/// Captures one round of a rubber-duck peer dialogue between two models.
///
/// @param roundNumber 1-based round index
/// @param modelA      the primary model identifier
/// @param contentA    the primary model's contribution this round
/// @param modelB      the peer model identifier
/// @param contentB    the peer model's contribution this round
record DialogueRound(
    int roundNumber,
    String modelA,
    String contentA,
    String modelB,
    String contentB
) {

    DialogueRound {
        if (roundNumber < 1) {
            throw new IllegalArgumentException("roundNumber must be >= 1");
        }
    }
}
