package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.util.FrontmatterParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/// Parses GitHub Copilot agent definition files (.agent.md format).
class AgentMarkdownParser {

    private static final Logger logger = LoggerFactory.getLogger(AgentMarkdownParser.class);

    private final AgentFrontmatterMapper frontmatterMapper = new AgentFrontmatterMapper();
    private final AgentSectionParser sectionParser;

    /// Creates a parser with no default output format.
    AgentMarkdownParser() {
        this(null);
    }

    /// Creates a parser with a default output format loaded from an external template.
    /// @param defaultOutputFormat Fallback output format when the agent file doesn't specify one
    AgentMarkdownParser(String defaultOutputFormat) {
        this.sectionParser = new AgentSectionParser(defaultOutputFormat);
    }

    /// Result of a staged parse: either a valid config or a rejection reason.
    record ParseResult(AgentConfig config, String rejectionReason) {
        boolean accepted() {
            return config != null;
        }

        static ParseResult accept(AgentConfig config) {
            return new ParseResult(config, null);
        }

        static ParseResult reject(String reason) {
            return new ParseResult(null, reason);
        }
    }

    /// Parses a .agent.md file with staged validation.
    ///
    /// **Stage 1** — raw-content policy checks (size, frontmatter presence).
    /// **Stage 2** — frontmatter parsing + enabled flag + key audit.
    /// **Stage 3** — section extraction + config construction.
    /// **Stage 4** — post-parse policy checks (model allowlist, name, focus-area limits).
    ///
    /// @param mdFile Path to the .agent.md file
    /// @return ParseResult with either a valid config or a rejection reason
    public ParseResult parseSafe(Path mdFile) throws IOException {
        String content = Files.readString(mdFile);
        String filename = mdFile.getFileName().toString();

        AgentDefinitionPolicy.PolicyResult rawCheck =
            AgentDefinitionPolicy.validateRawContent(content, filename);
        if (!rawCheck.accepted()) {
            return ParseResult.reject(rawCheck.reason());
        }

        return parseContentSafe(content, filename);
    }

    /// Parses a .agent.md file and returns an AgentConfig.
    /// @param mdFile Path to the .agent.md file
    /// @return AgentConfig parsed from the file
    public AgentConfig parse(Path mdFile) throws IOException {
        ParseResult result = parseSafe(mdFile);
        if (!result.accepted()) {
            logger.warn("Agent file rejected by policy: {} — {}", mdFile.getFileName(), result.rejectionReason());
            return null;
        }
        return result.config();
    }

    /// Staged parse of markdown content with full policy validation.
    ParseResult parseContentSafe(String content, String filename) {
        AgentDefinitionPolicy.PolicyResult rawCheck =
            AgentDefinitionPolicy.validateRawContent(content, filename);
        if (!rawCheck.accepted()) {
            return ParseResult.reject(rawCheck.reason());
        }

        FrontmatterParser.Parsed parsed = FrontmatterParser.parse(content);
        if (!parsed.hasFrontmatter()) {
            return ParseResult.reject(
                "agent file '%s' has no valid frontmatter".formatted(filename));
        }

        if (!AgentDefinitionPolicy.isAgentEnabled(parsed.metadata())) {
            return ParseResult.reject("agent '%s' is disabled (enabled: false)".formatted(filename));
        }

        AgentDefinitionPolicy.auditFrontmatterKeys(parsed.metadata(), filename);

        ParsedAgentMetadata metadata = frontmatterMapper.map(parsed, filename);
        AgentConfig config = buildAgentConfig(metadata);

        AgentDefinitionPolicy.PolicyResult parsedCheck =
            AgentDefinitionPolicy.validateParsed(config);
        if (!parsedCheck.accepted()) {
            return ParseResult.reject(parsedCheck.reason());
        }

        return ParseResult.accept(config);
    }

    /// Parses markdown content and returns an AgentConfig.
    /// Kept for backward compatibility. Prefer {@link #parseContentSafe}.
    public AgentConfig parseContent(String content, String filename) {
        ParseResult result = parseContentSafe(content, filename);
        if (!result.accepted()) {
            logger.warn("Agent content rejected by policy for '{}': {}", filename, result.rejectionReason());
            return null;
        }
        return result.config();
    }

    private AgentConfig buildAgentConfig(ParsedAgentMetadata metadata) {
        Map<String, String> sections = sectionParser.extractSections(metadata.body());
        String systemPrompt = sectionParser.systemPrompt(sections, metadata.body());
        String instruction = sectionParser.instruction(sections);
        String outputFormat = sectionParser.outputFormat(sections);
        List<String> focusAreas = sectionParser.focusAreas(sections, metadata.body());

        AgentConfig config = AgentConfig.builder()
            .name(metadata.name())
            .displayName(metadata.displayName())
            .model(metadata.model())
            .systemPrompt(systemPrompt)
            .instruction(instruction)
            .outputFormat(outputFormat)
            .focusAreas(focusAreas)
            .skills(List.of())
            .peerModel(metadata.peerModel())
            .rubberDuckEnabled(metadata.rubberDuckEnabled())
            .dialogueRounds(metadata.dialogueRounds())
            .language(metadata.language())
            .build();
        config.validateRequired();
        return config;
    }

    static String extractNameFromFilename(String filename) {
        return AgentSectionParser.extractNameFromFilename(filename);
    }
}
