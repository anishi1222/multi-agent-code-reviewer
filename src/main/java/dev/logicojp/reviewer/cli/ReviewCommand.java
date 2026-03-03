package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.SecurityAuditLogger;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// Main review command that executes the multi-agent code review.
@Singleton
public class ReviewCommand {

    private static final Logger logger = LoggerFactory.getLogger(ReviewCommand.class);

    private final ModelConfig defaultModelConfig;

    private final ReviewModelConfigResolver modelConfigResolver;

    private final ReviewOptionsParser optionsParser;

    private final ReviewTargetResolver targetResolver;

    private final ReviewAgentConfigResolver agentConfigResolver;

    private final ReviewPreparationService preparationService;

    private final ReviewRunRequestFactory runRequestFactory;

    private final ReviewExecutionCoordinator executionCoordinator;

    private final CliOutput output;

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
        OutputOptions output,
        ModelOptions models,
        InstructionOptions instructions,
        String githubToken,
        boolean trustTarget
    ) {
        record OutputOptions(
            Path outputDirectory,
            List<Path> additionalAgentDirs,
            int parallelism,
            boolean noSummary
        ) {
            OutputOptions {
                outputDirectory = outputDirectory != null ? outputDirectory : Path.of("./reports");
                additionalAgentDirs = additionalAgentDirs != null ? List.copyOf(additionalAgentDirs) : List.of();
                parallelism = parallelism > 0 ? parallelism : 1;
            }
        }

        record ModelOptions(
            String reviewModel,
            String reportModel,
            String summaryModel,
            String defaultModel
        ) {
        }

        record InstructionOptions(
            List<Path> instructionPaths,
            boolean noInstructions,
            boolean noPrompts
        ) {
            InstructionOptions {
                instructionPaths = instructionPaths != null ? List.copyOf(instructionPaths) : List.of();
            }
        }

        ParsedOptions {
            output = output != null ? output : new OutputOptions(Path.of("./reports"), List.of(), 1, false);
            models = models != null ? models : new ModelOptions(null, null, null, null);
            instructions = instructions != null ? instructions : new InstructionOptions(List.of(), false, false);
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(agents, "agents must not be null");
        }

        ParsedOptions(
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
            this(
                target,
                agents,
                new OutputOptions(outputDirectory, additionalAgentDirs, parallelism, noSummary),
                new ModelOptions(reviewModel, reportModel, summaryModel, defaultModel),
                new InstructionOptions(instructionPaths, noInstructions, noPrompts),
                githubToken,
                trustTarget
            );
        }

        public Path outputDirectory() {
            return output.outputDirectory();
        }

        public List<Path> additionalAgentDirs() {
            return output.additionalAgentDirs();
        }

        public int parallelism() {
            return output.parallelism();
        }

        public boolean noSummary() {
            return output.noSummary();
        }

        public String reviewModel() {
            return models.reviewModel();
        }

        public String reportModel() {
            return models.reportModel();
        }

        public String summaryModel() {
            return models.summaryModel();
        }

        public String defaultModel() {
            return models.defaultModel();
        }

        public List<Path> instructionPaths() {
            return instructions.instructionPaths();
        }

        public boolean noInstructions() {
            return instructions.noInstructions();
        }

        public boolean noPrompts() {
            return instructions.noPrompts();
        }

        static Builder builder() {
            return new Builder();
        }

        static final class Builder {
            private TargetSelection target;
            private AgentSelection agents;
            private Path outputDirectory = Path.of("./reports");
            private List<Path> additionalAgentDirs = List.of();
            private int parallelism = 1;
            private boolean noSummary;
            private String reviewModel;
            private String reportModel;
            private String summaryModel;
            private String defaultModel;
            private List<Path> instructionPaths = List.of();
            private boolean noInstructions;
            private boolean noPrompts;
            private String githubToken;
            private boolean trustTarget;

            Builder target(TargetSelection target) {
                this.target = target;
                return this;
            }

            Builder agents(AgentSelection agents) {
                this.agents = agents;
                return this;
            }

            Builder outputDirectory(Path outputDirectory) {
                this.outputDirectory = outputDirectory;
                return this;
            }

            Builder additionalAgentDirs(List<Path> additionalAgentDirs) {
                this.additionalAgentDirs = additionalAgentDirs;
                return this;
            }

            Builder githubToken(String githubToken) {
                this.githubToken = githubToken;
                return this;
            }

            Builder parallelism(int parallelism) {
                this.parallelism = parallelism;
                return this;
            }

            Builder noSummary(boolean noSummary) {
                this.noSummary = noSummary;
                return this;
            }

            Builder reviewModel(String reviewModel) {
                this.reviewModel = reviewModel;
                return this;
            }

            Builder reportModel(String reportModel) {
                this.reportModel = reportModel;
                return this;
            }

            Builder summaryModel(String summaryModel) {
                this.summaryModel = summaryModel;
                return this;
            }

            Builder defaultModel(String defaultModel) {
                this.defaultModel = defaultModel;
                return this;
            }

            Builder instructionPaths(List<Path> instructionPaths) {
                this.instructionPaths = instructionPaths;
                return this;
            }

            Builder noInstructions(boolean noInstructions) {
                this.noInstructions = noInstructions;
                return this;
            }

            Builder noPrompts(boolean noPrompts) {
                this.noPrompts = noPrompts;
                return this;
            }

            Builder trustTarget(boolean trustTarget) {
                this.trustTarget = trustTarget;
                return this;
            }

            ParsedOptions build() {
                return new ParsedOptions(
                    target,
                    agents,
                    new OutputOptions(outputDirectory, additionalAgentDirs, parallelism, noSummary),
                    new ModelOptions(reviewModel, reportModel, summaryModel, defaultModel),
                    new InstructionOptions(instructionPaths, noInstructions, noPrompts),
                    githubToken,
                    trustTarget
                );
            }
        }
    }

    @Inject
    public ReviewCommand(
        ModelConfig defaultModelConfig,
        ReviewModelConfigResolver modelConfigResolver,
        ReviewOptionsParser optionsParser,
        ReviewTargetResolver targetResolver,
        ReviewAgentConfigResolver agentConfigResolver,
        ReviewPreparationService preparationService,
        ReviewRunRequestFactory runRequestFactory,
        ReviewExecutionCoordinator executionCoordinator,
        CliOutput output
    ) {
        this.defaultModelConfig = defaultModelConfig;
        this.modelConfigResolver = modelConfigResolver;
        this.optionsParser = optionsParser;
        this.targetResolver = targetResolver;
        this.agentConfigResolver = agentConfigResolver;
        this.preparationService = preparationService;
        this.runRequestFactory = runRequestFactory;
        this.executionCoordinator = executionCoordinator;
        this.output = output;
    }

    public int execute(String[] args) {
        return CommandExecutor.execute(
            args,
            this::parseArgs,
            this::executeInternal,
            CliUsage::printRun,
            logger,
            output
        );
    }

    private Optional<ParsedOptions> parseArgs(String[] args) {
        return optionsParser.parse(args);
    }

    private int executeInternal(ParsedOptions options) {
        ReviewTargetResolver.TargetAndToken targetAndToken = resolveTargetAndToken(options);
        ReviewTarget target = targetAndToken.target();
        String resolvedToken = targetAndToken.resolvedToken();
        logReviewAuditEvent(target, options.trustTarget(), resolvedToken != null && !resolvedToken.isBlank());
        ModelConfig modelConfig = resolveModelConfig(options);
        ReviewAgentConfigResolver.AgentResolution agentResolution = resolveAgentConfigs(options);
        List<Path> agentDirs = agentResolution.agentDirectories();
        Map<String, AgentConfig> agentConfigs = agentResolution.agentConfigs();

        ReviewPreparationService.PreparedData prepared = prepareReviewData(
            options, target, modelConfig, agentConfigs, agentDirs);
        ReviewRunExecutor.ReviewRunRequest runRequest = createRunRequest(
            options,
            target,
            modelConfig,
            agentConfigs,
            prepared
        );

        return executeReview(agentConfigs, agentDirs, resolvedToken, runRequest);
    }

    private ReviewTargetResolver.TargetAndToken resolveTargetAndToken(ParsedOptions options) {
        return targetResolver.resolve(options.target(), options.githubToken());
    }

    private ReviewAgentConfigResolver.AgentResolution resolveAgentConfigs(ParsedOptions options) {
        return agentConfigResolver.resolve(options);
    }

    private ReviewPreparationService.PreparedData prepareReviewData(
            ParsedOptions options,
            ReviewTarget target,
            ModelConfig modelConfig,
            Map<String, AgentConfig> agentConfigs,
            List<Path> agentDirs) {
        return preparationService.prepare(options, target, modelConfig, agentConfigs, agentDirs);
    }

    private ModelConfig resolveModelConfig(ParsedOptions options) {
        return modelConfigResolver.resolve(
            defaultModelConfig,
            options.defaultModel(),
            options.reviewModel(),
            options.reportModel(),
            options.summaryModel()
        );
    }

    private ReviewRunExecutor.ReviewRunRequest createRunRequest(
            ParsedOptions options,
            ReviewTarget target,
            ModelConfig modelConfig,
            Map<String, AgentConfig> agentConfigs,
            ReviewPreparationService.PreparedData prepared) {
        return runRequestFactory.create(
            options,
            target,
            modelConfig,
            agentConfigs,
            prepared.customInstructions(),
            prepared.outputDirectory()
        );
    }

    private int executeReview(Map<String, AgentConfig> agentConfigs,
                              List<Path> agentDirs,
                              String resolvedToken,
                              ReviewRunExecutor.ReviewRunRequest runRequest) {
        return executionCoordinator.execute(agentConfigs, agentDirs, resolvedToken, runRequest);
    }

    private void logReviewAuditEvent(ReviewTarget target, boolean trustMode, boolean hasToken) {
        SecurityAuditLogger.log(
            "access",
            "review.start",
            "Review access initiated",
            Map.of(
                "targetType", target.isLocal() ? "local" : "github",
                "target", target.displayName(),
                "trustMode", Boolean.toString(trustMode),
                "tokenSource", hasToken ? "provided-or-resolved" : "not-required"
            )
        );
    }

}
