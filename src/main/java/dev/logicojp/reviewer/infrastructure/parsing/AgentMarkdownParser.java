package dev.logicojp.reviewer.infrastructure.parsing;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.AgentDefinitionPolicy;
import dev.logicojp.reviewer.domain.agent.AgentFrontmatterMapper;
import dev.logicojp.reviewer.domain.agent.AgentSectionParser;
import dev.logicojp.reviewer.domain.agent.ParsedAgentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/// Parses GitHub Copilot agent definition files (.agent.md format).
///
/// Infrastructure adapter: handles file I/O and YAML frontmatter parsing,
/// then delegates pure mapping/validation to domain-layer objects.
public class AgentMarkdownParser {

    private static final Logger logger = LoggerFactory.getLogger(AgentMarkdownParser.class);

    private final AgentFrontmatterMapper frontmatterMapper;
    private final AgentSectionParser sectionParser;

    /// Creates a parser with no default output format.
    public AgentMarkdownParser() {
        this(null);
    }

    /// Creates a parser with a default output format loaded from an external template.
    public AgentMarkdownParser(String defaultOutputFormat) {
        this.frontmatterMapper = new AgentFrontmatterMapper();
        this.sectionParser = new AgentSectionParser(defaultOutputFormat);
    }

    /// Result of a staged parse: either a valid config or a rejection reason.
    public record ParseResult(AgentConfig config, String rejectionReason) {
        public boolean accepted() {
            return config != null;
        }

        public static ParseResult accept(AgentConfig config) {
            return new ParseResult(config, null);
        }

        public static ParseResult reject(String reason) {
            return new ParseResult(null, reason);
        }
    }

    /// Parses a .agent.md file with staged validation.
    public ParseResult parseSafe(Path mdFile) throws IOException {
        String content = Files.readString(mdFile);
        String filename = mdFile.getFileName().toString();
        return parseContentSafe(content, filename);
    }

    /// Parses a .agent.md file and returns an AgentConfig (null on policy rejection).
    public AgentConfig parse(Path mdFile) throws IOException {
        ParseResult result = parseSafe(mdFile);
        if (!result.accepted()) {
            logger.warn("Agent file rejected by policy: {} — {}", mdFile.getFileName(), result.rejectionReason());
            return null;
        }
        return result.config();
    }

    /// Staged parse of markdown content with full policy validation.
    public ParseResult parseContentSafe(String content, String filename) {
        AgentDefinitionPolicy.PolicyResult rawCheck =
            AgentDefinitionPolicy.validateRawContent(content, filename);
        if (!rawCheck.accepted()) {
            return ParseResult.reject(rawCheck.reason());
        }

        FrontmatterParser.FrontmatterResult parsed = FrontmatterParser.parse(content);
        if (!parsed.hasFrontmatter()) {
            return ParseResult.reject(
                "agent file '%s' has no valid frontmatter".formatted(filename));
        }

        Map<String, String> metadata = parsed.fields();
        String body = parsed.body();

        if (!AgentDefinitionPolicy.isAgentEnabled(metadata)) {
            return ParseResult.reject("agent '%s' is disabled (enabled: false)".formatted(filename));
        }

        AgentDefinitionPolicy.auditFrontmatterKeys(metadata, filename);

        ParsedAgentMetadata parsedMetadata = frontmatterMapper.map(metadata, body, filename);
        AgentConfig config = buildAgentConfig(parsedMetadata);

        AgentDefinitionPolicy.PolicyResult parsedCheck =
            AgentDefinitionPolicy.validateParsed(config);
        if (!parsedCheck.accepted()) {
            return ParseResult.reject(parsedCheck.reason());
        }

        return ParseResult.accept(config);
    }

    /// Parses markdown content and returns an AgentConfig (null on policy rejection).
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

    public static String extractNameFromFilename(String filename) {
        return AgentSectionParser.extractNameFromFilename(filename);
    }
}
