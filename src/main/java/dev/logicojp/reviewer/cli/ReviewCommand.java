package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.instruction.CustomInstructionLoader;
import dev.logicojp.reviewer.instruction.CustomInstructionSafetyValidator;
import dev.logicojp.reviewer.service.AgentService;
import dev.logicojp.reviewer.service.CopilotService;
import dev.logicojp.reviewer.service.TemplateService;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.GitHubTokenResolver;
import dev.logicojp.reviewer.util.SecurityAuditLogger;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// Main review command that executes the multi-agent code review pipeline.
///
/// Pipeline: parse options → resolve target & token → resolve model config →
/// load agents → resolve custom instructions → create orchestrator →
/// execute reviews → generate reports → generate summary → shutdown.
@Singleton
public class ReviewCommand {

    private static final Logger logger = LoggerFactory.getLogger(ReviewCommand.class);

    private static final DateTimeFormatter OUTPUT_TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    private static final String USAGE = """
            Usage: review run [options]

            Target options (required):
                -r, --repo <owner/repo>     Target GitHub repository
                -l, --local <path>          Target local directory

            Agent options (required):
                --all                       Run all available agents
                -a, --agents <a,b,c>        Comma-separated agent names

            Other options:
                -o, --output <path>         Output directory (default: ./reports)
                --agents-dir <path...>      Additional agent definition directories
                --token -                   Read GitHub token from stdin (default: GITHUB_TOKEN env var)
                --parallelism <n>           Number of agents to run in parallel
                --no-summary                Skip executive summary generation
                --review-model <model>      Model for review stage
                --report-model <model>      Model for report stage
                --summary-model <model>     Model for summary stage
                --model <model>             Default model for all stages
                --instructions <path...>    Custom instruction files (Markdown)
                --no-instructions           Disable automatic instructions
                --no-prompts                Disable loading .github/prompts/*.prompt.md
            """;

    private final ModelConfig defaultModelConfig;
    private final ExecutionConfig executionConfig;
    private final CopilotService copilotService;
    private final AgentService agentService;
    private final ReviewPipelineExecutor pipelineExecutor;
    private final CustomInstructionLoader instructionLoader;
    private final GitHubTokenResolver tokenResolver;
    private final TemplateService templateService;
    private final CliOutput output;
    private final Clock clock;

    /// Target selection — sealed interface for type-safe exclusive choice.
    sealed interface TargetSelection {
        record Repository(String repository) implements TargetSelection {}
        record LocalDirectory(Path directory) implements TargetSelection {}
    }

    /// Agent selection — sealed interface for type-safe exclusive choice.
    sealed interface AgentSelection {
        record All() implements AgentSelection {}
        record Named(List<String> agents) implements AgentSelection {}
    }

    /// Parsed CLI options for a review run.
    record ParsedOptions(
        TargetSelection target,
        AgentSelection agents,
        Path outputDirectory,
        List<Path> additionalAgentDirs,
        String githubToken,
        int parallelism,
        boolean noSummary,
        String reviewModel,
        String reportModel,
        String summaryModel,
        String defaultModel,
        List<Path> instructionPaths,
        boolean noInstructions,
        boolean noPrompts,
        boolean trustTarget
    ) {
        ParsedOptions {
            additionalAgentDirs = additionalAgentDirs != null ? List.copyOf(additionalAgentDirs) : List.of();
            instructionPaths = instructionPaths != null ? List.copyOf(instructionPaths) : List.of();
            outputDirectory = outputDirectory != null ? outputDirectory : Path.of("./reports");
            parallelism = parallelism > 0 ? parallelism : 1;
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(agents, "agents must not be null");
        }
    }

    @Inject
    public ReviewCommand(ModelConfig defaultModelConfig,
                         ExecutionConfig executionConfig,
                         CopilotService copilotService,
                         AgentService agentService,
                        ReviewPipelineExecutor pipelineExecutor,
                         CustomInstructionLoader instructionLoader,
                         GitHubTokenResolver tokenResolver,
                         TemplateService templateService,
                         CliOutput output) {
        this(defaultModelConfig, executionConfig, copilotService, agentService,
               pipelineExecutor, instructionLoader, tokenResolver,
             templateService, output, Clock.systemDefaultZone());
    }

