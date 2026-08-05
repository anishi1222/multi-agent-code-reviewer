package dev.logicojp.reviewer.domain.agent;

import java.util.Map;

/// Maps raw parsed frontmatter metadata to {@link ParsedAgentMetadata}.
///
/// The infrastructure layer is responsible for parsing the frontmatter (YAML, etc.)
/// and passing the resulting key-value map and body text here.
/// This class contains only pure mapping logic — no file I/O, no YAML parsing.
public final class AgentFrontmatterMapper {

    /// Default model identifier used when the agent file does not specify one.
    public static final String DEFAULT_MODEL = "claude-sonnet-4.5";

    private final String defaultModel;

    public AgentFrontmatterMapper() {
        this(DEFAULT_MODEL);
    }

    public AgentFrontmatterMapper(String defaultModel) {
        this.defaultModel = defaultModel != null ? defaultModel : DEFAULT_MODEL;
    }

    /// Maps raw frontmatter metadata and body to a {@link ParsedAgentMetadata}.
    ///
    /// @param metadata  key-value pairs from the frontmatter block (already parsed by infrastructure)
    /// @param body      the markdown body (everything after the frontmatter delimiter)
    /// @param filename  the source filename, used to derive a default agent name
    /// @return parsed metadata record
    public ParsedAgentMetadata map(Map<String, String> metadata, String body, String filename) {
        String defaultName = AgentSectionParser.extractNameFromFilename(filename);
        String name = metadata.getOrDefault("name", defaultName);
        String displayName = metadata.getOrDefault("description",
            metadata.getOrDefault("displayName", name));
        String model = metadata.getOrDefault("model", defaultModel);
        String peerModel = metadata.getOrDefault("peer-model", null);
        boolean rubberDuckEnabled = Boolean.parseBoolean(metadata.getOrDefault("rubber-duck", "false"));
        int dialogueRounds = parseIntOrDefault(metadata.getOrDefault("dialogue-rounds", null),
            AgentConfig.DEFAULT_DIALOGUE_ROUNDS);
        String language = metadata.getOrDefault("language", AgentConfig.DEFAULT_LANGUAGE);
        return new ParsedAgentMetadata(name, displayName, model, body,
            peerModel, rubberDuckEnabled, dialogueRounds, language);
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException _) {
            return defaultValue;
        }
    }
}
