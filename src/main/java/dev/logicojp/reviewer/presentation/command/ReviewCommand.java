package dev.logicojp.reviewer.presentation.command;

import dev.logicojp.reviewer.application.port.inbound.ReviewRequest;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import dev.logicojp.reviewer.presentation.CliCommand;
import dev.logicojp.reviewer.presentation.CliOutput;
import dev.logicojp.reviewer.presentation.CliUsage;
import dev.logicojp.reviewer.presentation.ReviewAgentConfigResolver;
import dev.logicojp.reviewer.presentation.ReviewExecutionCoordinator;
import dev.logicojp.reviewer.presentation.ReviewModelConfigResolver;
import dev.logicojp.reviewer.presentation.ReviewOptions;
import dev.logicojp.reviewer.presentation.ReviewPreparationService;
import dev.logicojp.reviewer.presentation.ReviewRunRequestFactory;
import dev.logicojp.reviewer.presentation.ReviewTargetResolver;
import dev.logicojp.reviewer.presentation.parser.ReviewOptionsParser;
import dev.logicojp.reviewer.shared.ExecutionCorrelation;
import dev.logicojp.reviewer.util.SecurityAuditLogger;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Main review command that executes the multi-agent code review.
///
/// Injection dependencies are all from {@code presentation.*}, {@code application.port.inbound},
/// {@code domain.*}, {@code shared.*}, or brownfield {@code util.*} — no {@code infrastructure.*}.
@Singleton
public class ReviewCommand implements CliCommand {

    private static final Logger logger = LoggerFactory.getLogger(ReviewCommand.class);

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
            ReviewModelConfigResolver modelConfigResolver,
            ReviewOptionsParser optionsParser,
            ReviewTargetResolver targetResolver,
            ReviewAgentConfigResolver agentConfigResolver,
            ReviewPreparationService preparationService,
            ReviewRunRequestFactory runRequestFactory,
            ReviewExecutionCoordinator executionCoordinator,
            CliOutput output) {
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
        dev.logicojp.reviewer.util.ExecutionCorrelation.putExecutionId(executionId);
        try {
            return runWithCorrelation(options, executionId);
        } finally {
            dev.logicojp.reviewer.util.ExecutionCorrelation.clearExecutionId();
        }
    }

    private int runWithCorrelation(ReviewOptions options, String executionId) {
        String invocationTimestamp = Instant.now().toString();

        ReviewTargetResolver.TargetAndToken targetAndToken =
            targetResolver.resolve(options.target(), options.githubToken());
        ReviewTarget target = targetAndToken.target();
        String resolvedToken = targetAndToken.resolvedToken();

        logger.info("Review execution started: executionId={}, target={}", executionId, target.displayName());
        logReviewAuditEvent(target, options.trustTarget(), resolvedToken != null && !resolvedToken.isBlank());

        ReviewModelConfigResolver.ResolvedModels resolvedModels = modelConfigResolver.resolve(options);
        ReviewAgentConfigResolver.AgentResolution agentResolution = agentConfigResolver.resolve(options);
        List<Path> agentDirs = agentResolution.agentDirectories();
        Map<String, AgentConfig> agentConfigs = agentResolution.agentConfigs();
        Path outputDir = options.outputDirectory();

        preparationService.prepare(
            agentDirs, agentConfigs, target, outputDir,
            resolvedModels.summaryModel(), resolvedModels.reviewModel());

        ReviewRequest reviewRequest = runRequestFactory.create(
            options, target, agentConfigs, outputDir,
            invocationTimestamp, resolvedToken, resolvedModels);

        return executionCoordinator.execute(reviewRequest, agentResolution);
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
