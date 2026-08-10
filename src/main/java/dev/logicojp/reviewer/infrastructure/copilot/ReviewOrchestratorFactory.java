package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.port.outbound.CreateReviewSessionPortsPort;
import dev.logicojp.reviewer.application.port.outbound.CreateReviewSessionPortsPort.ReviewSessionOptions;
import dev.logicojp.reviewer.application.port.outbound.CreateReviewSessionPortsPort.ReviewSessionPorts;
import dev.logicojp.reviewer.domain.agent.ReviewSystemPromptFormatter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/// Outbound adapter that creates the two SDK-backed session ports for one review invocation.
///
/// The historical name is retained to avoid a gratuitous public type rename, but this class no
/// longer implements an inbound port, maps application configuration, or constructs an application
/// implementation. Composition-root wiring now binds the application orchestrator directly.
///
/// <pre>
///   RunCopilotSessionPort    ← {@link ReviewSessionExecutor}
///   RunRubberDuckSessionPort ← {@link RubberDuckDialogueExecutor}
/// </pre>
@Singleton
public class ReviewOrchestratorFactory implements CreateReviewSessionPortsPort {

    private static final Logger logger = LoggerFactory.getLogger(ReviewOrchestratorFactory.class);

    private final CopilotService copilotService;
    private final ReviewSessionConfigFactory sessionConfigFactory;

    @Inject
    public ReviewOrchestratorFactory(CopilotService copilotService,
                                      ReviewSessionConfigFactory sessionConfigFactory) {
        this.copilotService = Objects.requireNonNull(copilotService);
        this.sessionConfigFactory = Objects.requireNonNull(sessionConfigFactory);
    }

    @Override
    public ReviewSessionPorts create(ReviewSessionOptions options) {
        Objects.requireNonNull(options, "options must not be null");

        var systemPromptFormatter = new ReviewSystemPromptFormatter();

        var sessionExecutor = new ReviewSessionExecutor(
            copilotService,
            sessionConfigFactory,
            systemPromptFormatter,
            options.agentTimeoutMinutes(),
            options.invocationTimestamp()
        );

        var sdkSessionFactory = new SdkRubberDuckSessionFactory(
            copilotService,
            options.agentTimeoutMinutes(),
            options.invocationTimestamp()
        );

        var rubberDuckExecutor = new RubberDuckDialogueExecutor(
            sdkSessionFactory,
            systemPromptFormatter
        );

        logger.debug("ReviewOrchestratorFactory: creating invocation-scoped session adapters with options={}",
            options);
        return new ReviewSessionPorts(sessionExecutor, rubberDuckExecutor);
    }
}
