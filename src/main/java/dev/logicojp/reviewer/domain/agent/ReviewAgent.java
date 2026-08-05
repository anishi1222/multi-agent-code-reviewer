package dev.logicojp.reviewer.domain.agent;

import java.util.Objects;

/// Domain identity model for a review agent.
///
/// This is the pure domain representation of an agent — its identity,
/// configuration, and specification. Execution orchestration logic
/// (session management, retries, etc.) belongs to the application layer.
///
/// @param agentId   unique identifier (typically the YAML filename without extension)
/// @param config    the agent's compiled configuration
public record ReviewAgent(
    String agentId,
    AgentConfig config
) {

    public ReviewAgent {
        Objects.requireNonNull(agentId, "agentId must not be null");
        if (agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        Objects.requireNonNull(config, "config must not be null");
    }

    /// Convenience accessor — returns the agent's display name.
    public String displayName() {
        return config.displayName();
    }

    /// Convenience accessor — returns the agent's model identifier.
    public String model() {
        return config.model();
    }

    @Override
    public String toString() {
        return "ReviewAgent{agentId='" + agentId + "', model='" + config.model() + "'}";
    }
}
