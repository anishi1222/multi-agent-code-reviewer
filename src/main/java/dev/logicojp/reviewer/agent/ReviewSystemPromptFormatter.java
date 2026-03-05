package dev.logicojp.reviewer.agent;

final class ReviewSystemPromptFormatter {

    String format(String baseSystemPrompt,
                  String outputConstraints) {
        StringBuilder sb = new StringBuilder();
        sb.append(baseSystemPrompt);

        appendOutputConstraints(sb, outputConstraints);

        return sb.toString();
    }

    private void appendOutputConstraints(StringBuilder sb, String outputConstraints) {
        if (outputConstraints == null || outputConstraints.isBlank()) {
            return;
        }
        sb.append("\n");
        sb.append(outputConstraints.trim());
        sb.append("\n");
    }
}