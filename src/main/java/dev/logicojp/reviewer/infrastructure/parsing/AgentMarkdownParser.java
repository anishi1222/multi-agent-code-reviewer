package dev.logicojp.reviewer.infrastructure.parsing;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.AgentDefinitionPolicy;
import dev.logicojp.reviewer.domain.agent.AgentFrontmatterMapper;
import dev.logicojp.reviewer.domain.agent.AgentSectionParser;
import dev.logicojp.reviewer.domain.agent.AgentSource;
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

    /// Result of a staged parse: either a valid config, or a rejection carrying the
    /// identifier of the rule that fired (ADR-0007 D4).
    ///
    /// @param config          the parsed config, or null when rejected
    /// @param ruleId          identifier of the violated rule, or null when accepted
    /// @param rejectionReason human-readable reason, or null when accepted
    public record ParseResult(AgentConfig config, String ruleId, String rejectionReason) {
        public boolean accepted() {
            return config != null;
        }

        public static ParseResult accept(AgentConfig config) {
            return new ParseResult(config, null, null);
        }

        public static ParseResult reject(String ruleId, String reason) {
            return new ParseResult(null, ruleId, reason);
        }

        /// Adapts a domain policy rejection, preserving its rule identifier.
        public static ParseResult reject(AgentDefinitionPolicy.PolicyResult policyResult) {
            return new ParseResult(null, policyResult.ruleId(), policyResult.reason());
        }
    }

    /// Parses a .agent.md file with staged validation, under the trust profile for
    /// `source`.
    ///
    /// @param mdFile file to parse
    /// @param source provenance of the directory this file was found in (ADR-0007 D1)
    /// @return accepted config, or a rejection carrying the violated rule identifier
    /// @throws IOException if the file cannot be read
    public ParseResult parseSafe(Path mdFile, AgentSource source) throws IOException {
        String content = Files.readString(mdFile);
        String filename = mdFile.getFileName().toString();
        return parseContentSafe(content, filename, source);
    }

    /// Parses a .agent.md file and returns an AgentConfig (null on policy rejection).
    ///
    /// @param mdFile file to parse
    /// @param source provenance of the directory this file was found in
    /// @return the parsed config, or null when the policy rejected it
    /// @throws IOException if the file cannot be read
    public AgentConfig parse(Path mdFile, AgentSource source) throws IOException {
        ParseResult result = parseSafe(mdFile, source);
        if (!result.accepted()) {
            logger.warn("Agent file rejected by policy [{}] ({}): {} — {}",
                result.ruleId(), source, mdFile.getFileName(), result.rejectionReason());
            return null;
        }
        return result.config();
    }

    /// Staged parse of markdown content with full policy validation under `source`'s
    /// trust profile.
    ///
    /// Every policy decision below is delegated to [AgentDefinitionPolicy], which
    /// ADR-0007 D2 designates the single owner of agent-definition trust-boundary
    /// policy. This method contributes I/O and sequencing only — it must not add
    /// limits of its own.
    ///
    /// @param content  raw file content
    /// @param filename source filename, used in messages and as a name fallback
    /// @param source   provenance of the directory this content was found in
    /// @return accepted config, or a rejection carrying the violated rule identifier
    public ParseResult parseContentSafe(String content, String filename, AgentSource source) {
        AgentDefinitionPolicy.PolicyResult rawCheck =
            AgentDefinitionPolicy.validateRawContent(content, filename, source);
        if (!rawCheck.accepted()) {
            return ParseResult.reject(rawCheck);
        }

        FrontmatterParser.FrontmatterResult parsed = FrontmatterParser.parse(content);
        if (!parsed.hasFrontmatter()) {
            return ParseResult.reject(AgentDefinitionPolicy.RULE_FRONTMATTER_MISSING,
                "agent file '%s' has no valid frontmatter".formatted(filename));
        }

        Map<String, String> metadata = parsed.fields();
        String body = parsed.body();

        if (!AgentDefinitionPolicy.isAgentEnabled(metadata)) {
            return ParseResult.reject(AgentDefinitionPolicy.RULE_AGENT_DISABLED,
                "agent '%s' is disabled (enabled: false)".formatted(filename));
        }

        AgentDefinitionPolicy.PolicyResult keyCheck =
            AgentDefinitionPolicy.auditFrontmatterKeys(metadata, filename, source);
        if (!keyCheck.accepted()) {
            return ParseResult.reject(keyCheck);
        }

        ParsedAgentMetadata parsedMetadata = frontmatterMapper.map(metadata, body, filename);
        AgentConfig config = buildAgentConfig(parsedMetadata, source);

        AgentDefinitionPolicy.PolicyResult parsedCheck =
            AgentDefinitionPolicy.validateParsed(config);
        if (!parsedCheck.accepted()) {
            return ParseResult.reject(parsedCheck);
        }

        return ParseResult.accept(config);
    }

    /// Parses markdown content and returns an AgentConfig (null on policy rejection).
    ///
    /// @param content  raw file content
    /// @param filename source filename
    /// @param source   provenance of the directory this content was found in
    /// @return the parsed config, or null when the policy rejected it
    public AgentConfig parseContent(String content, String filename, AgentSource source) {
        ParseResult result = parseContentSafe(content, filename, source);
        if (!result.accepted()) {
            logger.warn("Agent content rejected by policy [{}] ({}) for '{}': {}",
                result.ruleId(), source, filename, result.rejectionReason());
            return null;
        }
        return result.config();
    }

    private AgentConfig buildAgentConfig(ParsedAgentMetadata metadata, AgentSource source) {
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
            .source(source)
            .build();
        config.validateRequired();
        return config;
    }

    public static String extractNameFromFilename(String filename) {
        return AgentSectionParser.extractNameFromFilename(filename);
    }
}
