package dev.logicojp.reviewer.domain.agent;

import dev.logicojp.reviewer.shared.PromptBudget;
import dev.logicojp.reviewer.shared.PromptContentCompactor;

import java.util.ArrayList;
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

    /// Builds the synthesis prompt, applying character budgets when compaction is enabled.
    ///
    /// Falls back to the uncompacted form when no budget is supplied or compaction is off,
    /// so behaviour is unchanged unless `reviewer.prompt-budget.compact-prompts` is set.
    default String buildSynthesisPrompt(List<DialogueRound> rounds,
                                        AgentConfig agentConfig,
                                        PromptBudget promptBudget) {
        if (promptBudget == null || !promptBudget.compactPrompts()) {
            return buildSynthesisPrompt(rounds, agentConfig);
        }
        return formatCompactSynthesisPrompt(templateContent(), rounds, agentConfig, promptBudget);
    }

    String templateContent();

    /// Synthesises using the last active session (no additional session created).
    /// The final synthesis prompt is sent to whichever model responded last in the dialogue.
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

    private static String formatCompactSynthesisPrompt(String template,
                                                       List<DialogueRound> rounds,
                                                       AgentConfig agentConfig,
                                                       PromptBudget budget) {
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
