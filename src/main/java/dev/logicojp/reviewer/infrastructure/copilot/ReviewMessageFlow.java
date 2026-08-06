package dev.logicojp.reviewer.infrastructure.copilot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Encapsulates review prompt send flow and fallback behavior.
///
/// Separates transport-independent message orchestration from session I/O,
/// making fallback behavior testable without Copilot SDK dependencies.
final class ReviewMessageFlow {

    @FunctionalInterface
    interface PromptSender {
        String send(String prompt) throws Exception;
    }

    @FunctionalInterface
    interface ResponseEvaluator {
        boolean hasContent(String content);
    }

    private static final Logger logger = LoggerFactory.getLogger(ReviewMessageFlow.class);

    private final String agentName;
    private final String followUpPrompt;
    private final String localSourceHeaderPrompt;
    private final String localReviewResultPrompt;
    private final int instructionBufferExtraCapacity;

    ReviewMessageFlow(String agentName,
                      String followUpPrompt,
                      String localSourceHeaderPrompt,
                      String localReviewResultPrompt,
                      int instructionBufferExtraCapacity) {
        this.agentName = agentName;
        this.followUpPrompt = followUpPrompt;
        this.localSourceHeaderPrompt = localSourceHeaderPrompt;
        this.localReviewResultPrompt = localReviewResultPrompt;
        this.instructionBufferExtraCapacity = instructionBufferExtraCapacity;
    }

    String execute(String instruction, String localSourceContent, PromptSender promptSender) throws Exception {
        return execute(instruction, localSourceContent, promptSender,
            content -> content != null && !content.isBlank());
    }

    String execute(String instruction,
                   String localSourceContent,
                   PromptSender promptSender,
                   ResponseEvaluator responseEvaluator) throws Exception {
        String content = localSourceContent != null
            ? sendForLocalReview(instruction, localSourceContent, promptSender)
            : sendForRemoteReview(instruction, promptSender);

        if (responseEvaluator.hasContent(content)) return content;

        logger.info("Agent {}: primary send returned empty content. Sending follow-up prompt...", agentName);
        String followUpContent = promptSender.send(followUpPrompt);
        if (responseEvaluator.hasContent(followUpContent)) {
            logger.info("Agent {}: follow-up prompt produced content ({} chars)",
                agentName, followUpContent.length());
            return followUpContent;
        }

        logger.warn("Agent {}: no content after follow-up", agentName);
        return null;
    }

    private String sendForLocalReview(String instruction, String localSourceContent,
                                      PromptSender promptSender) throws Exception {
        int estimatedPromptChars = instruction.length()
            + localSourceHeaderPrompt.length()
            + localSourceContent.length()
            + localReviewResultPrompt.length()
            + instructionBufferExtraCapacity;
        logger.debug("Agent {}: local review combined prompt size estimate={} chars",
            agentName, estimatedPromptChars);
        String combinedPrompt = new StringBuilder(estimatedPromptChars)
            .append(instruction)
            .append("\n\n")
            .append(localSourceHeaderPrompt)
            .append("\n\n")
            .append(localSourceContent)
            .append("\n\n")
            .append(localReviewResultPrompt)
            .toString();
        return promptSender.send(combinedPrompt);
    }

    private String sendForRemoteReview(String instruction, PromptSender promptSender) throws Exception {
        return promptSender.send(instruction);
    }
}
