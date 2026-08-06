package dev.logicojp.reviewer.application.port.outbound;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Domain representation of an MCP (Model Context Protocol) server specification.
///
/// This is a pure domain DTO — the infrastructure adapter translates it to the
/// SDK type ({@code McpHttpServerConfig}) when making actual Copilot calls.
///
/// ## Headers are carried in the clear, deliberately (ADR-0007 D5)
///
/// This record used to wrap [#headers] in a map whose `toString()` masked auth values. That was
/// removed because it could not do the job it appeared to do:
///
///   - it guarded `toString()` only — `get()`, `entrySet()` and any serializer saw the raw value;
///   - it was object-identity bound, so a copy, a stream or a rebuild dropped it silently;
///   - measured on `copilot-sdk-java`, `McpHttpServerConfig` stores the map without a defensive
///     copy and overrides no `toString()`, so the wrapper protected nothing past that boundary;
///   - it made a port declaration depend on a security helper in `shared`.
///
/// Masking is the responsibility of the **sink**: `logback.xml` / `logback-json.xml` mask on every
/// appender, by value shape and by header name, whatever object produced the text. Do not
/// re-introduce masking here — `LayerDependencyRulesTest` Rule 4b forbids the dependency and
/// `SensitiveHeaderMaskingSinkCanaryTest` pins the behaviour on both sides.
///
/// @param name    logical server name (e.g. "github")
/// @param url     HTTP endpoint URL for this server
/// @param headers HTTP headers to send with every request. Values are held **unmasked**;
///                anything that renders them is responsible for going through the log sink.
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
