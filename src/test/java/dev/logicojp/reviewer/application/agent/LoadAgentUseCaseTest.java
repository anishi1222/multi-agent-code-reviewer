package dev.logicojp.reviewer.application.agent;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.AgentSourceDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoadAgentUseCase")
class LoadAgentUseCaseTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("ロード済みエージェントを一覧できる")
    void listAvailableAgentsReturnsAgentNames() {
        Path configured = tempDir.resolve("agents");
        AgentConfig security = agent("security", "security agent");
        AgentConfig quality = agent("quality", "quality agent");
        AtomicReference<List<AgentSourceDirectory>> capturedDirectories = new AtomicReference<>();
        LoadAgentUseCase useCase = new LoadAgentUseCase(directories -> {
            capturedDirectories.set(directories);
            return List.of(security, quality);
        });

        List<AgentConfig> all = useCase.loadAll(List.of(AgentSourceDirectory.userSupplied(configured)));

        assertThat(capturedDirectories.get())
            .as("the use case must forward the directory *with* its provenance intact — "
                + "flattening back to a bare Path is the defect ADR-0007 D1 removes")
            .containsExactly(AgentSourceDirectory.userSupplied(configured));
        assertThat(all).extracting(AgentConfig::name).containsExactly("security", "quality");
    }

    @Test
    @DisplayName("名前でロード済みエージェントを検索できる")
    void loadByNameReturnsMatchingAgentIgnoringCase() {
        Path configured = tempDir.resolve("agents");
        AgentConfig security = agent("security", "security agent");
        LoadAgentUseCase useCase = new LoadAgentUseCase(_ -> List.of(security));

        assertThat(useCase.loadByName("SECURITY", List.of(AgentSourceDirectory.userSupplied(configured)))).contains(security);
        assertThat(useCase.loadByName("missing", List.of(AgentSourceDirectory.userSupplied(configured)))).isEmpty();
    }

    @Test
    @DisplayName("ディレクトリ未指定時はloaderを呼ばずにemptyを返す")
    void emptyDirectoriesReturnEmptyWithoutLoading() {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        LoadAgentUseCase useCase = new LoadAgentUseCase(_ -> {
            called.set(true);
            return List.of(agent("security", "security agent"));
        });

        assertThat(useCase.loadAll(List.of())).isEmpty();
        assertThat(useCase.loadByName("security", null)).isEmpty();
        assertThat(called).hasValue(false);
    }

    // not ported: configured/additional directory merging belongs to infrastructure configuration, not LoadAgentUseCase.

    private static AgentConfig agent(String name, String displayName) {
        return new AgentConfig(name, displayName, "model", "system", "instruction", null, List.of(), List.of());
    }
}
