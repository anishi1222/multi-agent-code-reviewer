package dev.logicojp.reviewer.domain.agent;

import java.util.List;

/// Strategy for synthesizing rubber-duck dialogue rounds into a final unified review.
///
/// Sealed to ensure exhaustive matching at call sites. Both variants are pure
/// domain logic — they do not depend on any infrastructure or SDK types.
public sealed interface SynthesisStrategy
    permits SynthesisStrategy.LastResponder,
            SynthesisStrategy.DedicatedSession {

    /// Builds the synthesis prompt from completed dialogue rounds.
    ///
    /// @param rounds      the completed dialogue rounds
    /// @param agentConfig the agent configuration (for output format, focus areas, etc.)
    /// @return the prompt to send for final synthesis
    String buildSynthesisPrompt(List<DialogueRound> rounds, AgentConfig agentConfig);

    /// Synthesises using the last active session (no additional session created).
    record LastResponder(String templateContent) implements SynthesisStrategy {
        @Override
        public String buildSynthesisPrompt(List<DialogueRound> rounds, AgentConfig agentConfig) {
            return formatSynthesisPrompt(templateContent, rounds, agentConfig);
        }
    }

    /// Synthesises using a newly created dedicated session.
    record DedicatedSession(String templateContent) implements SynthesisStrategy {
        @Override
        public String buildSynthesisPrompt(List<DialogueRound> rounds, AgentConfig agentConfig) {
            return formatSynthesisPrompt(templateContent, rounds, agentConfig);
        }
    }

    private static String formatSynthesisPrompt(String template,
                                                 List<DialogueRound> rounds,
                                                 AgentConfig agentConfig) {
        var sb = new StringBuilder(template.length() + rounds.size() * 2048);
        sb.append(template).append("\n\n");
        sb.append("## Dialogue History\n\n");
        for (DialogueRound round : rounds) {
            sb.append("### Round ").append(round.roundNumber()).append("\n\n");
            sb.append("**").append(round.modelA()).append(":**\n")
                .append(round.contentA()).append("\n\n");
            sb.append("**").append(round.modelB()).append(":**\n")
                .append(round.contentB()).append("\n\n");
        }

        if (agentConfig.outputFormat() != null) {
            sb.append("\n").append(agentConfig.outputFormat()).append("\n");
        }
        return sb.toString();
    }
}
