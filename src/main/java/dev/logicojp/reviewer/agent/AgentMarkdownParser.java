package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.util.FrontmatterParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Parses GitHub Copilot agent definition files (.agent.md format).
///
/// Format example:
/// ```
/// ---
/// name: security-reviewer
/// description: Security code review agent
/// model: claude-sonnet-4
/// ---
///
/// # Security Reviewer
///
/// You are a security expert...
///
/// ## Focus Areas
/// - SQL Injection
/// - XSS
/// ```
class AgentMarkdownParser {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentMarkdownParser.class);
    
    // Pattern to extract focus areas from markdown list
    private static final Pattern FOCUS_AREA_PATTERN = Pattern.compile(
        "##\\s*Focus Areas\\s*\\n((?:\\s*[-*]\\s*.+\\n?)+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SECTION_HEADER_PATTERN = Pattern.compile("^##\\s+(.+)$");
    private static final Set<String> RECOGNIZED_SECTIONS = Set.of(
        "role",
        "instruction",
        "output format",
        "focus areas"
    );
    private static final String DEFAULT_FOCUS_AREA = "一般的なコード品質";

    private final String defaultOutputFormat;

    /// Creates a parser with no default output format.
     AgentMarkdownParser() {
        this(null);
    }

    /// Creates a parser with a default output format loaded from an external template.
    /// @param defaultOutputFormat Fallback output format when the agent file doesn't specify one
     AgentMarkdownParser(String defaultOutputFormat) {
        this.defaultOutputFormat = defaultOutputFormat;
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

        // Stage 1: raw-content policy
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
        // Stage 1 (if not already done by caller): raw-content policy
        AgentDefinitionPolicy.PolicyResult rawCheck =
            AgentDefinitionPolicy.validateRawContent(content, filename);
        if (!rawCheck.accepted()) {
            return ParseResult.reject(rawCheck.reason());
        }

        // Stage 2: frontmatter parsing
        FrontmatterParser.Parsed parsed = FrontmatterParser.parse(content);
        if (!parsed.hasFrontmatter()) {
            return ParseResult.reject(
                "agent file '%s' has no valid frontmatter".formatted(filename));
        }

        // Stage 2b: enabled flag check
        if (!AgentDefinitionPolicy.isAgentEnabled(parsed.metadata())) {
            return ParseResult.reject("agent '%s' is disabled (enabled: false)".formatted(filename));
        }

        // Stage 2c: audit unknown frontmatter keys
        AgentDefinitionPolicy.auditFrontmatterKeys(parsed.metadata(), filename);

        // Stage 3: construct config
        ParsedAgentMetadata metadata = parseWithFrontmatter(parsed, filename);
        AgentConfig config = buildAgentConfig(
            metadata.name(),
            metadata.displayName(),
            metadata.model(),
            metadata.body(),
            metadata.peerModel(),
            metadata.rubberDuckEnabled(),
            metadata.dialogueRounds(),
            metadata.language()
        );

        // Stage 4: post-parse policy validation
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

    private ParsedAgentMetadata parseWithFrontmatter(FrontmatterParser.Parsed parsed, String filename) {
        Map<String, String> metadata = parsed.metadata();
        String defaultName = extractNameFromFilename(filename);
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

    private ParsedAgentMetadata parseWithoutFrontmatter(String content, String filename) {
        logger.warn("No valid frontmatter found in {}", filename);
        String name = extractNameFromFilename(filename);
        return new ParsedAgentMetadata(name, name, ModelConfig.DEFAULT_MODEL, content,
            null, false, AgentConfig.DEFAULT_DIALOGUE_ROUNDS, AgentConfig.DEFAULT_LANGUAGE);
    }

    /// Common AgentConfig construction from extracted metadata and body.
    private AgentConfig buildAgentConfig(String name, String displayName,
                                          String model, String body,
                                          String peerModel, boolean rubberDuckEnabled,
                                          int dialogueRounds, String language) {
        Map<String, String> sections = extractSections(body);
        String systemPrompt = getSection(sections, "role");
        String instruction = getSection(sections, "instruction");
        String outputFormat = resolveOutputFormat(sections);

        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = body.trim();
        }

        List<String> focusAreas = resolveFocusAreas(sections, body);

        AgentConfig config = AgentConfig.builder()
            .name(name)
            .displayName(displayName)
            .model(model)
            .systemPrompt(systemPrompt)
            .instruction(instruction)
            .outputFormat(outputFormat)
            .focusAreas(focusAreas)
            .skills(List.of())
            .peerModel(peerModel)
            .rubberDuckEnabled(rubberDuckEnabled)
            .dialogueRounds(dialogueRounds)
            .language(language)
            .build();
        config.validateRequired();
        return config;
    }

    private String resolveOutputFormat(Map<String, String> sections) {
        String outputFormat = getSection(sections, "output format");
        if ((outputFormat == null || outputFormat.isBlank()) && defaultOutputFormat != null) {
            return defaultOutputFormat;
        }
        return outputFormat;
    }

    private List<String> resolveFocusAreas(Map<String, String> sections, String body) {
        String focusAreasSection = getSection(sections, "focus areas");
        return focusAreasSection != null
            ? parseFocusAreaItems(focusAreasSection)
            : extractFocusAreas(body);
    }
    
    private List<String> extractFocusAreas(String body) {
        List<String> focusAreas = new ArrayList<>();
        
        Matcher matcher = FOCUS_AREA_PATTERN.matcher(body);
        if (matcher.find()) {
            String listContent = matcher.group(1);
            focusAreas = new ArrayList<>(parseBulletItems(listContent));
        }
        
        // If no focus areas found, return a default
        if (focusAreas.isEmpty()) {
            focusAreas.add(DEFAULT_FOCUS_AREA);
        }

        return List.copyOf(focusAreas);
    }

    /// Parses focus area items from an already-extracted section body (header stripped).
    /// Falls back to default if no bullet items are found.
    private List<String> parseFocusAreaItems(String sectionContent) {
        List<String> focusAreas = new ArrayList<>(parseBulletItems(sectionContent));
        if (focusAreas.isEmpty()) {
            logger.warn("Focus Areas section found but contains no bullet items; using default.");
            focusAreas.add(DEFAULT_FOCUS_AREA);
        }
        return List.copyOf(focusAreas);
    }

    private List<String> parseBulletItems(String text) {
        return text.lines()
            .map(String::trim)
            .filter(line -> line.startsWith("-") || line.startsWith("*"))
            .map(line -> line.substring(1).trim())
            .filter(item -> !item.isEmpty())
            .toList();
    }

    private Map<String, String> extractSections(String body) {
        Map<String, StringBuilder> sectionBuilders = new LinkedHashMap<>();
        String currentKey = null;

        for (String line : body.lines().toList()) {
            String sectionKey = extractRecognizedSectionKey(line);
            if (sectionKey != null) {
                if (sectionBuilders.containsKey(sectionKey)) {
                    logger.warn("Duplicate section '## {}' found; using the last occurrence.", sectionKey);
                }
                currentKey = sectionKey;
                sectionBuilders.put(currentKey, new StringBuilder());
                continue;
            }

            if (currentKey != null) {
                sectionBuilders.get(currentKey).append(line).append("\n");
            }
        }

        Map<String, String> sections = new LinkedHashMap<>();
        for (Map.Entry<String, StringBuilder> entry : sectionBuilders.entrySet()) {
            sections.put(entry.getKey(), entry.getValue().toString().trim());
        }
        return sections;
    }

    private String extractRecognizedSectionKey(String line) {
        Matcher matcher = SECTION_HEADER_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            return null;
        }
        String sectionKey = normalizeSectionKey(matcher.group(1));
        return RECOGNIZED_SECTIONS.contains(sectionKey) ? sectionKey : null;
    }

    private String getSection(Map<String, String> sections, String... keys) {
        for (String key : keys) {
            String normalized = normalizeSectionKey(key);
            if (sections.containsKey(normalized)) {
                return sections.get(normalized);
            }
        }
        return null;
    }

    private String normalizeSectionKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
    
    static String extractNameFromFilename(String filename) {
        // Remove .agent.md or .md suffix
        String name = filename;
        if (name.endsWith(".agent.md")) {
            name = name.substring(0, name.length() - ".agent.md".length());
        } else if (name.endsWith(".md")) {
            name = name.substring(0, name.length() - ".md".length());
        }
        return name;
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

    private record ParsedAgentMetadata(String name, String displayName, String model, String body,
                                        String peerModel, boolean rubberDuckEnabled,
                                        int dialogueRounds, String language) {
    }
}
