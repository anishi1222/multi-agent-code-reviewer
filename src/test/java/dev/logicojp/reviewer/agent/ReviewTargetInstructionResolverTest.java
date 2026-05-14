package dev.logicojp.reviewer.agent;

import com.github.copilot.sdk.json.McpHttpServerConfig;
import com.github.copilot.sdk.json.McpServerConfig;
import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewTargetInstructionResolver")
class ReviewTargetInstructionResolverTest {

    @TempDir
    Path tempDir;

    private AgentConfig agentConfig() {
        return AgentConfig.builder()
            .name("security")
            .displayName("Security")
            .instruction("Review ${repository}")
            .build();
    }

    @Test
    @DisplayName("GitHubターゲットではリモート指示とMCP設定を返す")
    void resolvesGithubInstructionAndMcpServers() {
        var resolver = new ReviewTargetInstructionResolver(
            agentConfig(),
            new LocalFileConfig(),
            () -> {
            }
        );
        Map<String, McpServerConfig> mcp = Map.of(
            "github",
            new McpHttpServerConfig().setUrl("https://api.githubcopilot.com/mcp/")
        );

        var resolved = resolver.resolve(ReviewTarget.gitHub("owner/repo"), null, mcp);

        assertThat(resolved.instruction()).contains("owner/repo");
        assertThat(resolved.localSourceContent()).isNull();
        assertThat(resolved.mcpServers()).isEqualTo(mcp);
    }

    @Test
    @DisplayName("ローカルターゲットではキャッシュ済みソースを優先して使う")
    void usesCachedLocalSourceWhenAvailable() {
        var computed = new AtomicBoolean(false);
        var resolver = new ReviewTargetInstructionResolver(
            agentConfig(),
            new LocalFileConfig(),
            () -> computed.set(true)
        );

        var resolved = resolver.resolve(ReviewTarget.local(tempDir), "CACHED", null);

        assertThat(resolved.instruction()).contains(tempDir.getFileName().toString());
        assertThat(resolved.localSourceContent()).isEqualTo("CACHED");
        assertThat(resolved.mcpServers()).isNull();
        assertThat(computed).isFalse();
    }

    @Test
    @DisplayName("ローカルターゲットでキャッシュなしならソースを収集してlistenerを呼ぶ")
    void collectsLocalSourceWhenCacheMissing() throws IOException {
        Files.writeString(tempDir.resolve("Main.java"), "class Main {}\n");

        var computed = new AtomicBoolean(false);
        var resolver = new ReviewTargetInstructionResolver(
            agentConfig(),
            new LocalFileConfig(
                1024 * 1024,
                2 * 1024 * 1024,
                List.of(),
                List.of("java"),
                List.of(".env", "secret", "key", "token", "password", "credential"),
                List.of("pem", "key", "p12", "jks", "keystore")
            ),
            () -> computed.set(true)
        );

        var resolved = resolver.resolve(ReviewTarget.local(tempDir), null, null);

        assertThat(resolved.localSourceContent()).contains("### Main.java");
        assertThat(resolved.localSourceContent()).contains("class Main {}");
        assertThat(computed).isTrue();
    }
}