package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReviewAgentConfigResolver")
class ReviewAgentConfigResolverTest {

    @Test
    @DisplayName("agent ディレクトリと設定を解決できる")
    void resolvesAgentDirectoriesAndConfigs() {
        var resolver = new ReviewAgentConfigResolver(
            additionalDirs -> List.of(Path.of("agents"), Path.of(".github/agents")),
            (selection, dirs) -> Map.of("code-quality", agentConfig("code-quality", "model-a"))
        );

        ReviewAgentConfigResolver.AgentResolution result = resolver.resolve(parsedOptions(null));

        assertThat(result.agentDirectories())
            .containsExactly(Path.of("agents"), Path.of(".github/agents"));
        assertThat(result.agentConfigs()).containsKey("code-quality");
        assertThat(result.agentConfigs().get("code-quality").model()).isEqualTo("model-a");
    }

    @Test
    @DisplayName("review-model 指定時は全 agent に model override を適用する")
    void appliesReviewModelOverrideToAllAgents() {
        Map<String, AgentConfig> original = new LinkedHashMap<>();
        original.put("a", agentConfig("a", "base-1"));
        original.put("b", agentConfig("b", "base-2"));

        var resolver = new ReviewAgentConfigResolver(
            additionalDirs -> List.of(Path.of("agents")),
            (selection, dirs) -> original
        );

        ReviewAgentConfigResolver.AgentResolution result = resolver.resolve(parsedOptions("override-model"));

        assertThat(result.agentConfigs().get("a").model()).isEqualTo("override-model");
        assertThat(result.agentConfigs().get("b").model()).isEqualTo("override-model");
        assertThat(original.get("a").model()).isEqualTo("base-1");
        assertThat(original.get("b").model()).isEqualTo("base-2");
    }

    @Test
    @DisplayName("読み込み時の IOException は UncheckedIOException に変換する")
    void wrapsIoExceptionAsUncheckedIoException() {
        var resolver = new ReviewAgentConfigResolver(
            additionalDirs -> List.of(Path.of("agents")),
            (selection, dirs) -> {
                throw new IOException("boom");
            }
        );

        assertThatThrownBy(() -> resolver.resolve(parsedOptions(null)))
            .isInstanceOf(UncheckedIOException.class)
            .hasMessageContaining("Failed to load agent configurations");
    }

    private static ReviewCommand.ParsedOptions parsedOptions(String reviewModel) {
        return new ReviewCommand.ParsedOptions(
            new ReviewCommand.TargetSelection.Repository("owner/repo"),
            new ReviewCommand.AgentSelection.All(),
            Path.of("./reports"),
            List.of(),
            null,
            4,
            false,
            reviewModel,
            null,
            null,
            null,
            false
        );
    }

    private static AgentConfig agentConfig(String name, String model) {
        return new AgentConfig(name, name, model, "prompt", "instruction", "", List.of(), List.of());
    }
}
