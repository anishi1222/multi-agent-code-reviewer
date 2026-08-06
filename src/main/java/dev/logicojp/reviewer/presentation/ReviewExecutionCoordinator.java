package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.ReviewRequest;
import dev.logicojp.reviewer.presentation.ReviewAgentConfigResolver.AgentResolution;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Coordinates the review execution flow: validates agents, then delegates to executor.
///
/// No lifecycle management — the port adapters handle Copilot session lifecycle internally.
@Singleton
public class ReviewExecutionCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(ReviewExecutionCoordinator.class);

    private final ReviewRunExecutor reviewRunExecutor;

    @Inject
    public ReviewExecutionCoordinator(ReviewRunExecutor reviewRunExecutor) {
        this.reviewRunExecutor = reviewRunExecutor;
    }

    public int execute(ReviewRequest request, AgentResolution agentResolution) {
        if (agentResolution.agentConfigs().isEmpty()) {
            logger.warn("No agents found. Additional dirs searched: {}", agentResolution.agentDirectories());
            throw new CliValidationException(
                "No agents found. Dirs searched: " + agentResolution.agentDirectories(), true);
        }
        return reviewRunExecutor.execute(request);
    }
}
