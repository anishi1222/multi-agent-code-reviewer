package dev.logicojp.reviewer.infrastructure.parsing;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.AgentRejection;
import dev.logicojp.reviewer.domain.agent.AgentSource;
import dev.logicojp.reviewer.domain.agent.AgentSourceDirectory;
import dev.logicojp.reviewer.domain.instruction.CustomInstructionSafetyValidator;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import dev.logicojp.reviewer.infrastructure.config.SkillConfig;
import dev.logicojp.reviewer.shared.SkillBudget;
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
import java.util.stream.Collectors;
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

    /// Marker every end-of-load summary line starts with, so operators and tests can
    /// locate the line without matching on the rest of the sentence (ADR-0007 D4).
    public static final String AGENT_LOAD_SUMMARY_PREFIX = "Agent load summary:";

    /// Rejection rule for a definition that threw while being read or built.
    public static final String RULE_LOAD_FAILED = "AGENT-LOAD-FAILED";

    /// Rejection rule for a definition whose text matched a prompt-injection pattern.
    public static final String RULE_SUSPICIOUS_PATTERN = "AGENT-SUSPICIOUS-PATTERN";

    private final List<AgentSourceDirectory> agentDirectories;
    private final AgentMarkdownParser markdownParser;
    private final SkillMarkdownParser skillParser;
    private final String skillsDirectory;

    /// Maximum size of one skill file **on disk, in bytes**, checked before the file is parsed.
    private final int maxSkillFileBytes;

    /// Maximum **character** length of one skill's injected content
    /// (`name` + `description` + `prompt`).
    private final int maxSkillContentChars;

    /// Maximum **cumulative character** length of every skill assigned to a single agent
    /// through `metadata.agent`.
    private final int maxAssignedSkillTotalChars;

    /// Maximum **character** length of the rendered "Assigned Review Skills" section that
    /// `AgentPromptBuilder` appends to an agent's instruction.
    ///
    /// Unlike the three above, this budget is not enforced here — it is handed to `domain` as a
    /// [SkillBudget] value, because the section it bounds only exists once the prompt is rendered.
    private final int maxRenderedSkillSectionChars;

    public static Builder builder(List<AgentSourceDirectory> agentDirectories) {
        return new Builder(agentDirectories);
    }

    public static final class Builder {
        private final List<AgentSourceDirectory> agentDirectories;
        private SkillConfig skillConfig = SkillConfig.defaults();
        private String defaultOutputFormat;

        private Builder(List<AgentSourceDirectory> agentDirectories) {
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

    /// Creates a loader for a single directory whose contents carry the given provenance.
    ///
    /// @param agentsDirectory directory paired with the provenance of its contents
    public AgentConfigLoader(AgentSourceDirectory agentsDirectory) {
        this(List.of(agentsDirectory), SkillConfig.defaults(), null);
    }

    /// Creates a loader with multiple agent directories, skill configuration,
    /// and an optional default output format.
    ///
    /// @param agentDirectories   directories to scan, each paired with the provenance of its
    ///                           contents (ADR-0007 D1). Provenance is decided by the
    ///                           composition root and is only carried here, never recomputed.
    /// @param skillConfig        skill discovery and budget settings
    /// @param defaultOutputFormat fallback output-format template, or null
    public AgentConfigLoader(List<AgentSourceDirectory> agentDirectories, SkillConfig skillConfig,
                             String defaultOutputFormat) {
        this.agentDirectories = List.copyOf(agentDirectories);
        this.markdownParser = new AgentMarkdownParser(defaultOutputFormat);
        this.skillParser = new SkillMarkdownParser(skillConfig.filename());
        this.skillsDirectory = skillConfig.directory();
        // These four budgets are all sourced from the single
        // `reviewer.skills.max-parameter-value-length` knob, but they are NOT interchangeable:
        // they measure four different quantities, and the first is counted in bytes while the
        // others are counted in UTF-16 characters. They are kept as separate fields so that
        // each call site declares which budget it is applying instead of hiding behind one alias.
        int sharedSkillBudget = skillConfig.maxParameterValueLength();
        this.maxSkillFileBytes = sharedSkillBudget;
        this.maxSkillContentChars = sharedSkillBudget;
        this.maxAssignedSkillTotalChars = sharedSkillBudget;
        this.maxRenderedSkillSectionChars = sharedSkillBudget;
    }

    /// Outcome of a load: the agents that survived validation, plus every definition that
    /// was refused and why (ADR-0007 D4).
    ///
    /// @param agents           accepted agents, keyed by name, in discovery order
    /// @param rejections       definitions refused by policy, in discovery order
    /// @param discoveredSkills valid global skills from the same discovery pass
    public record AgentLoadReport(
        Map<String, AgentConfig> agents,
        List<AgentRejection> rejections,
        List<SkillDefinition> discoveredSkills
    ) {
        public AgentLoadReport {
            agents = agents == null ? Map.of() : Map.copyOf(agents);
            rejections = rejections == null ? List.of() : List.copyOf(rejections);
            discoveredSkills = discoveredSkills == null ? List.of() : List.copyOf(discoveredSkills);
        }
    }

    /// Loads all agent configurations from all configured directories.
    ///
    /// Rejected definitions are dropped individually and do not abort the run; use
    /// [#loadAllAgentsWithReport()] when the caller needs to see what was dropped.
    ///
    /// @return accepted agents keyed by name
    /// @throws IOException if a directory cannot be listed
    public Map<String, AgentConfig> loadAllAgents() throws IOException {
        return loadAllAgentsWithReport().agents();
    }

    /// Loads all agent configurations and reports the definitions that were refused.
    ///
    /// @return accepted agents plus the rejection records
    /// @throws IOException if a directory cannot be listed
    public AgentLoadReport loadAllAgentsWithReport() throws IOException {
        return loadAgentsInternal(null);
    }

    /// Loads specific agents by name.
    ///
    /// @param agentNames names to load
    /// @return accepted agents keyed by name
    /// @throws IOException if a directory cannot be listed
    public Map<String, AgentConfig> loadAgents(List<String> agentNames) throws IOException {
        Map<String, AgentConfig> agents = loadAgentsInternal(new HashSet<>(agentNames)).agents();
        for (String name : agentNames) {
            if (!agents.containsKey(name)) {
                logger.warn("Agent not found: {}", name);
            }
        }
        return agents;
    }

    private AgentLoadReport loadAgentsInternal(Set<String> filter) throws IOException {
        Map<String, AgentConfig> agents = new LinkedHashMap<>();
        List<AgentRejection> rejections = new ArrayList<>();
        List<SkillDefinition> globalSkills = loadGlobalSkills();
        for (AgentSourceDirectory directory : agentDirectories) {
            if (!isExistingDirectory(directory.path())) continue;
            loadAgentsFromDirectory(directory, filter, globalSkills, agents, rejections);
        }
        reportOutcome(agents, rejections);
        return new AgentLoadReport(agents, rejections, globalSkills);
    }

    /// Emits the end-of-load summary required by ADR-0007 D4.
    ///
    /// The line is emitted on **every** load, including when nothing was refused. A summary
    /// that only appears on failure is one an operator never learns to look for, and its
    /// absence is then indistinguishable from "no rejections". Emitting it unconditionally
    /// also lets a test assert the line exists rather than assert on a negative.
    ///
    /// Both branches log at levels that are enabled by the shipped `logback.xml`
    /// (`dev.logicojp.reviewer` = INFO), so the summary is visible at the default level.
    private void reportOutcome(Map<String, AgentConfig> agents, List<AgentRejection> rejections) {
        if (rejections.isEmpty()) {
            logger.info("{} {} agent(s) accepted, 0 rejected", AGENT_LOAD_SUMMARY_PREFIX, agents.size());
        } else {
            logger.warn("{} {} agent(s) accepted, {} rejected: {}",
                AGENT_LOAD_SUMMARY_PREFIX, agents.size(), rejections.size(),
                rejections.stream().map(AgentRejection::describe).collect(Collectors.joining("; ")));
        }
        if (agents.isEmpty()) {
            // ADR-0007 D4: zero-agent behaviour is unchanged (warn and continue), but the
            // wording must let an operator tell "this repository defines none" apart from
            // "every definition was refused" — previously both produced the same sentence.
            if (rejections.isEmpty()) {
                logger.warn("No agents found in any configured directory (no definition files present)");
            } else {
                logger.warn("No agents found in any configured directory ({} definition(s) were rejected by policy)",
                    rejections.size());
            }
        }
    }

    private boolean isExistingDirectory(Path directory) {
        if (!Files.exists(directory)) {
            logger.debug("Agents directory does not exist: {}", directory);
            return false;
        }
        return true;
    }

    private void loadAgentsFromDirectory(AgentSourceDirectory directory, Set<String> filter,
                                         List<SkillDefinition> globalSkills,
                                         Map<String, AgentConfig> agents,
                                         List<AgentRejection> rejections) throws IOException {
        logger.info("Loading agents from: {} ({})", directory.path(), directory.source());
        List<Path> files = listAgentFiles(directory.path(), filter);
        for (Path file : files) {
            parseAndStoreAgent(file, directory.source(), globalSkills, agents, rejections);
        }
    }

    private void parseAndStoreAgent(Path file, AgentSource source, List<SkillDefinition> globalSkills,
                                    Map<String, AgentConfig> agents, List<AgentRejection> rejections) {
        try {
            Optional<AgentConfig> parsed = parseAgent(file, source, globalSkills, rejections);
            if (parsed.isEmpty()) return;
            AgentConfig config = parsed.get();
            agents.put(config.name(), config);
            logger.info("Loaded agent: {} from {}", config.name(), file.getFileName());
        } catch (IOException | IllegalArgumentException | UncheckedIOException e) {
            // A malformed definition must not abort the run (ADR-0007 D4); it is recorded so
            // the summary can account for it rather than leaving the agent silently absent.
            rejections.add(new AgentRejection(file.getFileName().toString(), source,
                RULE_LOAD_FAILED, String.valueOf(e.getMessage())));
            logger.error("Failed to load agent from {}: {}", file, e.getMessage(), e);
        }
    }

    private Optional<AgentConfig> parseAgent(Path file, AgentSource source,
                                             List<SkillDefinition> globalSkills,
                                             List<AgentRejection> rejections) throws IOException {
        AgentMarkdownParser.ParseResult parseResult = markdownParser.parseSafe(file, source);
        if (!parseResult.accepted()) {
            AgentRejection rejection = new AgentRejection(file.getFileName().toString(), source,
                parseResult.ruleId(), parseResult.rejectionReason());
            rejections.add(rejection);
            logger.warn("Agent rejected by policy: {}", rejection.describe());
            return Optional.empty();
        }
        AgentConfig config = parseResult.config();
        Optional<String> suspiciousField = firstSuspiciousField(config);
        if (suspiciousField.isPresent()) {
            AgentRejection rejection = new AgentRejection(file.getFileName().toString(), source,
                RULE_SUSPICIOUS_PATTERN,
                "field '%s' contains a suspicious prompt-injection pattern".formatted(suspiciousField.get()));
            rejections.add(rejection);
            logger.warn("Agent rejected by policy: {}", rejection.describe());
            return Optional.empty();
        }
        // Attached here rather than inside applySkills(), which returns early for agents that
        // have no assigned skills — every loaded agent must carry the configured budget.
        AgentConfig withSkills = applySkills(config, globalSkills)
            .withSkillBudget(new SkillBudget(maxRenderedSkillSectionChars));
        withSkills.validateRequired();
        return Optional.of(withSkills);
    }

    private AgentConfig applySkills(AgentConfig config, List<SkillDefinition> globalSkills) {
        List<SkillDefinition> agentSkills = enforceAssignedSkillBudget(
            config.name(),
            collectSkillsForAgent(config.name(), globalSkills)
        );
        if (agentSkills.isEmpty()) {
            return config;
        }
        logger.info("Loaded {} skills for agent: {}", agentSkills.size(), config.name());
        return config.withSkills(agentSkills);
    }

    private List<SkillDefinition> enforceAssignedSkillBudget(String agentName,
                                                             List<SkillDefinition> skills) {
        List<SkillDefinition> accepted = new ArrayList<>(skills.size());
        int assignedPromptLength = 0;
        for (SkillDefinition skill : skills) {
            if (!agentName.equals(skill.metadata().get("agent"))) {
                accepted.add(skill);
                continue;
            }

            int skillLength = skill.name().length()
                + skill.description().length()
                + skill.prompt().length();
            if (assignedPromptLength + skillLength > maxAssignedSkillTotalChars) {
                logger.warn("Assigned review skill budget exceeded for agent '{}', skipping skill '{}'",
                    agentName, skill.id());
                continue;
            }
            assignedPromptLength += skillLength;
            accepted.add(skill);
        }
        return List.copyOf(accepted);
    }

    /// Collects skills for a specific agent from .github/skills/.
    ///
    /// Skills are matched to agents via the `metadata.agent` field.
    /// Skills without an agent metadata field are available to all agents.
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
                if (Files.size(skillFile) > maxSkillFileBytes) {
                    logger.warn("Skill file exceeds maximum size ({} bytes), skipping: {}",
                        maxSkillFileBytes, skillFile);
                    continue;
                }
                SkillDefinition skill = skillParser.parse(skillFile);
                if (!isSafeSkill(skill, skillFile)) {
                    continue;
                }
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

    private boolean isSafeSkill(SkillDefinition skill, Path skillFile) {
        String injectedContent = String.join("\n", skill.name(), skill.description(), skill.prompt());
        if (injectedContent.length() > maxSkillContentChars) {
            logger.warn("Skill content exceeds maximum length ({} > {}), skipping: {}",
                injectedContent.length(), maxSkillContentChars, skillFile);
            return false;
        }
        if (CustomInstructionSafetyValidator.containsSuspiciousPattern(injectedContent)) {
            logger.warn("Skill contains suspicious prompt patterns, skipping: {}", skillFile);
            return false;
        }
        return true;
    }

    private boolean isAgentFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return filename.endsWith(".agent.md");
    }

    public List<String> listAvailableAgents() throws IOException {
        Set<String> agentNames = new TreeSet<>();
        for (AgentSourceDirectory directory : agentDirectories) {
            if (!Files.exists(directory.path())) continue;
            List<Path> files = listAgentFiles(directory.path());
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

    List<AgentSourceDirectory> getAgentDirectories() {
        return agentDirectories;
    }
}
