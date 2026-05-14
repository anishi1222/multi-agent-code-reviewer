package dev.logicojp.reviewer.config;

import com.github.copilot.sdk.json.McpHttpServerConfig;
import com.github.copilot.sdk.json.McpServerConfig;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

import java.net.URI;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

/// Configuration for the GitHub MCP server connection.
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
        String type,
        String url,
        List<String> tools,
        Map<String, String> headers,
        String authHeaderName,
        @Nullable String authHeaderTemplate
    ) {
        this(type, url, tools, headers, authHeaderName, authHeaderTemplate, null);
    }


    public GithubMcpConfig {
        type = ConfigDefaults.defaultIfBlank(type, "http");
        url = ConfigDefaults.defaultIfBlank(url, DEFAULT_MCP_URL);
        Set<String> effectiveAllowedHosts = sanitizeAllowedHosts(allowedHosts);
        validateUrl(url, effectiveAllowedHosts);
        tools = (tools == null || tools.isEmpty()) ? List.of("*") : List.copyOf(tools);
        headers = (headers == null) ? Map.of() : Map.copyOf(headers);
        authHeaderName = ConfigDefaults.defaultIfBlank(authHeaderName, "Authorization");
        authHeaderTemplate = ConfigDefaults.defaultIfBlank(authHeaderTemplate, "Bearer {token}");
        allowedHosts = List.copyOf(effectiveAllowedHosts);
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
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(normalizedHost)) {
            throw new IllegalArgumentException(
                "GitHub MCP URL host is not in allowlist: " + host + " (allowed: " + allowedHosts + ")"
            );
        }
    }

    /// Builds an MCP server map suitable for {@code SessionConfig#setMcpServers}.
    /// Returns {@link Optional#empty()} when inputs are invalid (no token or no config).
    public static Optional<Map<String, McpServerConfig>> buildMcpServers(String githubToken, GithubMcpConfig config) {
        if (canBuildMcpServers(githubToken, config)) {
            return Optional.of(Map.of("github", config.toMcpServer(githubToken)));
        }
        return Optional.empty();
    }

    private static boolean canBuildMcpServers(String githubToken, GithubMcpConfig config) {
        return githubToken != null && !githubToken.isBlank() && config != null;
    }

    /// Builds an SDK {@link McpHttpServerConfig} for this GitHub MCP configuration.
    /// The returned object's headers map masks sensitive values (e.g. Authorization)
    /// in {@code toString()}/{@code entrySet().toString()} renderings to prevent
    /// token leakage via SDK debug logging while keeping the raw value available
    /// for actual HTTP requests via {@code Map#get}.
    public McpHttpServerConfig toMcpServer(String token) {
        Map<String, String> combinedHeaders = new HashMap<>(headers != null ? headers : Map.of());
        applyAuthHeader(token, combinedHeaders);
        Map<String, String> immutableHeaders = Map.copyOf(combinedHeaders);
        Map<String, String> maskedHeaders = SensitiveHeaderMasking.wrapHeaders(immutableHeaders);
        return new McpHttpServerConfig()
            .setUrl(url)
            .setHeaders(maskedHeaders)
            .setTools(tools);
    }

    private void applyAuthHeader(String token, Map<String, String> combinedHeaders) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (containsCrLf(authHeaderName)) {
            throw new IllegalArgumentException("Auth header name contains illegal characters (CRLF)");
        }
        String headerValue = authHeaderTemplate
            .replace("{token}", token);
        if (containsCrLf(headerValue)) {
            throw new IllegalArgumentException("Auth header value contains illegal characters (CRLF)");
        }
        combinedHeaders.put(authHeaderName, headerValue);
    }

    private static boolean containsCrLf(String value) {
        return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
    }
}
