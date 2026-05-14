package dev.logicojp.reviewer.orchestrator;

import com.github.copilot.sdk.json.McpServerConfig;

import java.util.Map;
import java.util.Objects;

record OrchestratorCollaborators(
    AgentReviewerFactory reviewerFactory,
    LocalSourceCollectorFactory localSourceCollectorFactory,
    ExecutorResources executorResources,
    Map<String, McpServerConfig> cachedMcpServers,
    ReviewResultPipeline reviewResultPipeline,
    AgentReviewExecutor agentReviewExecutor,
    ReviewExecutionModeRunner reviewExecutionModeRunner,
    ReviewContextFactory reviewContextFactory,
    LocalSourcePrecomputer localSourcePrecomputer
) {
    OrchestratorCollaborators {
        reviewerFactory = Objects.requireNonNull(reviewerFactory);
        localSourceCollectorFactory = Objects.requireNonNull(localSourceCollectorFactory);
        executorResources = Objects.requireNonNull(executorResources);
        cachedMcpServers = cachedMcpServers != null ? Map.copyOf(cachedMcpServers) : Map.of();
        reviewResultPipeline = Objects.requireNonNull(reviewResultPipeline);
        agentReviewExecutor = Objects.requireNonNull(agentReviewExecutor);
        reviewExecutionModeRunner = Objects.requireNonNull(reviewExecutionModeRunner);
        reviewContextFactory = Objects.requireNonNull(reviewContextFactory);
        localSourcePrecomputer = Objects.requireNonNull(localSourcePrecomputer);
    }
}