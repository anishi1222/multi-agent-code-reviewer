package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.config.PromptBudgetConfig;
import dev.logicojp.reviewer.config.RubberDuckConfig;
import dev.logicojp.reviewer.target.ReviewTarget;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.Map;

@Singleton
class ReviewRunRequestFactory {

    private final RubberDuckConfig defaultRubberDuckConfig;
    private final PromptBudgetConfig defaultPromptBudgetConfig;

    @Inject
    ReviewRunRequestFactory(RubberDuckConfig defaultRubberDuckConfig,
                            PromptBudgetConfig defaultPromptBudgetConfig) {
        this.defaultRubberDuckConfig = defaultRubberDuckConfig;
        this.defaultPromptBudgetConfig = defaultPromptBudgetConfig;
    }

    ReviewRunRequestFactory() {
        this(new RubberDuckConfig(), new PromptBudgetConfig());
    }

    public ReviewRunExecutor.ReviewRunRequest create(
        ReviewOptions options,
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
        RubberDuckConfig rubberDuckConfig = resolveRubberDuckConfig(options);
        PromptBudgetConfig promptBudgetConfig = resolvePromptBudgetConfig(options);

        return new ReviewRunExecutor.ReviewRunRequest(
            target,
            summaryModel,
            reasoningEffort,
            invocationTimestamp,
            agentConfigs,
            parallelism,
            noSummary,
            outputDirectory,
            rubberDuckConfig,
            promptBudgetConfig
        );
    }

    private RubberDuckConfig resolveRubberDuckConfig(ReviewOptions options) {
        boolean enabled = !options.noRubberDuck()
            && (options.rubberDuck() || defaultRubberDuckConfig.enabled());
        int rounds = options.dialogueRounds() > 0
            ? options.dialogueRounds()
            : defaultRubberDuckConfig.dialogueRounds();
        String peerModel = options.peerModel() != null
            ? options.peerModel()
            : defaultRubberDuckConfig.peerModel();
        String strategy = defaultRubberDuckConfig.synthesisStrategy();
        return new RubberDuckConfig(enabled, rounds, peerModel, strategy);
    }

    private PromptBudgetConfig resolvePromptBudgetConfig(ReviewOptions options) {
        return defaultPromptBudgetConfig.withCompactPrompts(
            options.compactPrompts() || defaultPromptBudgetConfig.compactPrompts()
        );
    }

    private String resolveSummaryModel(ModelConfig modelConfig) {
        return modelConfig.summaryModel();
    }

    private String resolveReasoningEffort(ModelConfig modelConfig) {
        return modelConfig.reasoningEffort();
    }

    private int resolveParallelism(ReviewOptions options) {
        return options.parallelism();
    }

    private boolean isSummaryDisabled(ReviewOptions options) {
        return options.noSummary();
    }

}
