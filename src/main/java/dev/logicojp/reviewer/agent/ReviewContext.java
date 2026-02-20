package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.ReviewerConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.util.ApiCircuitBreaker;
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
    ReviewerConfig.LocalFiles localFileConfig,
    ScheduledExecutorService sharedScheduler,
    AgentTuningConfig agentTuningConfig,
    RetryConfig retryConfig,
    ApiCircuitBreaker circuitBreaker
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
            4 * 1024 * 1024, 4096, 32
        );
    }

    /// Retry and backoff parameters for agent execution.
    public record RetryConfig(int maxAttempts, long backoffBaseMs, long backoffMaxMs) {
        public RetryConfig {
            maxAttempts = Math.max(1, maxAttempts);
            backoffBaseMs = Math.max(1, backoffBaseMs);
            backoffMaxMs = Math.max(backoffBaseMs, backoffMaxMs);
        }

        public static final RetryConfig DEFAULTS = new RetryConfig(3, 1000L, 8000L);
    }

    public ReviewContext {
        Objects.requireNonNull(client, "client must not be null");
        Objects.requireNonNull(sharedScheduler, "sharedScheduler must not be null");
        timeoutConfig = timeoutConfig != null ? timeoutConfig : new TimeoutConfig(0, 0, 0);
        customInstructions = customInstructions != null ? List.copyOf(customInstructions) : List.of();
        cachedResources = cachedResources != null ? cachedResources : new CachedResources(null, null);
        agentTuningConfig = agentTuningConfig != null ? agentTuningConfig : AgentTuningConfig.DEFAULTS;
        retryConfig = retryConfig != null ? retryConfig : RetryConfig.DEFAULTS;
        circuitBreaker = circuitBreaker != null ? circuitBreaker : ApiCircuitBreaker.forReview();
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
        private ReviewerConfig.LocalFiles localFileConfig;
        private ScheduledExecutorService sharedScheduler;
        private AgentTuningConfig agentTuningConfig;
        private RetryConfig retryConfig;
        private ApiCircuitBreaker circuitBreaker;

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

        public Builder localFileConfig(ReviewerConfig.LocalFiles localFileConfig) {
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

        public Builder retryConfig(RetryConfig retryConfig) {
            this.retryConfig = retryConfig;
            return this;
        }

        public Builder circuitBreaker(ApiCircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
            return this;
        }

        public ReviewContext build() {
            Objects.requireNonNull(client, "client must not be null");
            Objects.requireNonNull(sharedScheduler, "sharedScheduler must not be null");
            requirePositive(timeoutMinutes, "timeoutMinutes");
            requirePositive(idleTimeoutMinutes, "idleTimeoutMinutes");

            ReviewerConfig.LocalFiles effectiveLocalFileConfig = resolveLocalFileConfig();

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
                retryConfig,
                circuitBreaker
            );
        }

        private void requirePositive(long value, String fieldName) {
            if (value <= 0) {
                throw new IllegalArgumentException(fieldName + " must be positive");
            }
        }

        private ReviewerConfig.LocalFiles resolveLocalFileConfig() {
            if (localFileConfig != null) {
                return localFileConfig;
            }
            return new ReviewerConfig.LocalFiles();
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
