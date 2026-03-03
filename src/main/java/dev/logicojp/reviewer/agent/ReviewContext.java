package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import com.github.copilot.sdk.CopilotClient;
import io.micronaut.core.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;

/// Shared, immutable context for executing review agents.
///
/// Groups the common configuration that every {@link ReviewAgent} needs,
/// reducing the constructor parameter count from 10 to 2
/// ({@code AgentConfig} + {@code ReviewContext}).
///
/// @param client              The Copilot SDK client
/// @param timeoutConfig       Timeout and retry configuration
/// @param customInstructions  Custom instructions to inject into agent prompts
/// @param reasoningEffort     Reasoning effort level for reasoning models (nullable)
/// @param outputConstraints   Output constraints template content (nullable)
/// @param cachedResources     Pre-computed cached resources for reuse across agents (nullable fields)
/// @param localFileConfig     Local file collection configuration (used by fallback path)
/// @param sharedScheduler     Shared ScheduledExecutorService for idle-timeout scheduling
/// @param agentTuningConfig   Internal tuning parameters for agent execution
public record ReviewContext(
    CopilotClient client,
    TimeoutConfig timeoutConfig,
    List<CustomInstruction> customInstructions,
    @Nullable String reasoningEffort,
    @Nullable String outputConstraints,
    CachedResources cachedResources,
    LocalFileConfig localFileConfig,
    ScheduledExecutorService sharedScheduler,
    AgentTuningConfig agentTuningConfig,
    SharedCircuitBreaker reviewCircuitBreaker
) {

    /// Groups timeout and retry parameters.
    public record TimeoutConfig(long timeoutMinutes, long idleTimeoutMinutes, int maxRetries) {}

    /// Groups pre-computed resources that are shared across agents.
    public record CachedResources(
        @Nullable Map<String, Object> mcpServers,
        @Nullable String sourceContent
    ) {}

    /// Internal tuning parameters for agent execution.
    public record AgentTuningConfig(
        int maxAccumulatedSize,
        int initialAccumulatedCapacity,
        int instructionBufferExtraCapacity
    ) {
        public static final AgentTuningConfig DEFAULTS = new AgentTuningConfig(
            ExecutionConfig.DEFAULT_MAX_ACCUMULATED_SIZE,
            ExecutionConfig.DEFAULT_INITIAL_ACCUMULATED_CAPACITY,
            ExecutionConfig.DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY
        );
    }

    public ReviewContext {
        Objects.requireNonNull(client, "client must not be null");
        Objects.requireNonNull(sharedScheduler, "sharedScheduler must not be null");
        timeoutConfig = timeoutConfig != null ? timeoutConfig : new TimeoutConfig(0, 0, 0);
        customInstructions = customInstructions != null ? List.copyOf(customInstructions) : List.of();
        cachedResources = cachedResources != null ? cachedResources : new CachedResources(null, null);
        agentTuningConfig = agentTuningConfig != null ? agentTuningConfig : AgentTuningConfig.DEFAULTS;
        reviewCircuitBreaker = reviewCircuitBreaker != null
            ? reviewCircuitBreaker
            : SharedCircuitBreaker.withDefaultConfig();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private CopilotClient client;
        private long timeoutMinutes;
        private long idleTimeoutMinutes;
        private List<CustomInstruction> customInstructions;
        private String reasoningEffort;
        private int maxRetries;
        private String outputConstraints;
        private Map<String, Object> cachedMcpServers;
        private String cachedSourceContent;
        private LocalFileConfig localFileConfig;
        private ScheduledExecutorService sharedScheduler;
        private AgentTuningConfig agentTuningConfig;
        private SharedCircuitBreaker reviewCircuitBreaker;

        public Builder client(CopilotClient client) {
            this.client = client;
            return this;
        }

        public Builder timeoutMinutes(long timeoutMinutes) {
            this.timeoutMinutes = timeoutMinutes;
            return this;
        }

        public Builder idleTimeoutMinutes(long idleTimeoutMinutes) {
            this.idleTimeoutMinutes = idleTimeoutMinutes;
            return this;
        }

        public Builder customInstructions(List<CustomInstruction> customInstructions) {
            this.customInstructions = customInstructions;
            return this;
        }

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder outputConstraints(String outputConstraints) {
            this.outputConstraints = outputConstraints;
            return this;
        }

        public Builder cachedMcpServers(Map<String, Object> cachedMcpServers) {
            this.cachedMcpServers = cachedMcpServers;
            return this;
        }

        public Builder cachedSourceContent(String cachedSourceContent) {
            this.cachedSourceContent = cachedSourceContent;
            return this;
        }

        public Builder localFileConfig(LocalFileConfig localFileConfig) {
            this.localFileConfig = localFileConfig;
            return this;
        }

        public Builder sharedScheduler(ScheduledExecutorService sharedScheduler) {
            this.sharedScheduler = sharedScheduler;
            return this;
        }

        public Builder agentTuningConfig(AgentTuningConfig agentTuningConfig) {
            this.agentTuningConfig = agentTuningConfig;
            return this;
        }

        public Builder reviewCircuitBreaker(SharedCircuitBreaker reviewCircuitBreaker) {
            this.reviewCircuitBreaker = reviewCircuitBreaker;
            return this;
        }

        public ReviewContext build() {
            Objects.requireNonNull(client, "client must not be null");
            Objects.requireNonNull(sharedScheduler, "sharedScheduler must not be null");
            requirePositive(timeoutMinutes, "timeoutMinutes");
            requirePositive(idleTimeoutMinutes, "idleTimeoutMinutes");

            LocalFileConfig effectiveLocalFileConfig = resolveLocalFileConfig();

            return new ReviewContext(
                client,
                new TimeoutConfig(timeoutMinutes, idleTimeoutMinutes, maxRetries),
                customInstructions,
                reasoningEffort,
                outputConstraints,
                new CachedResources(cachedMcpServers, cachedSourceContent),
                effectiveLocalFileConfig,
                sharedScheduler,
                agentTuningConfig,
                reviewCircuitBreaker
            );
        }

        private void requirePositive(long value, String fieldName) {
            if (value <= 0) {
                throw new IllegalArgumentException(fieldName + " must be positive");
            }
        }

        private LocalFileConfig resolveLocalFileConfig() {
            if (localFileConfig != null) {
                return localFileConfig;
            }
            return new LocalFileConfig();
        }
    }

    @Override
    public String toString() {
        return "ReviewContext{cachedMcpServers=%s, timeoutMinutes=%d, idleTimeoutMinutes=%d, maxRetries=%d, localFileConfig=%s}"
            .formatted(
                cachedResources.mcpServers() != null ? "(configured)" : "(null)",
                timeoutConfig.timeoutMinutes(),
                timeoutConfig.idleTimeoutMinutes(),
                timeoutConfig.maxRetries(),
                localFileConfig != null ? "(configured)" : "(null)");
    }
}
