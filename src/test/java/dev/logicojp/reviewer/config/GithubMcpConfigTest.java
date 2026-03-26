package dev.logicojp.reviewer.config;

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
    @DisplayName("toMcpServer")
    class ToMcpServer {

        @Test
        @DisplayName("トークン付きでMCPサーバー設定を生成する")
        void generatesMcpServerWithToken() {
            GithubMcpConfig config = new GithubMcpConfig(
                "http", "https://api.example.com/mcp/",
                List.of("tool1"), Map.of(), "Authorization", "Bearer {token}", List.of("api.example.com"));
            Map<String, Object> server = config.toMcpServer("my-token");

            assertThat(server).containsEntry("type", "http");
            assertThat(server).containsEntry("url", "https://api.example.com/mcp/");
            assertThat(server).containsEntry("tools", List.of("tool1"));

            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) server.get("headers");
            assertThat(headers).containsEntry("Authorization", "Bearer my-token");
        }

        @Test
        @DisplayName("トークンがnullの場合はAuthorizationヘッダーを追加しない")
        void nullTokenSkipsAuthHeader() {
            GithubMcpConfig config = new GithubMcpConfig(null, null, null, null, null, null);
            Map<String, Object> server = config.toMcpServer(null);

            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) server.get("headers");
            assertThat(headers).doesNotContainKey("Authorization");
        }

        @Test
        @DisplayName("空白トークンの場合はAuthorizationヘッダーを追加しない")
        void blankTokenSkipsAuthHeader() {
            GithubMcpConfig config = new GithubMcpConfig(null, null, null, null, null, null);
            Map<String, Object> server = config.toMcpServer("  ");

            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) server.get("headers");
            assertThat(headers).doesNotContainKey("Authorization");
        }

        @Test
        @DisplayName("既存ヘッダーとAuthorizationヘッダーが結合される")
        void mergesExistingHeaders() {
            GithubMcpConfig config = new GithubMcpConfig(
                "http", "https://api.example.com/",
                List.of("*"), Map.of("X-Custom", "value"),
                "Authorization", "Bearer {token}", List.of("api.example.com"));
            Map<String, Object> server = config.toMcpServer("tok");

            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) server.get("headers");
            assertThat(headers).containsEntry("X-Custom", "value");
            assertThat(headers).containsEntry("Authorization", "Bearer tok");
        }

        @Test
        @DisplayName("{token}プレースホルダーのみがサポートされている")
        void onlySingleBraceTokenPlaceholderSupported() {
            GithubMcpConfig config = new GithubMcpConfig(
                "http", "https://api.example.com/",
                List.of("*"), Map.of(),
                "Authorization", "token ${token}", List.of("api.example.com"));
            Map<String, Object> server = config.toMcpServer("abc123");

            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) server.get("headers");
            // ${token} contains {token} which gets replaced, leaving the $ prefix
            assertThat(headers).containsEntry("Authorization", "token $abc123");
        }

        @Test
        @DisplayName("entrySetとvaluesのtoStringでもAuthorization値をマスクする")
        void masksAuthorizationInEntrySetAndValuesToString() {
            GithubMcpConfig config = new GithubMcpConfig(
                "http", "https://api.example.com/",
                List.of("*"), Map.of("X-Custom", "value"),
                "Authorization", "Bearer {token}", List.of("api.example.com"));
            Map<String, Object> server = config.toMcpServer("ghp_secret123");

            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) server.get("headers");

            assertThat(headers.get("Authorization")).isEqualTo("Bearer ghp_secret123");
            assertThat(headers.entrySet().toString()).contains("Bearer ***");
            assertThat(headers.entrySet().toString()).doesNotContain("ghp_secret123");
            assertThat(headers.values().toString()).contains("Bearer ***");
            assertThat(headers.values().toString()).doesNotContain("ghp_secret123");
        }

        @Test
        @DisplayName("MCPサーバーマップのentrySet文字列化でもトークンをマスクする")
        void masksAuthorizationInServerEntrySetToString() {
            GithubMcpConfig config = new GithubMcpConfig(
                "http", "https://api.example.com/",
                List.of("*"), Map.of(),
                "Authorization", "Bearer {token}", List.of("api.example.com"));
            Map<String, Object> server = config.toMcpServer("ghp_secret456");

            String entrySetString = server.entrySet().toString();

            assertThat(entrySetString).contains("Bearer ***");
            assertThat(entrySetString).doesNotContain("ghp_secret456");
        }
    }

    @Nested
    @DisplayName("McpServerConfig")
    class McpServerConfigTests {

        @Test
        @DisplayName("toMapは型安全な表現からMapに変換する")
        void toMapConvertsCorrectly() {
            var mcpConfig = new GithubMcpConfig.McpServerConfig(
                "http", "https://api.example.com/",
                List.of("tool1", "tool2"),
                Map.of("X-Header", "value"));
            Map<String, Object> map = mcpConfig.toMap();

            assertThat(map).containsEntry("type", "http");
            assertThat(map).containsEntry("url", "https://api.example.com/");
            assertThat(map).containsEntry("tools", List.of("tool1", "tool2"));
            assertThat(map).containsEntry("headers", Map.of("X-Header", "value"));
        }

        @Test
        @DisplayName("toMapは不変Mapを返す")
        void toMapReturnsImmutableMap() {
            var mcpConfig = new GithubMcpConfig.McpServerConfig(
                "http", "https://api.example.com/",
                List.of("*"), Map.of());
            Map<String, Object> map = mcpConfig.toMap();
            assertThat(map).isUnmodifiable();
        }

        @Test
        @DisplayName("toStringでAuthorizationヘッダーがマスクされる")
        void toStringMasksAuthorizationHeader() {
            var mcpConfig = new GithubMcpConfig.McpServerConfig(
                "http", "https://api.example.com/",
                List.of("*"),
                Map.of("Authorization", "Bearer ghp_secret123", "X-Custom", "visible"));
            String str = mcpConfig.toString();

            assertThat(str).contains("Bearer ***");
            assertThat(str).doesNotContain("ghp_secret123");
            assertThat(str).contains("visible");
        }

        @Test
        @DisplayName("toStringでAuthorizationヘッダーなしの場合はそのまま出力される")
        void toStringWithoutAuthorizationHeader() {
            var mcpConfig = new GithubMcpConfig.McpServerConfig(
                "http", "https://api.example.com/",
                List.of("*"),
                Map.of("X-Custom", "value"));
            String str = mcpConfig.toString();

            assertThat(str).contains("X-Custom");
            assertThat(str).doesNotContain("Bearer ***");
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
            assertThat(servers.orElseThrow()).containsKey("github");
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
