package dev.logicojp.reviewer.infrastructure.config;

import dev.logicojp.reviewer.application.port.outbound.McpServerSpec;
import dev.logicojp.reviewer.shared.SensitiveHeaderMasking;
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
        @DisplayName("トークン付きでMcpServerSpecを生成する")
        void generatesMcpServerWithToken() {
            GithubMcpConfig config = new GithubMcpConfig(
                "http", "https://api.example.com/mcp/",
                List.of("tool1"), Map.of(), "Authorization", "Bearer {token}", List.of("api.example.com"));
            McpServerSpec server = config.toMcpServerSpec("my-token");

            assertThat(server.name()).isEqualTo("github");
            assertThat(server.url()).isEqualTo("https://api.example.com/mcp/");
            assertThat(server.tools()).containsExactly("tool1");
            assertThat(server.headers()).containsEntry("Authorization", "Bearer my-token");
        }

        @Test
        @DisplayName("トークンがnullの場合はAuthorizationヘッダーを追加しない")
        void nullTokenSkipsAuthHeader() {
            GithubMcpConfig config = new GithubMcpConfig(null, null, null, null, null, null);
            McpServerSpec server = config.toMcpServerSpec(null);

            assertThat(server.headers()).doesNotContainKey("Authorization");
        }

        @Test
        @DisplayName("空白トークンの場合はAuthorizationヘッダーを追加しない")
        void blankTokenSkipsAuthHeader() {
            GithubMcpConfig config = new GithubMcpConfig(null, null, null, null, null, null);
            McpServerSpec server = config.toMcpServerSpec("  ");

            assertThat(server.headers()).doesNotContainKey("Authorization");
        }

        @Test
        @DisplayName("既存ヘッダーとAuthorizationヘッダーが結合される")
        void mergesExistingHeaders() {
            GithubMcpConfig config = new GithubMcpConfig(
                "http", "https://api.example.com/",
                List.of("*"), Map.of("X-Custom", "value"),
                "Authorization", "Bearer {token}", List.of("api.example.com"));
            McpServerSpec server = config.toMcpServerSpec("tok");

            assertThat(server.headers()).containsEntry("X-Custom", "value");
            assertThat(server.headers()).containsEntry("Authorization", "Bearer tok");
        }

        @Test
        @DisplayName("{token}プレースホルダーのみがサポートされている")
        void onlySingleBraceTokenPlaceholderSupported() {
            GithubMcpConfig config = new GithubMcpConfig(
                "http", "https://api.example.com/",
                List.of("*"), Map.of(),
                "Authorization", "token ${token}", List.of("api.example.com"));
            McpServerSpec server = config.toMcpServerSpec("abc123");

            // ${token} contains {token} which gets replaced, leaving the $ prefix
            assertThat(server.headers()).containsEntry("Authorization", "token $abc123");
        }

        @Test
        @DisplayName("ヘッダーマップは生値を保持する（マスクはログシンクの責務: ADR-0007 D5/D6）")
        void exposesRawAuthorizationBecauseMaskingMovedToTheSink() {
            GithubMcpConfig config = new GithubMcpConfig(
                "http", "https://api.example.com/",
                List.of("*"), Map.of("X-Custom", "value"),
                "Authorization", "Bearer {token}", List.of("api.example.com"));
            McpServerSpec server = config.toMcpServerSpec("ghp_secret123");

            // Raw value remains accessible for actual HTTP requests
            assertThat(server.headers().get("Authorization")).isEqualTo("Bearer ghp_secret123");
            String rawToken = "ghp" + "_secret123";

            // ADR-0007 D5 removed object-level masking from this boundary. It only ever guarded
            // toString(); get()/entrySet() returned raw by design; it was lost on any copy; and the
            // SDK stores this map with a plain field write and overrides no toString() — so the one
            // guarded surface was unreachable past the boundary. Masking is the log sink's job now
            // (D6). That the secret still never reaches a log is proven, for these exact inputs, by
            // SensitiveHeaderMaskingSinkCanaryTest — not by this record.
            assertThat(server.headers().toString()).contains(rawToken);
        }
    }

    @Nested
    @DisplayName("buildMcpServer")
    class BuildMcpServersTests {

        @Test
        @DisplayName("トークンと設定が揃っている場合はMCPサーバー設定を返す")
        void returnsMcpServersWhenInputsAreValid() {
            GithubMcpConfig config = new GithubMcpConfig(null, null, null, null, null, null);

            var servers = GithubMcpConfig.buildMcpServer("ghp_token", config);

            assertThat(servers).isPresent();
            McpServerSpec server = servers.orElseThrow();
            assertThat(server.name()).isEqualTo("github");
            assertThat(server.url()).isEqualTo("https://api.githubcopilot.com/mcp/");
        }

        @Test
        @DisplayName("トークンまたは設定が不正な場合はemptyを返す")
        void returnsEmptyWhenInputsAreInvalid() {
            assertThat(GithubMcpConfig.buildMcpServer("", new GithubMcpConfig(null, null, null, null, null, null)))
                .isEmpty();
            assertThat(GithubMcpConfig.buildMcpServer("ghp_token", null)).isEmpty();
        }
    }
}
