package dev.logicojp.reviewer.config;

import com.github.copilot.sdk.json.McpHttpServerConfig;
import com.github.copilot.sdk.json.McpServerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GithubMcpConfig")
class GithubMcpConfigTest {

    @Nested
    @DisplayName("コンストラクタ - デフォルト値")
    class DefaultValues {

        @Test
        @DisplayName("すべてnullの場合はデフォルト値が設定される")
        void allNullsUseDefaults() {
            GithubMcpConfig config = new GithubMcpConfig(null, null, null, null, null, null);
            assertThat(config.type()).isEqualTo("http");
            assertThat(config.url()).isEqualTo("https://api.githubcopilot.com/mcp/");
            assertThat(config.tools()).containsExactly("*");
            assertThat(config.headers()).isEmpty();
            assertThat(config.authHeaderName()).isEqualTo("Authorization");
            assertThat(config.authHeaderTemplate()).isEqualTo("Bearer {token}");
            assertThat(config.allowedHosts()).containsExactly("api.githubcopilot.com");
        }
    }

    @Nested
    @DisplayName("URL validation")
    class UrlValidation {

        @Test
        @DisplayName("http URL は拒否される")
        void rejectsNonHttpsUrl() {
            assertThatThrownBy(() -> new GithubMcpConfig(
                "http", "http://api.example.com/mcp/", List.of("*"), Map.of(), "Authorization", "Bearer {token}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must use HTTPS");
        }

        @Test
        @DisplayName("許可されていないホストは拒否される")
        void rejectsUrlHostOutsideAllowlist() {
            assertThatThrownBy(() -> new GithubMcpConfig(
                "http", "https://api.example.com/mcp/", List.of("*"), Map.of(), "Authorization", "Bearer {token}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host is not in allowlist");
        }

        @Test
        @DisplayName("デフォルト許可ホストは受け入れられる")
        void acceptsDefaultAllowedHost() {
            GithubMcpConfig config = new GithubMcpConfig(
                "http",
                "https://api.githubcopilot.com/mcp/",
                List.of("*"),
                Map.of(),
                "Authorization",
                "Bearer {token}"
            );
            assertThat(config.url()).isEqualTo("https://api.githubcopilot.com/mcp/");
        }

        @Test
        @DisplayName("allowlist指定時は許可ホストを受け入れる")
        void acceptsConfiguredAllowedHost() {
            GithubMcpConfig config = new GithubMcpConfig(
                "http",
                "https://api.example.com/mcp/",
                List.of("*"),
                Map.of(),
                "Authorization",
                "Bearer {token}",
                List.of("api.example.com")
            );
            assertThat(config.url()).isEqualTo("https://api.example.com/mcp/");
            assertThat(config.allowedHosts()).containsExactly("api.example.com");
        }

        @Test
        @DisplayName("allowlistが空の場合は拒否される")
        void rejectsEmptyAllowlist() {
            assertThatThrownBy(() -> new GithubMcpConfig(
                "http",
                "https://api.githubcopilot.com/mcp/",
                List.of("*"),
                Map.of(),
                "Authorization",
                "Bearer {token}",
                List.of(" ", "")
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed hosts must not be empty");
        }
    }

    @Nested
    @DisplayName("type validation")
    class TypeValidation {

        @Test
        @DisplayName("http以外のtypeは拒否される")
        void rejectsNonHttpType() {
            assertThatThrownBy(() -> new GithubMcpConfig(
                "stdio", "https://api.githubcopilot.com/mcp/", List.of("*"), Map.of(), "Authorization", "Bearer {token}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type must be 'http'");
        }
    }

    @Nested
    @DisplayName("toMcpServer")
    class ToMcpServer {

        @Test
        @DisplayName("トークン付きでSDKのMcpHttpServerConfigを生成する")
        void generatesMcpServerWithToken() {
            GithubMcpConfig config = new GithubMcpConfig(
                "http", "https://api.example.com/mcp/",
                List.of("tool1"), Map.of(), "Authorization", "Bearer {token}", List.of("api.example.com"));
            McpHttpServerConfig server = config.toMcpServer("my-token");

            assertThat(server.getType()).isEqualTo("http");
            assertThat(server.getUrl()).isEqualTo("https://api.example.com/mcp/");
            assertThat(server.getTools()).containsExactly("tool1");
            assertThat(server.getHeaders()).containsEntry("Authorization", "Bearer my-token");
        }

        @Test
        @DisplayName("トークンがnullの場合はAuthorizationヘッダーを追加しない")
        void nullTokenSkipsAuthHeader() {
            GithubMcpConfig config = new GithubMcpConfig(null, null, null, null, null, null);
            McpHttpServerConfig server = config.toMcpServer(null);

            assertThat(server.getHeaders()).doesNotContainKey("Authorization");
        }

        @Test
        @DisplayName("空白トークンの場合はAuthorizationヘッダーを追加しない")
        void blankTokenSkipsAuthHeader() {
            GithubMcpConfig config = new GithubMcpConfig(null, null, null, null, null, null);
            McpHttpServerConfig server = config.toMcpServer("  ");

            assertThat(server.getHeaders()).doesNotContainKey("Authorization");
        }

        @Test
        @DisplayName("既存ヘッダーとAuthorizationヘッダーが結合される")
        void mergesExistingHeaders() {
            GithubMcpConfig config = new GithubMcpConfig(
                "http", "https://api.example.com/",
                List.of("*"), Map.of("X-Custom", "value"),
                "Authorization", "Bearer {token}", List.of("api.example.com"));
            McpHttpServerConfig server = config.toMcpServer("tok");

            assertThat(server.getHeaders()).containsEntry("X-Custom", "value");
            assertThat(server.getHeaders()).containsEntry("Authorization", "Bearer tok");
        }

        @Test
        @DisplayName("{token}プレースホルダーのみがサポートされている")
        void onlySingleBraceTokenPlaceholderSupported() {
            GithubMcpConfig config = new GithubMcpConfig(
                "http", "https://api.example.com/",
                List.of("*"), Map.of(),
                "Authorization", "token ${token}", List.of("api.example.com"));
            McpHttpServerConfig server = config.toMcpServer("abc123");

            // ${token} contains {token} which gets replaced, leaving the $ prefix
            assertThat(server.getHeaders()).containsEntry("Authorization", "token $abc123");
        }

        @Test
        @DisplayName("ヘッダーマップのtoString()でAuthorization値をマスクする")
        void masksAuthorizationInHeadersToString() {
            GithubMcpConfig config = new GithubMcpConfig(
                "http", "https://api.example.com/",
                List.of("*"), Map.of("X-Custom", "value"),
                "Authorization", "Bearer {token}", List.of("api.example.com"));
            McpHttpServerConfig server = config.toMcpServer("ghp_secret123");

            // Raw value remains accessible for actual HTTP requests
            assertThat(server.getHeaders().get("Authorization")).isEqualTo("Bearer ghp_secret123");

            // toString() variants mask the sensitive value to prevent log leakage
            assertThat(server.getHeaders().toString()).contains("Bearer ***");
            assertThat(server.getHeaders().toString()).doesNotContain("ghp_secret123");
            assertThat(server.getHeaders().entrySet().toString()).contains("Bearer ***");
            assertThat(server.getHeaders().entrySet().toString()).doesNotContain("ghp_secret123");
            assertThat(server.getHeaders().values().toString()).contains("Bearer ***");
            assertThat(server.getHeaders().values().toString()).doesNotContain("ghp_secret123");
        }
    }

    @Nested
    @DisplayName("buildMcpServers")
    class BuildMcpServersTests {

        @Test
        @DisplayName("トークンと設定が揃っている場合はMCPサーバー設定を返す")
        void returnsMcpServersWhenInputsAreValid() {
            GithubMcpConfig config = new GithubMcpConfig(null, null, null, null, null, null);

            var servers = GithubMcpConfig.buildMcpServers("ghp_token", config);

            assertThat(servers).isPresent();
            Map<String, McpServerConfig> map = servers.orElseThrow();
            assertThat(map).containsKey("github");
            assertThat(map.get("github")).isInstanceOf(McpHttpServerConfig.class);
        }

        @Test
        @DisplayName("トークンまたは設定が不正な場合はemptyを返す")
        void returnsEmptyWhenInputsAreInvalid() {
            assertThat(GithubMcpConfig.buildMcpServers("", new GithubMcpConfig(null, null, null, null, null, null)))
                .isEmpty();
            assertThat(GithubMcpConfig.buildMcpServers("ghp_token", null)).isEmpty();
        }
    }
}
