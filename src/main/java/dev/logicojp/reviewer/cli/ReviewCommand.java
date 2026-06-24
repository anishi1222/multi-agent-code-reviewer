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
import java.util.Optional;

/// Main review command that executes the multi-agent code review.
@Singleton
public class ReviewCommand implements CliCommand {

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

    @Override
    public String name() {
        return "run";
    }

    @Override
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

    private Optional<ReviewOptions> parseArgs(String[] args) {
        return optionsParser.parse(args);
    }

    private int executeInternal(ReviewOptions options) {
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

    private ReviewTargetResolver.TargetAndToken resolveTargetAndToken(ReviewOptions options) {
        return targetResolver.resolve(options.target(), options.githubToken());
    }

    private ReviewAgentConfigResolver.AgentResolution resolveAgentConfigs(ReviewOptions options) {
        return agentConfigResolver.resolve(options);
    }

    private ReviewPreparationService.PreparedData prepareReviewData(
            ReviewOptions options,
            ReviewTarget target,
            ModelConfig modelConfig,
            Map<String, AgentConfig> agentConfigs,
            List<Path> agentDirs) {
        return preparationService.prepare(options, target, modelConfig, agentConfigs, agentDirs);
    }

    private ModelConfig resolveModelConfig(ReviewOptions options) {
        return modelConfigResolver.resolve(
            defaultModelConfig,
            options.defaultModel(),
            options.reviewModel(),
            options.reportModel(),
            options.summaryModel()
        );
    }

    private ReviewRunExecutor.ReviewRunRequest createRunRequest(
            ReviewOptions options,
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
