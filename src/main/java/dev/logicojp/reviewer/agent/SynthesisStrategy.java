package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.PromptBudgetConfig;
import dev.logicojp.reviewer.util.PromptContentCompactor;

import java.util.ArrayList;
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

    default String buildSynthesisPrompt(List<DialogueRound> rounds,
                                        AgentConfig agentConfig,
                                        PromptBudgetConfig promptBudgetConfig) {
        if (promptBudgetConfig == null || !promptBudgetConfig.compactPrompts()) {
            return buildSynthesisPrompt(rounds, agentConfig);
        }
        return formatCompactSynthesisPrompt(templateContent(), rounds, agentConfig, promptBudgetConfig);
    }

    String templateContent();

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

    private static String formatCompactSynthesisPrompt(String template,
                                                       List<DialogueRound> rounds,
                                                       AgentConfig agentConfig,
                                                       PromptBudgetConfig budget) {
        var sb = new StringBuilder(Math.min(
            template.length() + budget.synthesisHistoryMaxChars(),
            budget.synthesisHistoryMaxChars() + 2048
        ));
        sb.append(template).append("\n\n");
        sb.append("## Dialogue History\n\n");

        int remaining = budget.synthesisHistoryMaxChars();
        List<String> selectedRounds = new ArrayList<>();
        for (int i = rounds.size() - 1; i >= 0 && remaining > 0; i--) {
            DialogueRound round = rounds.get(i);
            String roundText = formatRound(round, budget.synthesisTurnMaxChars());
            String clipped = PromptContentCompactor.compactKeepingTail(roundText, remaining);
            selectedRounds.addFirst(clipped);
            remaining -= clipped.length();
        }
        selectedRounds.forEach(round -> sb.append(round).append("\n\n"));

        if (agentConfig.outputFormat() != null) {
            sb.append("\n").append(agentConfig.outputFormat()).append("\n");
        }
        return sb.toString();
    }

    private static String formatSynthesisPrompt(String template,
                                                 List<DialogueRound> rounds,
                                                 AgentConfig agentConfig) {
        var sb = new StringBuilder(template.length() + rounds.size() * 2048);
        sb.append(template).append("\n\n");
        sb.append("## Dialogue History\n\n");
        for (DialogueRound round : rounds) {
            sb.append(formatRound(round, Integer.MAX_VALUE));
        }

        if (agentConfig.outputFormat() != null) {
            sb.append("\n").append(agentConfig.outputFormat()).append("\n");
        }
        return sb.toString();
    }

    private static String formatRound(DialogueRound round, int contentBudget) {
        String contentA = compactRoundContent(round.contentA(), contentBudget);
        String contentB = compactRoundContent(round.contentB(), contentBudget);
        return new StringBuilder()
            .append("### Round ").append(round.roundNumber()).append("\n\n")
            .append("**").append(round.modelA()).append(":**\n")
            .append(contentA).append("\n\n")
            .append("**").append(round.modelB()).append(":**\n")
            .append(contentB).append("\n\n")
            .toString();
    }

    private static String compactRoundContent(String content, int contentBudget) {
        if (contentBudget == Integer.MAX_VALUE) {
            return content;
        }
        return PromptContentCompactor.compactKeepingTail(content, contentBudget);
    }
}
