package dev.logicojp.reviewer.application.port.outbound;

import dev.logicojp.reviewer.domain.agent.AgentConfig;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Domain DTO carrying all parameters needed to run a single Copilot review session.
///
/// No SDK types are referenced — the infrastructure adapter maps this to the
/// SDK {@code SessionConfig} when initiating the actual session.
///
/// @param agentConfig the agent configuration for this session
/// @param prompt      the review prompt to submit
/// @param mcpServers  MCP server specifications (empty means no MCP tools)
/// @param parameters  additional session parameters (e.g. model override, max tokens)
public record SessionRequest(
    AgentConfig agentConfig,
    String prompt,
    List<McpServerSpec> mcpServers,
    Map<String, String> parameters
) {

    public SessionRequest {
        Objects.requireNonNull(agentConfig, "agentConfig must not be null");
        Objects.requireNonNull(prompt, "prompt must not be null");
        mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }

    /// Creates a simple session request with no MCP servers or extra parameters.
    public static SessionRequest of(AgentConfig agentConfig, String prompt) {
        return new SessionRequest(agentConfig, prompt, List.of(), Map.of());
    }

    /// Creates a session request with MCP servers.
    public static SessionRequest withMcp(AgentConfig agentConfig,
                                         String prompt,
                                         List<McpServerSpec> mcpServers) {
        return new SessionRequest(agentConfig, prompt, mcpServers, Map.of());
    }
}
