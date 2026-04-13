package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.ExecutionCorrelation;
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
        String githubToken,
        boolean trustTarget,
        RubberDuckOptions rubberDuckOptions
    ) {
        record OutputOptions(
            Path outputDirectory,
            List<Path> additionalAgentDirs,
            int parallelism,
            boolean noSummary,
            boolean noSharedSession
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

        record RubberDuckOptions(
            boolean enabled,
            int dialogueRounds,
            String peerModel
        ) {
        }

        ParsedOptions {
            output = output != null ? output : new OutputOptions(Path.of("./reports"), List.of(), 1, false, false);
            models = models != null ? models : new ModelOptions(null, null, null, null);
            rubberDuckOptions = rubberDuckOptions != null ? rubberDuckOptions : new RubberDuckOptions(false, 0, null);
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(agents, "agents must not be null");
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

        public boolean noSharedSession() {
            return output.noSharedSession();
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

        public boolean rubberDuck() {
            return rubberDuckOptions.enabled();
        }

        public int dialogueRounds() {
            return rubberDuckOptions.dialogueRounds();
        }

        public String peerModel() {
            return rubberDuckOptions.peerModel();
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
            private boolean noSharedSession;
            private String reviewModel;
            private String reportModel;
            private String summaryModel;
            private String defaultModel;
            private String githubToken;
            private boolean trustTarget;
            private boolean rubberDuck;
            private int dialogueRounds;
            private String peerModel;

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

            Builder noSharedSession(boolean noSharedSession) {
                this.noSharedSession = noSharedSession;
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

            Builder trustTarget(boolean trustTarget) {
                this.trustTarget = trustTarget;
                return this;
            }

            Builder rubberDuck(boolean rubberDuck) {
                this.rubberDuck = rubberDuck;
                return this;
            }

            Builder dialogueRounds(int dialogueRounds) {
                this.dialogueRounds = dialogueRounds;
                return this;
            }

            Builder peerModel(String peerModel) {
                this.peerModel = peerModel;
                return this;
            }

            ParsedOptions build() {
                return new ParsedOptions(
                    target,
                    agents,
                    new OutputOptions(outputDirectory, additionalAgentDirs, parallelism, noSummary, noSharedSession),
                    new ModelOptions(reviewModel, reportModel, summaryModel, defaultModel),
                    githubToken,
                    trustTarget,
                    new RubberDuckOptions(rubberDuck, dialogueRounds, peerModel)
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
        String executionId = ExecutionCorrelation.generateExecutionId();
        ExecutionCorrelation.putExecutionId(executionId);
        try {
            ReviewTargetResolver.TargetAndToken targetAndToken = resolveTargetAndToken(options);
            ReviewTarget target = targetAndToken.target();
            String resolvedToken = targetAndToken.resolvedToken();
            logger.info("Review execution started: executionId={}, target={}", executionId, target.displayName());
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
        } finally {
            ExecutionCorrelation.clearExecutionId();
        }
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
            prepared.outputDirectory(),
            prepared.invocationTimestamp()
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
