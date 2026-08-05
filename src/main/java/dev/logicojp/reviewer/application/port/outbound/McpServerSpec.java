package dev.logicojp.reviewer.application.port.outbound;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Domain representation of an MCP (Model Context Protocol) server specification.
///
/// This is a pure domain DTO — the infrastructure adapter translates it to the
/// SDK type ({@code McpHttpServerConfig}) when making actual Copilot calls.
///
/// @param name    logical server name (e.g. "github")
/// @param url     HTTP endpoint URL for this server
/// @param headers HTTP headers to send with every request (may include auth; use masked map externally)
/// @param tools   list of tool names exposed by this MCP server (empty = all tools available)
public record McpServerSpec(
    String name,
    String url,
    Map<String, String> headers,
    List<String> tools
) {

    public McpServerSpec {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(url, "url must not be null");
        headers = headers != null ? Map.copyOf(headers) : Map.of();
        tools = tools != null ? List.copyOf(tools) : List.of();
    }

    /// Creates a minimal spec with a URL only (no headers or tool filtering).
    public static McpServerSpec of(String name, String url) {
        return new McpServerSpec(name, url, Map.of(), List.of());
    }

    /// Creates a spec with URL and auth header.
    public static McpServerSpec withAuth(String name, String url, Map<String, String> headers) {
        return new McpServerSpec(name, url, headers, List.of());
    }
}
