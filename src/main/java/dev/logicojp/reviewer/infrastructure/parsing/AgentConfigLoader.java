package dev.logicojp.reviewer.infrastructure.parsing;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.instruction.CustomInstructionSafetyValidator;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import dev.logicojp.reviewer.infrastructure.config.SkillConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/// Loads agent configurations from external files.
/// Supports GitHub Copilot agent definition format (.agent.md).
///
/// Agent files can be placed in:
/// - agents/ directory
/// - .github/agents/ directory
///
/// Skills are loaded from the configured skills directory following the Agent Skills spec.
public class AgentConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfigLoader.class);

    private final List<Path> agentDirectories;
    private final AgentMarkdownParser markdownParser;
    private final SkillMarkdownParser skillParser;
    private final String skillsDirectory;

    public static Builder builder(List<Path> agentDirectories) {
        return new Builder(agentDirectories);
    }

    public static final class Builder {
        private final List<Path> agentDirectories;
        private SkillConfig skillConfig = SkillConfig.defaults();
        private String defaultOutputFormat;

        private Builder(List<Path> agentDirectories) {
            this.agentDirectories = List.copyOf(agentDirectories);
        }

        public Builder skillConfig(SkillConfig skillConfig) {
            this.skillConfig = skillConfig != null ? skillConfig : SkillConfig.defaults();
            return this;
        }

        public Builder defaultOutputFormat(String defaultOutputFormat) {
            this.defaultOutputFormat = defaultOutputFormat;
            return this;
        }

        public AgentConfigLoader build() {
            return new AgentConfigLoader(agentDirectories, skillConfig, defaultOutputFormat);
        }
    }

    /// Creates a loader with a single agents directory and default skill settings.
    public AgentConfigLoader(Path agentsDirectory) {
        this(List.of(agentsDirectory), SkillConfig.defaults(), null);
    }

    /// Creates a loader with multiple agent directories, skill configuration,
    /// and an optional default output format.
    public AgentConfigLoader(List<Path> agentDirectories, SkillConfig skillConfig,
                             String defaultOutputFormat) {
        this.agentDirectories = List.copyOf(agentDirectories);
        this.markdownParser = new AgentMarkdownParser(defaultOutputFormat);
        this.skillParser = new SkillMarkdownParser(skillConfig.filename());
        this.skillsDirectory = skillConfig.directory();
    }

    /// Loads all agent configurations from all configured directories.
    public Map<String, AgentConfig> loadAllAgents() throws IOException {
        return loadAgentsInternal(null);
    }

    /// Loads specific agents by name.
    public Map<String, AgentConfig> loadAgents(List<String> agentNames) throws IOException {
        Map<String, AgentConfig> agents = loadAgentsInternal(new HashSet<>(agentNames));
        for (String name : agentNames) {
            if (!agents.containsKey(name)) {
                logger.warn("Agent not found: {}", name);
            }
        }
        return agents;
    }

    private Map<String, AgentConfig> loadAgentsInternal(Set<String> filter) throws IOException {
        Map<String, AgentConfig> agents = new LinkedHashMap<>();
        List<SkillDefinition> globalSkills = loadGlobalSkills();
        for (Path directory : agentDirectories) {
            if (!isExistingDirectory(directory)) continue;
            loadAgentsFromDirectory(directory, filter, globalSkills, agents);
        }
        if (agents.isEmpty()) {
            logger.warn("No agents found in any configured directory");
        }
        return agents;
    }

    private boolean isExistingDirectory(Path directory) {
        if (!Files.exists(directory)) {
            logger.debug("Agents directory does not exist: {}", directory);
            return false;
        }
        return true;
    }

    private void loadAgentsFromDirectory(Path directory, Set<String> filter,
                                         List<SkillDefinition> globalSkills,
                                         Map<String, AgentConfig> agents) throws IOException {
        logger.info("Loading agents from: {}", directory);
        List<Path> files = listAgentFiles(directory, filter);
        for (Path file : files) {
            parseAndStoreAgent(file, globalSkills, agents);
        }
    }

    private void parseAndStoreAgent(Path file, List<SkillDefinition> globalSkills,
                                    Map<String, AgentConfig> agents) {
        try {
            Optional<AgentConfig> parsed = parseAgent(file, globalSkills);
            if (parsed.isEmpty()) return;
            AgentConfig config = parsed.get();
            agents.put(config.name(), config);
            logger.info("Loaded agent: {} from {}", config.name(), file.getFileName());
        } catch (IOException | IllegalArgumentException | UncheckedIOException e) {
            logger.error("Failed to load agent from {}: {}", file, e.getMessage(), e);
        }
    }

    private Optional<AgentConfig> parseAgent(Path file, List<SkillDefinition> globalSkills) throws IOException {
        AgentMarkdownParser.ParseResult parseResult = markdownParser.parseSafe(file);
        if (!parseResult.accepted()) {
            logger.warn("Agent rejected by policy: {} — {}", file.getFileName(), parseResult.rejectionReason());
            return Optional.empty();
        }
        AgentConfig config = parseResult.config();
        Optional<String> suspiciousField = firstSuspiciousField(config);
        if (suspiciousField.isPresent()) {
            logger.warn("Agent file contains suspicious patterns in '{}', skipping: {}",
                suspiciousField.get(), file);
            return Optional.empty();
        }
        AgentConfig withSkills = applySkills(config, globalSkills);
        withSkills.validateRequired();
        return Optional.of(withSkills);
    }

    private AgentConfig applySkills(AgentConfig config, List<SkillDefinition> globalSkills) {
        List<SkillDefinition> agentSkills = collectSkillsForAgent(config.name(), globalSkills);
        if (agentSkills.isEmpty()) return config;
        logger.info("Loaded {} skills for agent: {}", agentSkills.size(), config.name());
        return config.withSkills(agentSkills);
    }

    private List<SkillDefinition> collectSkillsForAgent(String agentName,
                                                         List<SkillDefinition> globalSkills) {
        return globalSkills.stream()
            .filter(skill -> isSkillApplicableToAgent(skill, agentName))
            .toList();
    }

    private boolean isSkillApplicableToAgent(SkillDefinition skill, String agentName) {
        String skillAgent = skill.metadata().get("agent");
        return skillAgent == null || skillAgent.equals(agentName);
    }

    private List<SkillDefinition> loadGlobalSkills() {
        Path skillsRoot = Path.of(skillsDirectory);
        if (!Files.isDirectory(skillsRoot)) {
            logger.debug("Global skills directory does not exist: {}", skillsRoot);
            return List.of();
        }
        List<Path> skillFiles = skillParser.discoverSkills(skillsRoot);
        if (skillFiles.isEmpty()) return List.of();
        List<SkillDefinition> skills = new ArrayList<>();
        for (Path skillFile : skillFiles) {
            try {
                SkillDefinition skill = skillParser.parse(skillFile);
                skills.add(skill);
                logger.info("Loaded global skill: {} from {}", skill.id(),
                    skillFile.getParent().getFileName());
            } catch (IOException | IllegalArgumentException | UncheckedIOException e) {
                logger.error("Failed to load skill from {}: {}", skillFile, e.getMessage(), e);
            }
        }
        logger.info("Loaded {} global skills from {}", skills.size(), skillsRoot);
        return List.copyOf(skills);
    }

    private boolean isAgentFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return filename.endsWith(".agent.md");
    }

    public List<String> listAvailableAgents() throws IOException {
        Set<String> agentNames = new TreeSet<>();
        for (Path directory : agentDirectories) {
            if (!Files.exists(directory)) continue;
            List<Path> files = listAgentFiles(directory);
            for (Path file : files) {
                agentNames.add(extractAgentName(file));
            }
        }
        return List.copyOf(agentNames);
    }

    private List<Path> listAgentFiles(Path directory) throws IOException {
        return listAgentFiles(directory, null);
    }

    private List<Path> listAgentFiles(Path directory, Set<String> filter) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                .filter(this::isAgentFile)
                .filter(path -> filter == null || filter.contains(extractAgentName(path)))
                .toList();
        }
    }

    private static final List<Map.Entry<String, java.util.function.Function<AgentConfig, String>>> FIELD_EXTRACTORS = List.of(
        Map.entry("role", AgentConfig::systemPrompt),
        Map.entry("instruction", AgentConfig::instruction),
        Map.entry("output-format", AgentConfig::outputFormat),
        Map.entry("display-name", AgentConfig::displayName),
        Map.entry("model", AgentConfig::model),
        Map.entry("name", AgentConfig::name)
    );

    private Optional<String> firstSuspiciousField(AgentConfig config) {
        Optional<String> scalarMatch = FIELD_EXTRACTORS.stream()
            .filter(entry -> containsSuspicious(entry.getValue().apply(config)))
            .map(Map.Entry::getKey)
            .findFirst();
        if (scalarMatch.isPresent()) return scalarMatch;
        return config.focusAreas().stream()
            .filter(this::containsSuspicious)
            .findFirst()
            .map(_ -> "focus-areas");
    }

    private boolean containsSuspicious(String value) {
        return CustomInstructionSafetyValidator.containsSuspiciousPattern(value);
    }

    private String extractAgentName(Path file) {
        return AgentMarkdownParser.extractNameFromFilename(file.getFileName().toString());
    }

    List<Path> getAgentDirectories() {
        return agentDirectories;
    }
}
