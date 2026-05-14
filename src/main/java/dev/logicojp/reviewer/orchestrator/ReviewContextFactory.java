package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.agent.SharedCircuitBreaker;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.LocalFileConfig;
import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.McpServerConfig;

import java.util.Map;
import java.util.Optional;

final class ReviewContextFactory {

    private final CopilotClient client;
    private final ExecutionConfig executionConfig;
    private final String reasoningEffort;
    private final String outputConstraints;
    private final String invocationTimestamp;
    private final Map<String, McpServerConfig> cachedMcpServers;
    private final LocalFileConfig localFileConfig;
    private final SharedCircuitBreaker reviewCircuitBreaker;

    ReviewContextFactory(CopilotClient client,
                         ExecutionConfig executionConfig,
                         String reasoningEffort,
                         String outputConstraints,
                         String invocationTimestamp,
                         Map<String, McpServerConfig> cachedMcpServers,
                         LocalFileConfig localFileConfig,
                         SharedCircuitBreaker reviewCircuitBreaker) {
        this.client = client;
        this.executionConfig = executionConfig;
        this.reasoningEffort = reasoningEffort;
        this.outputConstraints = outputConstraints;
        this.invocationTimestamp = invocationTimestamp;
        this.cachedMcpServers = cachedMcpServers;
        this.localFileConfig = localFileConfig;
        this.reviewCircuitBreaker = reviewCircuitBreaker;
    }

    ReviewContext create(Optional<String> cachedSourceContent) {
        return ReviewContext.builder()
            .client(client)
            .timeoutMinutes(executionConfig.agentTimeoutMinutes())
            .idleTimeoutMinutes(executionConfig.idleTimeoutMinutes())
            .reasoningEffort(reasoningEffort)
            .sharedSessionEnabled(executionConfig.isSharedSessionEnabled())
            .maxRetries(executionConfig.maxRetries())
            .outputConstraints(outputConstraints)
            .invocationTimestamp(invocationTimestamp)
            .cachedMcpServers(cachedMcpServers)
            .cachedSourceContent(cachedSourceContent.orElse(null))
            .localFileConfig(localFileConfig)
            .reviewCircuitBreaker(reviewCircuitBreaker)
            .agentTuningConfig(new ReviewContext.AgentTuningConfig(
                executionConfig.instructionBufferExtraCapacity()))
            .build();
    }
}