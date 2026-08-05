package dev.logicojp.reviewer.application.port.outbound;

import dev.logicojp.reviewer.domain.agent.AgentConfig;

import java.util.List;
import java.util.Objects;

/// Domain DTO carrying all parameters needed to run a multi-turn rubber-duck dialogue.
///
/// No SDK types are referenced — the infrastructure adapter maps this to SDK calls.
///
/// @param agentA       first dialogue participant
/// @param agentB       second dialogue participant  
/// @param initialPrompt the opening prompt given to agentA to start the dialogue
/// @param rounds        number of dialogue rounds to run
/// @param mcpServers    MCP server specifications available to both agents
public record RubberDuckRequest(
    AgentConfig agentA,
    AgentConfig agentB,
    String initialPrompt,
    int rounds,
    List<McpServerSpec> mcpServers
) {

    public RubberDuckRequest {
        Objects.requireNonNull(agentA, "agentA must not be null");
        Objects.requireNonNull(agentB, "agentB must not be null");
        Objects.requireNonNull(initialPrompt, "initialPrompt must not be null");
        if (rounds < 1) {
            throw new IllegalArgumentException("rounds must be at least 1, got: " + rounds);
        }
        mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
    }

    /// Creates a request with the default 2 dialogue rounds.
    public static RubberDuckRequest of(AgentConfig agentA,
                                       AgentConfig agentB,
                                       String initialPrompt) {
        return new RubberDuckRequest(agentA, agentB, initialPrompt, 2, List.of());
    }
}
