package dev.logicojp.reviewer.agent;

import java.util.List;

/// Strategy for synthesizing dialogue rounds into a final unified review.
sealed interface SynthesisStrategy
    permits SynthesisStrategy.LastResponder,
            SynthesisStrategy.DedicatedSession {

    /// Builds the synthesis prompt from completed dialogue rounds.
    ///
    /// @param rounds     the completed dialogue rounds
    /// @param agentConfig the agent configuration (for output format, focus areas, etc.)
    /// @return the prompt to send for final synthesis
    String buildSynthesisPrompt(List<DialogueRound> rounds, AgentConfig agentConfig);

    /// Synthesizes using the last active session (no additional session created).
    /// The final synthesis prompt is sent to whichever model responded last in the dialogue.
    record LastResponder(String templateContent) implements SynthesisStrategy {
        @Override
        public String buildSynthesisPrompt(List<DialogueRound> rounds, AgentConfig agentConfig) {
            return formatSynthesisPrompt(templateContent, rounds, agentConfig);
        }
    }

    /// Synthesizes using a newly created dedicated session.
    /// Both models' dialogue history is provided as input context.
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
