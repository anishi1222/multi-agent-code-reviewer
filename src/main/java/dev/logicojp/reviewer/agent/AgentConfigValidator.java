package dev.logicojp.reviewer.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/// Validates {@link AgentConfig} instances.
/// Separated from the record to maintain single responsibility —
/// the record is a pure data carrier.
final class AgentConfigValidator {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfigValidator.class);

    // Required sections in outputFormat
    private static final List<String> REQUIRED_OUTPUT_SECTIONS = List.of(
        "Good Points",
        "Priority",
        "指摘の概要",
        "推奨対応",
        "効果"
    );

    private static final Pattern PRIORITY_PATTERN = Pattern.compile(
        "Critical|High|Medium|Low", Pattern.CASE_INSENSITIVE);

    private AgentConfigValidator() {
    }

    /// Validates that all required fields are present in the given config.
    /// @param config the agent config to validate
    /// @throws IllegalArgumentException if required fields are missing
    public static void validateRequired(AgentConfig config) {
        StringJoiner missing = collectMissingRequiredFields(config);
        // outputFormat may be null when loaded from an external template at runtime
        if (missing.length() > 0) {
            throw new IllegalArgumentException("Missing required agent fields: " + missing);
        }
        if (config.focusAreas().isEmpty()) {
            logger.warn("Agent '{}' has no focusAreas; proceeding with defaults.", config.name());
        }

        // Validate outputFormat required sections
        validateOutputFormat(config);
    }

    private static StringJoiner collectMissingRequiredFields(AgentConfig config) {
        var missing = new StringJoiner(", ");
        if (config.name() == null || config.name().isBlank()) {
            missing.add("name");
        }
        if (config.systemPrompt() == null || config.systemPrompt().isBlank()) {
            missing.add("systemPrompt");
        }
        if (config.instruction() == null || config.instruction().isBlank()) {
            missing.add("instruction");
        }
        return missing;
    }

    /// Validates that the outputFormat contains required sections.
    private static void validateOutputFormat(AgentConfig config) {
        String outputFormat = config.outputFormat();
        if (outputFormat == null || outputFormat.isBlank()) {
            return;
        }

        List<String> missingSections = new ArrayList<>();
        collectMissingOutputSections(outputFormat, missingSections);

        if (!missingSections.isEmpty()) {
            logger.warn("Agent '{}' outputFormat is missing recommended sections: {}",
                config.name(), String.join(", ", missingSections));
        }

        // Check for Priority keywords
        if (!PRIORITY_PATTERN.matcher(outputFormat).find()) {
            logger.warn("Agent '{}' outputFormat does not contain Priority levels (Critical/High/Medium/Low).",
                config.name());
        }
    }

    private static void collectMissingOutputSections(String outputFormat, List<String> missingSections) {
        for (String section : REQUIRED_OUTPUT_SECTIONS) {
            if (!outputFormat.contains(section)) {
                missingSections.add(section);
            }
        }
    }
}
