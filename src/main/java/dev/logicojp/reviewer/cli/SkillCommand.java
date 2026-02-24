package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.service.AgentService;
import dev.logicojp.reviewer.service.CopilotService;
import dev.logicojp.reviewer.service.SkillService;
import dev.logicojp.reviewer.skill.SkillDefinition;
import dev.logicojp.reviewer.skill.SkillExecutor;
import dev.logicojp.reviewer.util.GitHubTokenResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// Skill command that executes individual agent skills.
///
/// Consolidates option parsing, skill preparation, execution coordination,
/// and output formatting into a single command class.
@Singleton
public class SkillCommand {

    private static final Logger logger = LoggerFactory.getLogger(SkillCommand.class);

    private static final String USAGE = """
            Usage: review skill [skill-id] [options]

            Options:
                -p, --param <key=value>     Skill parameters (repeatable or comma-separated)
                --token -                   Read GitHub token from stdin (default: GITHUB_TOKEN env var)
                --model <model>             Model for skill execution
                --agents-dir <path...>      Additional agent definition directories
                --list                      List available skills
            """;

    private final SkillService skillService;
    private final AgentService agentService;
    private final CopilotService copilotService;
    private final ExecutionConfig executionConfig;
    private final GitHubTokenResolver tokenResolver;
    private final CliOutput output;

    /// Parsed CLI options for the skill command.
    record ParsedOptions(
        String skillId,
        List<String> paramStrings,
        String githubToken,
        String model,
        List<Path> additionalAgentDirs,
        boolean listSkills
    ) {
        ParsedOptions {
            paramStrings = paramStrings != null ? List.copyOf(paramStrings) : List.of();
            additionalAgentDirs = additionalAgentDirs != null ? List.copyOf(additionalAgentDirs) : List.of();
        }
    }

    @Inject
    public SkillCommand(SkillService skillService,
                        AgentService agentService,
                        CopilotService copilotService,
                        ExecutionConfig executionConfig,
                        GitHubTokenResolver tokenResolver,
                        CliOutput output) {
        this.skillService = skillService;
        this.agentService = agentService;
        this.copilotService = copilotService;
        this.executionConfig = executionConfig;
        this.tokenResolver = tokenResolver;
        this.output = output;
    }

    public int execute(String[] args) {
        try {
            Optional<ParsedOptions> parsed = parseArgs(args);
            if (parsed.isEmpty()) return ExitCodes.OK;
            return executeInternal(parsed.get());
        } catch (CliValidationException e) {
            if (e.getMessage() != null && !e.getMessage().isBlank()) output.errorln(e.getMessage());
            if (e.showUsage()) output.out().print(USAGE);
            return ExitCodes.USAGE;
        } catch (Exception e) {
            logger.error("Execution failed: {}", e.getMessage(), e);
            output.errorln("Error: " + e.getMessage());
            return ExitCodes.SOFTWARE;
        }
    }

    // ── Option Parsing ──────────────────────────────────────────────────

