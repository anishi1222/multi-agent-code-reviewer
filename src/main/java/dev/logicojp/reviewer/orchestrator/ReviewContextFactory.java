package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.agent.SharedCircuitBreaker;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.LocalFileConfig;
import com.github.copilot.sdk.CopilotClient;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

final class ReviewContextFactory {

    private final CopilotClient client;
    private final ExecutionConfig executionConfig;
    private final String reasoningEffort;
    private final String outputConstraints;
    private final Map<String, Object> cachedMcpServers;
    private final LocalFileConfig localFileConfig;
    private final ScheduledExecutorService sharedScheduler;
    private final SharedCircuitBreaker reviewCircuitBreaker;

    ReviewContextFactory(CopilotClient client,
                         ExecutionConfig executionConfig,
                         String reasoningEffort,
                         String outputConstraints,
                         Map<String, Object> cachedMcpServers,
                         LocalFileConfig localFileConfig,
                         ScheduledExecutorService sharedScheduler,
                         SharedCircuitBreaker reviewCircuitBreaker) {
        this.client = client;
        this.executionConfig = executionConfig;
        this.reasoningEffort = reasoningEffort;
        this.outputConstraints = outputConstraints;
        this.cachedMcpServers = cachedMcpServers;
        this.localFileConfig = localFileConfig;
        this.sharedScheduler = sharedScheduler;
        this.reviewCircuitBreaker = reviewCircuitBreaker;
    }

    ReviewContext create(Optional<String> cachedSourceContent) {
        return ReviewContext.builder()
            .client(client)
            .timeoutMinutes(executionConfig.agentTimeoutMinutes())
            .idleTimeoutMinutes(executionConfig.idleTimeoutMinutes())
            .reasoningEffort(reasoningEffort)
            .maxRetries(executionConfig.maxRetries())
            .outputConstraints(outputConstraints)
            .cachedMcpServers(cachedMcpServers)
            .cachedSourceContent(cachedSourceContent.orElse(null))
            .localFileConfig(localFileConfig)
            .sharedScheduler(sharedScheduler)
            .reviewCircuitBreaker(reviewCircuitBreaker)
            .agentTuningConfig(new ReviewContext.AgentTuningConfig(
                executionConfig.maxAccumulatedSize(),
                executionConfig.initialAccumulatedCapacity(),
                executionConfig.instructionBufferExtraCapacity()))
            .build();
    }
}