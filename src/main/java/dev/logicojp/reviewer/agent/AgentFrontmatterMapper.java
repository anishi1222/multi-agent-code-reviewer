package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.util.FrontmatterParser;

final class AgentFrontmatterMapper {

    ParsedAgentMetadata map(FrontmatterParser.Parsed parsed, String filename) {
        var metadata = parsed.metadata();
        String defaultName = AgentSectionParser.extractNameFromFilename(filename);
        String name = metadata.getOrDefault("name", defaultName);
        String displayName = metadata.getOrDefault("description",
            metadata.getOrDefault("displayName", name));
        String model = metadata.getOrDefault("model", ModelConfig.DEFAULT_MODEL);
        String peerModel = metadata.getOrDefault("peer-model", null);
        boolean rubberDuckEnabled = Boolean.parseBoolean(metadata.getOrDefault("rubber-duck", "false"));
        int dialogueRounds = parseIntOrDefault(metadata.getOrDefault("dialogue-rounds", null),
            AgentConfig.DEFAULT_DIALOGUE_ROUNDS);
        String language = metadata.getOrDefault("language", AgentConfig.DEFAULT_LANGUAGE);
        return new ParsedAgentMetadata(name, displayName, model, parsed.body(),
            peerModel, rubberDuckEnabled, dialogueRounds, language);
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException _) {
            return defaultValue;
        }
    }
}
