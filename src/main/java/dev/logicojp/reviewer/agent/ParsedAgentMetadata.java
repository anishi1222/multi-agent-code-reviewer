package dev.logicojp.reviewer.agent;

record ParsedAgentMetadata(
    String name,
    String displayName,
    String model,
    String body,
    String peerModel,
    boolean rubberDuckEnabled,
    int dialogueRounds,
    String language
) {
}
