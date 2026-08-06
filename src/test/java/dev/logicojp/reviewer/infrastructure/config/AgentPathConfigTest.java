package dev.logicojp.reviewer.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentPathConfig")
class AgentPathConfigTest {

    @Test
    @DisplayName("nullの場合はデフォルトディレクトリを使用する")
    void nullUsesDefaults() {
        AgentPathConfig config = new AgentPathConfig(null);

        assertThat(config.directories()).containsExactly("./agents", "./.github/agents");
    }

    @Test
    @DisplayName("指定値がある場合はその値を使用する")
    void customDirectoriesAreUsed() {
        AgentPathConfig config = new AgentPathConfig(List.of("/tmp/agents"));

        assertThat(config.directories()).containsExactly("/tmp/agents");
    }
}
