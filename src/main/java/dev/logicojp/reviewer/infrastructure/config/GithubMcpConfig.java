package dev.logicojp.reviewer.infrastructure.config;

import dev.logicojp.reviewer.application.port.outbound.McpServerSpec;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// Configuration for the GitHub MCP server connection.
///
/// Key change from brownfield: {@code toMcpServerSpec()} returns {@link McpServerSpec}
/// (a domain DTO) instead of SDK {@code McpHttpServerConfig}.
/// The infrastructure adapter ({@code SkillExecutor}, {@code ReviewSessionConfigFactory})
/// is responsible for converting {@link McpServerSpec} to the SDK type when needed.
@ConfigurationProperties("reviewer.mcp.github")
public record GithubMcpConfig(
    String type,
    String url,
    List<String> tools,
    Map<String, String> headers,
    String authHeaderName,
    @Nullable String authHeaderTemplate,
    @Nullable List<String> allowedHosts
) {
    private static final String DEFAULT_MCP_URL = "https://api.githubcopilot.com/mcp/";
    private static final Set<String> DEFAULT_ALLOWED_HOSTS = Set.of("api.githubcopilot.com");

    public GithubMcpConfig(
        String type, String url, List<String> tools, Map<String, String> headers,
        String authHeaderName, @Nullable String authHeaderTemplate
    ) {
        this(type, url, tools, headers, authHeaderName, authHeaderTemplate, null);
    }

    public GithubMcpConfig {
        type = ConfigDefaults.defaultIfBlank(type, "http");
        validateType(type);
        url = ConfigDefaults.defaultIfBlank(url, DEFAULT_MCP_URL);
        Set<String> effectiveAllowedHosts = sanitizeAllowedHosts(allowedHosts);
        validateUrl(url, effectiveAllowedHosts);
        tools = (tools == null || tools.isEmpty()) ? List.of("*") : List.copyOf(tools);
        headers = (headers == null) ? Map.of() : Map.copyOf(headers);
        authHeaderName = ConfigDefaults.defaultIfBlank(authHeaderName, "Authorization");
        authHeaderTemplate = ConfigDefaults.defaultIfBlank(authHeaderTemplate, "******");
        allowedHosts = List.copyOf(effectiveAllowedHosts);
    }

    private static void validateType(String type) {
        if (!"http".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("GitHub MCP type must be 'http': " + type);
        }
    }

    private static Set<String> sanitizeAllowedHosts(@Nullable List<String> allowedHosts) {
        if (allowedHosts == null) {
            return DEFAULT_ALLOWED_HOSTS;
        }
        Set<String> normalized = allowedHosts.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(host -> host.trim().toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("GitHub MCP allowed hosts must not be empty");
        }
        return normalized;
    }

    private static void validateUrl(String url, Set<String> allowedHosts) {
        URI parsed = URI.create(url);
        String scheme = parsed.getScheme();
        if (scheme == null || !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("GitHub MCP URL must use HTTPS: " + url);
        }
        String host = parsed.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("GitHub MCP URL must include host: " + url);
        }
        if (!allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                "GitHub MCP URL host is not in allowlist: " + host + " (allowed: " + allowedHosts + ")");
        }
    }

    /// Builds a {@link McpServerSpec} for this GitHub MCP configuration.
    /// Returns {@link Optional#empty()} when inputs are invalid.
    public static Optional<McpServerSpec> buildMcpServer(String githubToken, GithubMcpConfig config) {
        if (githubToken != null && !githubToken.isBlank() && config != null) {
            return Optional.of(config.toMcpServerSpec(githubToken));
        }
        return Optional.empty();
    }

    /// Builds a domain {@link McpServerSpec} from this config.
    /// Headers are included with the resolved auth header.
    public McpServerSpec toMcpServerSpec(String token) {
        Map<String, String> combinedHeaders = new HashMap<>(headers != null ? headers : Map.of());
        applyAuthHeader(token, combinedHeaders);
        return new McpServerSpec("github", url, Map.copyOf(combinedHeaders), List.copyOf(tools));
    }

    private void applyAuthHeader(String token, Map<String, String> combinedHeaders) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (containsCrLf(authHeaderName)) {
            throw new IllegalArgumentException("Auth header name contains illegal characters (CRLF)");
        }
        String headerValue = authHeaderTemplate.replace("{token}", token);
        if (containsCrLf(headerValue)) {
            throw new IllegalArgumentException("Auth header value contains illegal characters (CRLF)");
        }
        combinedHeaders.put(authHeaderName, headerValue);
    }

    private static boolean containsCrLf(String value) {
        return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
    }
}