    private Optional<ParsedOptions> parseArgs(String[] args) {
        args = Objects.requireNonNullElse(args, new String[0]);
        var state = new ParseState();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> {
                    output.out().print(USAGE);
                    return Optional.empty();
                }
                case "-p", "--param" -> {
                    var value = CliParsing.readSingleValue(arg, args, i, "--param");
                    state.paramStrings.addAll(CliParsing.splitComma(value.value()));
                    i = value.newIndex();
                }
                case "--token" -> i = CliParsing.readTokenInto(args, i, "--token", v -> state.githubToken = v);
                case "--model" -> i = CliParsing.readInto(args, i, "--model", v -> state.model = v);
                case "--agents-dir" -> i = CliParsing.readMultiInto(args, i, "--agents-dir",
                    v -> state.additionalAgentDirs.add(Path.of(v)));
                case "--list" -> state.listSkills = true;
                default -> {
                    if (arg.startsWith("-")) {
                        throw new CliValidationException("Unknown option: " + arg, true);
                    }
                    if (state.skillId == null) {
                        state.skillId = arg;
                    } else {
                        throw new CliValidationException("Unexpected argument: " + arg, true);
                    }
                }
            }
        }

        return Optional.of(new ParsedOptions(
            state.skillId, List.copyOf(state.paramStrings), state.githubToken, state.model,
            List.copyOf(state.additionalAgentDirs), state.listSkills));
    }

    private static final class ParseState {
        String skillId;
        final List<String> paramStrings = new ArrayList<>();
        String githubToken;
        String model = ModelConfig.DEFAULT_MODEL;
        final List<Path> additionalAgentDirs = new ArrayList<>();
        boolean listSkills;
    }

    // ── Execution Logic ─────────────────────────────────────────────────

    private int executeInternal(ParsedOptions options) {
        loadAndRegisterAgentSkills(options.additionalAgentDirs());

        if (options.listSkills()) {
            printAvailableSkills();
            return ExitCodes.OK;
        }

        return executeSkill(options);
    }

    private void loadAndRegisterAgentSkills(List<Path> additionalAgentDirs) {
        try {
            List<Path> agentDirs = agentService.buildAgentDirectories(additionalAgentDirs);
            Map<String, AgentConfig> agents = agentService.loadAllAgents(agentDirs);
            for (AgentConfig config : agents.values()) {
                skillService.getRegistry().registerAll(config.skills());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load agents", e);
        }
    }

    private int executeSkill(ParsedOptions options) {
        String skillId = requireSkillId(options.skillId());
        String resolvedToken = resolveRequiredToken(options.githubToken());
        ensureSkillExists(skillId);
        Map<String, String> parameters = parseParameters(options.paramStrings());

        try {
            copilotService.initializeOrThrow(resolvedToken);
            return runSkill(skillId, parameters, resolvedToken, options.model());
        } finally {
            copilotService.shutdown();
        }
    }

    private int runSkill(String skillId, Map<String, String> parameters,
                         String resolvedToken, String model) {
        output.println("Executing skill: " + skillId);
        output.println("Parameters: " + parameters.keySet());

        SkillExecutor.Result result = skillService.executeSkill(skillId, parameters, resolvedToken, model);
        if (result.success()) {
            output.println("=== Skill Result ===\n");
            output.println(result.content());
            return ExitCodes.OK;
        }
        output.errorln("Skill execution failed: " + result.errorMessage());
        return ExitCodes.SOFTWARE;
    }

    // ── Validation ──────────────────────────────────────────────────────

    private String requireSkillId(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            throw new CliValidationException("Skill ID required. Use --list to see available skills.", true);
        }
        return skillId;
    }

    private String resolveRequiredToken(String githubToken) {
        String resolved = tokenResolver.resolve(githubToken).orElse(null);
        if (resolved == null || resolved.isBlank()) {
            throw new CliValidationException(
                "GitHub token is required. Set GITHUB_TOKEN, use --token, or login with `gh auth login`.",
                true);
        }
        return resolved;
    }

    private void ensureSkillExists(String skillId) {
        if (skillService.getSkill(skillId).isEmpty()) {
            throw new CliValidationException("Skill not found: " + skillId, true);
        }
    }

    // ── Parameter Parsing ───────────────────────────────────────────────

    static Map<String, String> parseParameters(List<String> paramStrings) {
        Map<String, String> params = new HashMap<>();
        if (paramStrings != null) {
            for (String paramStr : paramStrings) {
                int eqIdx = paramStr.indexOf('=');
                if (eqIdx > 0) {
                    params.put(paramStr.substring(0, eqIdx).trim(), paramStr.substring(eqIdx + 1).trim());
                } else {
                    throw new CliValidationException(
                        "Invalid parameter format: '" + paramStr + "'. Expected 'key=value'.", true);
                }
            }
        }
        return Map.copyOf(params);
    }

    // ── Output Formatting ───────────────────────────────────────────────

    private void printAvailableSkills() {
        var skills = skillService.getRegistry().getAll();
        output.println("Available Skills:\n");
        if (skills.isEmpty()) {
            output.println("  No skills found.");
            return;
        }
        for (SkillDefinition skill : skills) {
            output.println("  " + skill.id());
            output.println("    Name: " + skill.name());
            output.println("    Description: " + skill.description());
            if (!skill.parameters().isEmpty()) {
                output.println("    Parameters:");
                for (var param : skill.parameters()) {
                    String required = param.required() ? " (required)" : "";
                    output.println("      - " + param.name() + ": " + param.description() + required);
                }
            }
            output.println("");
        }
    }
}
