package dev.logicojp.reviewer.domain.agent;

/// Parsed frontmatter metadata for an agent definition file.
///
/// @param name              agent identifier (from frontmatter {@code name:})
/// @param displayName       human-readable display name
/// @param model             LLM model identifier
/// @param body              markdown body below the frontmatter delimiter
/// @param peerModel         optional peer model for rubber-duck sessions
/// @param rubberDuckEnabled whether rubber-duck mode is active for this agent
/// @param dialogueRounds    agent-level override for number of dialogue rounds (0 = use global default)
/// @param language          response language code (e.g. "ja", "en")
public record ParsedAgentMetadata(
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
