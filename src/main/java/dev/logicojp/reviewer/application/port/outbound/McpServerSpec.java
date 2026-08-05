package dev.logicojp.reviewer.application.port.outbound;

import dev.logicojp.reviewer.shared.SensitiveHeaderMasking;

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
/// @param headers HTTP headers to send with every request. Auth values are masked in
///                {@code toString()} automatically; {@code get()} still returns the raw value.
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
        // T013: wrap rather than Map.copyOf(...). A plain defensive copy strips any masking
        // wrapper the caller supplied, so toString() emitted the raw Authorization token into
        // SDK debug logs. Masking is now an invariant of this DTO on every construction path:
        // toString() shows "Bearer ***" while get("Authorization") still returns the real value.
        // MaskedHeadersMap copies defensively itself, so immutability is preserved.
        headers = headers != null ? SensitiveHeaderMasking.wrapHeaders(headers) : Map.of();
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
