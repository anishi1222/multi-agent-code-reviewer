package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.config.RubberDuckConfig;
import dev.logicojp.reviewer.target.ReviewTarget;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.Map;

@Singleton
class ReviewRunRequestFactory {

    private final RubberDuckConfig defaultRubberDuckConfig;

    @Inject
    ReviewRunRequestFactory(RubberDuckConfig defaultRubberDuckConfig) {
        this.defaultRubberDuckConfig = defaultRubberDuckConfig;
    }

    ReviewRunRequestFactory() {
        this(new RubberDuckConfig());
    }

    public ReviewRunExecutor.ReviewRunRequest create(
        ReviewCommand.ParsedOptions options,
        ReviewTarget target,
        ModelConfig modelConfig,
        Map<String, AgentConfig> agentConfigs,
        Path outputDirectory,
        String invocationTimestamp
    ) {
        String summaryModel = resolveSummaryModel(modelConfig);
        String reasoningEffort = resolveReasoningEffort(modelConfig);
        int parallelism = resolveParallelism(options);
        boolean noSummary = isSummaryDisabled(options);
        boolean noSharedSession = isSharedSessionDisabled(options);
        RubberDuckConfig rubberDuckConfig = resolveRubberDuckConfig(options);

        return new ReviewRunExecutor.ReviewRunRequest(
            target,
            summaryModel,
            reasoningEffort,
            invocationTimestamp,
            agentConfigs,
            parallelism,
            noSummary,
            noSharedSession,
            outputDirectory,
            rubberDuckConfig
        );
    }

    private RubberDuckConfig resolveRubberDuckConfig(ReviewCommand.ParsedOptions options) {
        boolean enabled = options.rubberDuck() || defaultRubberDuckConfig.enabled();
        int rounds = options.dialogueRounds() > 0
            ? options.dialogueRounds()
            : defaultRubberDuckConfig.dialogueRounds();
        String peerModel = options.peerModel() != null
            ? options.peerModel()
            : defaultRubberDuckConfig.peerModel();
        String strategy = defaultRubberDuckConfig.synthesisStrategy();
        return new RubberDuckConfig(enabled, rounds, peerModel, strategy);
    }

    private String resolveSummaryModel(ModelConfig modelConfig) {
        return modelConfig.summaryModel();
    }

    private String resolveReasoningEffort(ModelConfig modelConfig) {
        return modelConfig.reasoningEffort();
    }

    private int resolveParallelism(ReviewCommand.ParsedOptions options) {
        return options.parallelism();
    }

    private boolean isSummaryDisabled(ReviewCommand.ParsedOptions options) {
        return options.noSummary();
    }

    private boolean isSharedSessionDisabled(ReviewCommand.ParsedOptions options) {
        return options.noSharedSession();
    }
}
