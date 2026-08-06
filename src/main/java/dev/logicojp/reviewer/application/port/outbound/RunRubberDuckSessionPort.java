package dev.logicojp.reviewer.application.port.outbound;

import dev.logicojp.reviewer.domain.agent.DialogueRound;

import java.util.List;

/// Outbound port: execute a multi-turn rubber-duck dialogue.
///
/// Implementer: {@code infrastructure.copilot.RubberDuckDialogueExecutor}
/// Callers:     {@code application.review.RubberDuckDialogueRunner}
///
/// Covers behaviors: ORC-08
public interface RunRubberDuckSessionPort {

    /// Execute a multi-turn rubber-duck dialogue and return the completed rounds.
    ///
    /// Each {@link DialogueRound} captures both agents' responses for that round.
    /// The dialogue alternates between {@code request.agentA()} and
    /// {@code request.agentB()} for the configured number of rounds.
    ///
    /// @param request the dialogue parameters (agents, initial prompt, rounds, MCP config)
    /// @return the completed dialogue rounds in order
    List<DialogueRound> executeDialogue(RubberDuckRequest request);
}
