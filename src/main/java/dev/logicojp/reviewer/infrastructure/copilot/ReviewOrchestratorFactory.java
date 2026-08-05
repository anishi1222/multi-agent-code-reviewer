package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.port.inbound.RunReviewPort;
import dev.logicojp.reviewer.application.review.OrchestratorConfig;
import dev.logicojp.reviewer.application.review.ReviewOrchestrator;
import dev.logicojp.reviewer.domain.agent.ReviewSystemPromptFormatter;
import dev.logicojp.reviewer.infrastructure.config.ExecutionConfig;
import dev.logicojp.reviewer.infrastructure.config.ModelConfig;
import dev.logicojp.reviewer.infrastructure.config.RubberDuckConfig;
import dev.logicojp.reviewer.infrastructure.file.LocalFileProvider;
import dev.logicojp.reviewer.infrastructure.template.TemplateRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/// CRITICAL DI wiring point — assembles all infrastructure adapters and creates
/// the application-layer {@link ReviewOrchestrator}.
///
/// Holds all Micronaut-injectable infrastructure singletons and wires them into
/// pure-application / pure-domain objects. This is the only place in the codebase
/// where all five port implementations come together.
///
/// <pre>
///   ManageCopilotClientPort  ← {@link CopilotService}
///   CollectLocalSourcePort   ← {@link LocalFileProvider}
///   LoadTemplatePort         ← {@link TemplateRepository}
///   RunCopilotSessionPort    ← {@link ReviewSessionExecutor}
///   RunRubberDuckSessionPort ← {@link RubberDuckDialogueExecutor}
/// </pre>
@Singleton
public class ReviewOrchestratorFactory {

    private static final Logger logger = LoggerFactory.getLogger(ReviewOrchestratorFactory.class);

    private final CopilotService copilotService;
    private final LocalFileProvider localFileProvider;
    private final TemplateRepository templateRepository;
    private final ExecutionConfig executionConfig;
    private final ModelConfig modelConfig;
    private final RubberDuckConfig rubberDuckConfig;
    private final ReviewSessionConfigFactory sessionConfigFactory;

    @Inject
    public ReviewOrchestratorFactory(CopilotService copilotService,
                                      LocalFileProvider localFileProvider,
                                      TemplateRepository templateRepository,
                                      ExecutionConfig executionConfig,
                                      ModelConfig modelConfig,
                                      RubberDuckConfig rubberDuckConfig,
                                      ReviewSessionConfigFactory sessionConfigFactory) {
        this.copilotService = Objects.requireNonNull(copilotService);
        this.localFileProvider = Objects.requireNonNull(localFileProvider);
        this.templateRepository = Objects.requireNonNull(templateRepository);
        this.executionConfig = Objects.requireNonNull(executionConfig);
        this.modelConfig = Objects.requireNonNull(modelConfig);
        this.rubberDuckConfig = Objects.requireNonNull(rubberDuckConfig);
        this.sessionConfigFactory = Objects.requireNonNull(sessionConfigFactory);
    }

    /// Creates a fully-wired {@link RunReviewPort} for the given orchestrator configuration.
    ///
    /// @param config per-invocation parameters (token, timeout, passes, etc.)
    /// @return {@link ReviewOrchestrator} with all five port implementations wired
    public RunReviewPort create(OrchestratorConfig config) {
        Objects.requireNonNull(config, "config must not be null");

        long agentTimeoutMinutes = config.agentTimeoutMinutes();
        String invocationTimestamp = config.invocationTimestamp();

        var systemPromptFormatter = new ReviewSystemPromptFormatter();

        var sessionExecutor = new ReviewSessionExecutor(
            copilotService,
            sessionConfigFactory,
            systemPromptFormatter,
            agentTimeoutMinutes,
            invocationTimestamp
        );

        var sdkSessionFactory = new SdkRubberDuckSessionFactory(
            copilotService,
            agentTimeoutMinutes,
            invocationTimestamp
        );

        var rubberDuckExecutor = new RubberDuckDialogueExecutor(
            sdkSessionFactory,
            systemPromptFormatter
        );

        logger.debug("ReviewOrchestratorFactory: creating ReviewOrchestrator with config={}", config);

        return new ReviewOrchestrator(
            copilotService,
            localFileProvider,
            templateRepository,
            sessionExecutor,
            rubberDuckExecutor,
            config
        );
    }

    /// Creates an {@link OrchestratorConfig} from infrastructure configuration records
    /// and per-invocation parameters.
    ///
    /// Delegates to {@link ReviewContextFactory} to map config values.
    public OrchestratorConfig buildConfig(String githubToken,
                                           String invocationTimestamp,
                                           String reasoningEffort,
                                           String outputConstraints) {
        return new ReviewContextFactory(executionConfig, modelConfig, rubberDuckConfig)
            .buildOrchestratorConfig(githubToken, invocationTimestamp, reasoningEffort, outputConstraints);
    }
}