    ReviewCommand(ModelConfig defaultModelConfig,
                  ExecutionConfig executionConfig,
                  CopilotService copilotService,
                  AgentService agentService,
                   ReviewPipelineExecutor pipelineExecutor,
                  CustomInstructionLoader instructionLoader,
                  GitHubTokenResolver tokenResolver,
                  TemplateService templateService,
                  CliOutput output,
                  Clock clock) {
        this.defaultModelConfig = defaultModelConfig;
        this.executionConfig = executionConfig;
        this.copilotService = copilotService;
        this.agentService = agentService;
        this.pipelineExecutor = pipelineExecutor;
        this.instructionLoader = instructionLoader;
        this.tokenResolver = tokenResolver;
        this.templateService = templateService;
        this.output = output;
        this.clock = clock;
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
        var state = new ParseState(executionConfig.parallelism());

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            i = applyOption(state, arg, args, i);
            if (state.helpRequested) return Optional.empty();
        }

        return Optional.of(buildParsedOptions(state));
    }

    private static final class ParseState {
        String repository;
        Path localDirectory;
        boolean allAgents;
        final List<String> agentNames = new ArrayList<>();
        Path outputDirectory = Path.of("./reports");
        final List<Path> additionalAgentDirs = new ArrayList<>();
        String githubToken;
        int parallelism;
        boolean noSummary;
        String reviewModel;
        String reportModel;
        String summaryModel;
        String defaultModel;
        final List<Path> instructionPaths = new ArrayList<>();
        boolean noInstructions;
        boolean noPrompts;
        boolean trustTarget;
        boolean helpRequested;

        ParseState(int defaultParallelism) {
            this.parallelism = defaultParallelism;
        }
    }

    private int applyOption(ParseState state, String arg, String[] args, int i) {
        return switch (arg) {
            case "-h", "--help" -> { state.helpRequested = true; output.out().print(USAGE); yield i; }
            case "-r", "--repo" -> CliParsing.readInto(args, i, "--repo", v -> state.repository = v);
            case "-l", "--local" -> CliParsing.readInto(args, i, "--local", v -> state.localDirectory = Path.of(v));
            case "--all" -> { state.allAgents = true; yield i; }
            case "-a", "--agents" -> {
                var value = CliParsing.readSingleValue(arg, args, i, "--agents");
                List<String> parsed = CliParsing.splitComma(value.value());
                if (parsed.isEmpty()) {
                    throw new CliValidationException("--agents requires at least one value", true);
                }
                state.agentNames.addAll(parsed);
                yield value.newIndex();
            }
            case "-o", "--output" -> CliParsing.readInto(args, i, "--output", v -> state.outputDirectory = Path.of(v));
            case "--agents-dir" -> CliParsing.readMultiInto(args, i, "--agents-dir",
                v -> state.additionalAgentDirs.add(Path.of(v)));
            case "--token" -> CliParsing.readTokenInto(args, i, "--token", v -> state.githubToken = v);
            case "--parallelism" -> CliParsing.readInto(args, i, "--parallelism",
                v -> state.parallelism = parseInt(v, "--parallelism"));
            case "--no-summary" -> { state.noSummary = true; yield i; }
            case "--review-model" -> CliParsing.readInto(args, i, "--review-model", v -> state.reviewModel = v);
            case "--report-model" -> CliParsing.readInto(args, i, "--report-model", v -> state.reportModel = v);
            case "--summary-model" -> CliParsing.readInto(args, i, "--summary-model", v -> state.summaryModel = v);
            case "--model" -> CliParsing.readInto(args, i, "--model", v -> state.defaultModel = v);
            case "--instructions" -> CliParsing.readMultiInto(args, i, "--instructions",
                v -> state.instructionPaths.add(Path.of(v)));
            case "--no-instructions" -> { state.noInstructions = true; yield i; }
            case "--no-prompts" -> { state.noPrompts = true; yield i; }
            case "--trust" -> { state.trustTarget = true; yield i; }
            default -> {
                if (arg.startsWith("-")) {
                    throw new CliValidationException("Unknown option: " + arg, true);
                }
                throw new CliValidationException("Unexpected argument: " + arg, true);
            }
        };
    }

    private ParsedOptions buildParsedOptions(ParseState state) {
        var target = resolveTargetSelection(state.repository, state.localDirectory);
        var agents = resolveAgentSelection(state.allAgents, state.agentNames);
        return new ParsedOptions(
            target, agents, state.outputDirectory, state.additionalAgentDirs,
            state.githubToken, state.parallelism, state.noSummary,
            state.reviewModel, state.reportModel, state.summaryModel, state.defaultModel,
            state.instructionPaths, state.noInstructions, state.noPrompts, state.trustTarget);
    }

    private static TargetSelection resolveTargetSelection(String repository, Path localDirectory) {
        boolean hasRepo = repository != null && !repository.isBlank();
        boolean hasLocal = localDirectory != null;
        if (!hasRepo && !hasLocal) {
            throw new CliValidationException("Either --repo or --local must be specified.", true);
        }
        if (hasRepo && hasLocal) {
            throw new CliValidationException("Specify either --repo or --local (not both).", true);
        }
        return hasRepo
            ? new TargetSelection.Repository(repository)
            : new TargetSelection.LocalDirectory(localDirectory);
    }

    private static AgentSelection resolveAgentSelection(boolean allAgents, List<String> agentNames) {
        boolean hasAgents = !agentNames.isEmpty();
        if (!allAgents && !hasAgents) {
            throw new CliValidationException("Either --all or --agents must be specified.", true);
        }
        if (allAgents && hasAgents) {
            throw new CliValidationException("Specify either --all or --agents (not both).", true);
        }
        return allAgents
            ? new AgentSelection.All()
            : new AgentSelection.Named(List.copyOf(agentNames));
    }

    // ── Main Execution ──────────────────────────────────────────────────

    private int executeInternal(ParsedOptions options) {
        // 1. Resolve target & token
        ReviewTarget target = resolveTarget(options.target());
        String resolvedToken = resolveToken(options.target(), options.githubToken());
        logReviewAuditEvent(target, options.trustTarget(), resolvedToken != null && !resolvedToken.isBlank());

        // 2. Resolve model config
        ModelConfig modelConfig = resolveModelConfig(options);

        // 3. Load agents
        List<Path> agentDirs = agentService.buildAgentDirectories(options.additionalAgentDirs());
        Map<String, AgentConfig> agentConfigs = loadAgentConfigs(options.agents(), agentDirs);
        agentConfigs = applyReviewModelOverride(agentConfigs, options.reviewModel());
        if (agentConfigs.isEmpty()) {
            printNoAgentsError(agentDirs);
            return ExitCodes.SOFTWARE;
        }

        // 4. Resolve output directory
        Path outputDirectory = resolveOutputDirectory(options, target);

        // 5. Resolve custom instructions
        List<CustomInstruction> customInstructions = resolveCustomInstructions(options, target);

        // 6. Print banner
        printBanner(agentConfigs, agentDirs, modelConfig, target, outputDirectory, options.reviewModel());

        // 7. Load output constraints template
        String outputConstraints = loadOutputConstraints();

        // 8. Initialize Copilot, execute, shutdown
        try {
            copilotService.initializeOrThrow(resolvedToken);
            return pipelineExecutor.execute(
                target, modelConfig, agentConfigs, customInstructions,
                outputDirectory, outputConstraints,
                options.parallelism(), options.noSummary(), resolvedToken);
        } finally {
            copilotService.shutdown();
        }
    }

    // ── Target & Token Resolution ───────────────────────────────────────

    private ReviewTarget resolveTarget(TargetSelection selection) {
        return switch (selection) {
            case TargetSelection.Repository(String repository) -> ReviewTarget.gitHub(repository);
            case TargetSelection.LocalDirectory(Path dir) -> {
                Path localPath = dir.toAbsolutePath();
                if (!Files.exists(localPath)) {
                    throw new CliValidationException("Local directory does not exist: " + localPath, true);
                }
                if (!Files.isDirectory(localPath)) {
                    throw new CliValidationException("Path is not a directory: " + localPath, true);
                }
                yield ReviewTarget.local(localPath);
            }
        };
    }

    private @Nullable String resolveToken(TargetSelection selection, @Nullable String githubToken) {
        return switch (selection) {
            case TargetSelection.Repository _ -> {
                String resolved = tokenResolver.resolve(githubToken).orElse(null);
                if (resolved == null || resolved.isBlank()) {
                    throw new CliValidationException(
                        "GitHub token is required for repository review. "
                            + "Set GITHUB_TOKEN, use --token, or login with `gh auth login`.", true);
                }
                yield resolved;
            }
            case TargetSelection.LocalDirectory _ -> null;
        };
    }

    // ── Model Config Resolution ─────────────────────────────────────────

    private ModelConfig resolveModelConfig(ParsedOptions options) {
        ModelConfig base = defaultModelConfig != null ? defaultModelConfig : new ModelConfig();
        var builder = ModelConfig.builder()
            .reviewModel(base.reviewModel())
            .reportModel(base.reportModel())
            .summaryModel(base.summaryModel())
            .reasoningEffort(base.reasoningEffort());

        if (options.defaultModel() != null) builder.allModels(options.defaultModel());
        if (options.reviewModel() != null) builder.reviewModel(options.reviewModel());
        if (options.reportModel() != null) builder.reportModel(options.reportModel());
        if (options.summaryModel() != null) builder.summaryModel(options.summaryModel());

        return builder.build();
    }

    // ── Agent Resolution ────────────────────────────────────────────────

    private Map<String, AgentConfig> loadAgentConfigs(AgentSelection selection, List<Path> agentDirs) {
        try {
            return switch (selection) {
                case AgentSelection.All() -> agentService.loadAllAgents(agentDirs);
                case AgentSelection.Named(List<String> names) -> agentService.loadAgents(agentDirs, names);
            };
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load agent configurations", e);
        }
    }

    private Map<String, AgentConfig> applyReviewModelOverride(Map<String, AgentConfig> configs, String reviewModel) {
        if (reviewModel == null) return configs;
        Map<String, AgentConfig> adjusted = new LinkedHashMap<>();
        for (var entry : configs.entrySet()) {
            adjusted.put(entry.getKey(), entry.getValue().withModel(reviewModel));
        }
        return adjusted;
    }

    // ── Custom Instruction Resolution ───────────────────────────────────

    private List<CustomInstruction> resolveCustomInstructions(ParsedOptions options, ReviewTarget target) {
        if (options.noInstructions()) {
            logger.info("Custom instructions disabled by --no-instructions flag");
            return List.of();
        }

        List<CustomInstruction> instructions = new ArrayList<>();
        loadExplicitInstructions(options.instructionPaths(), instructions);
        loadTargetInstructions(target, options, instructions);
        return List.copyOf(instructions);
    }

    private void loadExplicitInstructions(List<Path> paths, List<CustomInstruction> instructions) {
        if (paths == null || paths.isEmpty()) return;
        for (Path path : paths) {
            loadInstructionFromPath(path).ifPresent(instruction ->
                addIfSafe(instruction, instructions, "  ✓ Loaded instructions: ", true));
        }
    }

    private Optional<CustomInstruction> loadInstructionFromPath(Path path) {
        try {
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                logger.warn("Instruction file not found: {}", path);
                return Optional.empty();
            }
            String content = Files.readString(path);
            if (content.isBlank()) return Optional.empty();
            return Optional.of(new CustomInstruction(
                path.toString(), content.trim(), CustomInstruction.Source.LOCAL_FILE, null, null));
        } catch (IOException | SecurityException e) {
            logger.warn("Failed to read instruction file {}: {}", path, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private void loadTargetInstructions(ReviewTarget target, ParsedOptions options,
                                        List<CustomInstruction> instructions) {
        if (!target.isLocal()) return;
        if (!options.trustTarget()) {
            output.println("ℹ  Target instructions skipped (use --trust to load from review target).");
            return;
        }

        output.println("⚠  --trust enabled: loading custom instructions from the review target.");
        logger.warn("[SECURITY AUDIT] Trust boundary relaxed: loading instructions from target={}",
            target.displayName());
        SecurityAuditLogger.log("trust-boundary", "instruction-load",
            "Trust mode enabled for target instruction loading",
            Map.of("target", target.displayName()));

        CustomInstructionLoader targetLoader = options.noPrompts()
            ? CustomInstructionLoader.withSettings(null, false)
            : instructionLoader;
        List<CustomInstruction> targetInstructions = targetLoader.loadForTarget(target);

        for (CustomInstruction instruction : targetInstructions) {
            String sourcePath = instruction.sourcePath() != null ? instruction.sourcePath() : "unknown";
            logger.warn("[SECURITY AUDIT] Loaded trusted instruction from: {} (size: {} bytes)",
                sourcePath, instruction.content() != null ? instruction.content().length() : 0);
            SecurityAuditLogger.log("trust-boundary", "instruction-load",
                "Trusted instruction loaded",
                Map.of("source", sourcePath,
                       "size", Integer.toString(instruction.content() != null ? instruction.content().length() : 0)));
            addIfSafe(instruction, instructions, "  ✓ Loaded instructions from target: ", false);
        }
    }

    private void addIfSafe(CustomInstruction instruction, List<CustomInstruction> instructions,
                           String loadedPrefix, boolean trusted) {
        var validation = CustomInstructionSafetyValidator.validate(instruction, trusted);
        if (!validation.safe()) {
            String sourcePath = instruction.sourcePath() != null ? instruction.sourcePath() : "unknown";
            logger.warn("Skipped unsafe instruction {}: {}", sourcePath, validation.reason());
            SecurityAuditLogger.log("instruction-validation", "instruction-rejected",
                "Unsafe instruction rejected",
                Map.of("source", sourcePath, "reason", validation.reason(),
                       "trusted", Boolean.toString(trusted)));
            return;
        }
        instructions.add(instruction);
        output.println(loadedPrefix + instruction.sourcePath());
    }

    // ── Output Directory ────────────────────────────────────────────────

    private Path resolveOutputDirectory(ParsedOptions options, ReviewTarget target) {
        String invocationTimestamp = LocalDateTime.now(clock).format(OUTPUT_TIMESTAMP_FORMATTER);
        return options.outputDirectory()
            .resolve(target.repositorySubPath())
            .resolve(invocationTimestamp);
    }

    // ── Template Loading ────────────────────────────────────────────────

    private String loadOutputConstraints() {
        try {
            return templateService.loadTemplateContent("output-constraints.md");
        } catch (IllegalStateException | IllegalArgumentException e) {
            logger.debug("Output constraints template unavailable: {}", e.getMessage());
            return null;
        }
    }

    // ── Banner & Summary Output ─────────────────────────────────────────

    private void printBanner(Map<String, AgentConfig> agentConfigs, List<Path> agentDirs,
                             ModelConfig modelConfig, ReviewTarget target,
                             Path outputDirectory, String reviewModel) {
        output.println("╔════════════════════════════════════════════════════════════╗");
        output.println("║           Multi-Agent Code Reviewer                        ║");
        output.println("╚════════════════════════════════════════════════════════════╝");
        output.println("");
        output.println("Target: " + target.displayName() + (target.isLocal() ? " (local)" : " (GitHub)"));
        output.println("Agents: " + agentConfigs.keySet());
        output.println("Output: " + outputDirectory.toAbsolutePath());
        output.println("");
        output.println("Agent directories:");
        for (Path dir : agentDirs) {
            output.println("  - " + dir + (Files.exists(dir) ? "" : " (not found)"));
        }
        output.println("");
        output.println("Models:");
        output.println("  Review: " + (reviewModel != null ? reviewModel : "(agent default)"));
        output.println("  Summary: " + modelConfig.summaryModel());
        if (executionConfig.reviewPasses() > 1) {
            output.println("Review passes: " + executionConfig.reviewPasses() + " per agent");
        }
        output.println("");
    }

    private void printNoAgentsError(List<Path> agentDirs) {
        output.errorln("Error: No agents found. Check the agents directories:");
        for (Path dir : agentDirs) {
            output.errorln("  - " + dir);
        }
    }

    // ── Audit Logging ───────────────────────────────────────────────────

    private void logReviewAuditEvent(ReviewTarget target, boolean trustMode, boolean hasToken) {
        SecurityAuditLogger.log("access", "review.start", "Review access initiated",
            Map.of(
                "targetType", target.isLocal() ? "local" : "github",
                "target", target.displayName(),
                "trustMode", Boolean.toString(trustMode),
                "tokenSource", hasToken ? "provided-or-resolved" : "not-required"));
    }

    // ── Utility ─────────────────────────────────────────────────────────

    private static int parseInt(String value, String optionName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new CliValidationException("Invalid value for " + optionName + ": " + value, true);
        }
    }
}
