package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.agent.LoadAgentUseCase;
import dev.logicojp.reviewer.application.port.inbound.LoadAgentPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.AgentSourceDirectory;
import io.micronaut.context.env.Environment;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// Verifies acceptance criterion **B1**: `LoadAgentUseCase` must be reachable through DI and
/// actually exercised end-to-end, rather than being un-instantiable dead code.
///
/// The distinction matters. Reading `ApplicationPortFactory` only shows that wiring code was
/// *written*; it does not show that Micronaut resolves the bean, that the lambda bridging to
/// `AgentConfigLoader` works, or that agents load through the use case rather than through a
/// direct call to the loader. This test resolves the port from a real `ApplicationContext` and
/// drives it against the repository's own `agents/` directory, so all three are proven.
@MicronautTest(environments = Environment.CLI)
@DisplayName("LoadAgentPort のDI配線 (受け入れ基準B1)")
class LoadAgentPortWiringTest {

    private static final Path AGENTS_DIR = Path.of("agents");

    @Inject
    LoadAgentPort loadAgentPort;

    @Test
    @DisplayName("DIコンテナがLoadAgentPortをLoadAgentUseCaseとして解決する")
    void diResolvesPortBackedByUseCase() {
        // Proves the bean is instantiable: if AgentLoader had no binding, context startup would
        // fail rather than reach this assertion.
        assertThat(loadAgentPort)
            .as("LoadAgentPort must be resolvable from the Micronaut context")
            .isNotNull()
            .isInstanceOf(LoadAgentUseCase.class);
    }

    @Test
    @DisplayName("ユースケース経由で実際のエージェント定義を読み込む")
    void loadsRealAgentDefinitionsThroughTheUseCase() {
        List<AgentConfig> agents = loadAgentPort.loadAll(List.of(AgentSourceDirectory.userSupplied(AGENTS_DIR)));

        // Exercises the full path: use case -> injected AgentLoader lambda -> AgentConfigLoader
        // -> frontmatter parsing -> domain AgentConfig.
        assertThat(agents)
            .as("agents/ contains agent definitions, so the use case must return them")
            .isNotEmpty();
        assertThat(agents).extracting(AgentConfig::name).contains("security");
    }

    @Test
    @DisplayName("名前を指定してエージェントを取得できる")
    void findsAgentByName() {
        Optional<AgentConfig> agent = loadAgentPort.loadByName("security", List.of(AgentSourceDirectory.userSupplied(AGENTS_DIR)));

        assertThat(agent).isPresent();
        assertThat(agent.orElseThrow().name()).isEqualTo("security");
    }

    @Test
    @DisplayName("存在しないエージェント名は空を返す")
    void returnsEmptyForUnknownAgentName() {
        assertThat(loadAgentPort.loadByName("no-such-agent", List.of(AgentSourceDirectory.userSupplied(AGENTS_DIR)))).isEmpty();
    }

    @Test
    @DisplayName("追加ディレクトリなしでも設定済み既定ディレクトリから読み込む")
    void loadsConfiguredDefaultsWhenNoAdditionalDirectoriesGiven() {
        assertThat(loadAgentPort.loadAll(List.of()))
            .as("empty add-ons must not bypass reviewer.agents.directories")
            .extracting(AgentConfig::name)
            .contains("security");
        assertThat(loadAgentPort.loadByName("security", List.of()))
            .as("name lookup must use the same configured defaults")
            .isPresent();
    }
}
