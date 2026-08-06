package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.LoadAgentPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.AgentSourceDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewAgentConfigResolver")
class ReviewAgentConfigResolverTest {

    @Test
    @DisplayName("agent ディレクトリと設定を解決できる")
    void resolvesAgentDirectoriesAndConfigs() {
        var agents = List.of(agentConfig("code-quality", "model-a"));
        var loadAgentPort = new StubLoadAgentPort(agents);
        var resolver = new ReviewAgentConfigResolver(loadAgentPort);
        var additionalDirs = List.of(Path.of("agents"), Path.of(".github/agents"));

        ReviewAgentConfigResolver.AgentResolution result = resolver.resolve(parsedOptions(null, additionalDirs));

        assertThat(loadAgentPort.loadedDirectories())
            .as("`--agents-dir` values originate from argv, so the resolver must tag them "
                + "USER_SUPPLIED; tagging them REPOSITORY_SUPPLIED would wrongly apply the "
                + "strict profile to operator input")
            .containsExactlyElementsOf(AgentSourceDirectory.allUserSupplied(additionalDirs));
        assertThat(result.agentDirectories()).containsExactlyElementsOf(additionalDirs);
        assertThat(result.agentConfigs()).containsKey("code-quality");
        assertThat(result.agentConfigs().get("code-quality").model()).isEqualTo("model-a");
    }

    @Test
    @DisplayName("review-model 指定時は全 agent に model override を適用する")
    void appliesReviewModelOverrideToAllAgents() {
        var original = List.of(agentConfig("a", "base-1"), agentConfig("b", "base-2"));
        var resolver = new ReviewAgentConfigResolver(new StubLoadAgentPort(original));

        ReviewAgentConfigResolver.AgentResolution result = resolver.resolve(parsedOptions("override-model"));

        assertThat(result.agentConfigs().get("a").model()).isEqualTo("override-model");
        assertThat(result.agentConfigs().get("b").model()).isEqualTo("override-model");
        assertThat(original.get(0).model()).isEqualTo("base-1");
        assertThat(original.get(1).model()).isEqualTo("base-2");
    }

    // removed: IOException wrapping moved behind LoadAgentPort infrastructure adapter; presentation only depends on the port.

    @Test
    @DisplayName("peer-model 指定時は rubber-duck 設定とともに agent に適用する")
    void appliesRubberDuckWhenPeerModelIsSpecified() {
        var original = List.of(agentConfig("a", "base-1"));
        var resolver = new ReviewAgentConfigResolver(new StubLoadAgentPort(original));

        ReviewAgentConfigResolver.AgentResolution result = resolver.resolve(
            parsedOptions(null, true, 0, "peer-model-x")
        );

        AgentConfig config = result.agentConfigs().get("a");
        assertThat(config.rubberDuckEnabled()).isTrue();
        assertThat(config.peerModel()).isEqualTo("peer-model-x");
    }

    @Test
    @DisplayName("dialogue-rounds 指定時は rubber-duck 設定とともに agent に適用する")
    void appliesRubberDuckWhenDialogueRoundsIsSpecified() {
        var original = List.of(agentConfig("a", "base-1"));
        var resolver = new ReviewAgentConfigResolver(new StubLoadAgentPort(original));

        ReviewAgentConfigResolver.AgentResolution result = resolver.resolve(
            parsedOptions(null, true, 3, null)
        );

        AgentConfig config = result.agentConfigs().get("a");
        assertThat(config.rubberDuckEnabled()).isTrue();
        assertThat(config.dialogueRounds()).isEqualTo(3);
    }

    private static ReviewOptions parsedOptions(String reviewModel) {
        return parsedOptions(reviewModel, List.of());
    }

    private static ReviewOptions parsedOptions(String reviewModel, List<Path> additionalAgentDirs) {
        return parsedOptions(reviewModel, additionalAgentDirs, false, 0, null);
    }

    private static ReviewOptions parsedOptions(String reviewModel,
                                               boolean rubberDuck,
                                               int dialogueRounds,
                                               String peerModel) {
        return parsedOptions(reviewModel, List.of(), rubberDuck, dialogueRounds, peerModel);
    }

    private static ReviewOptions parsedOptions(String reviewModel,
                                               List<Path> additionalAgentDirs,
                                               boolean rubberDuck,
                                               int dialogueRounds,
                                               String peerModel) {
        return ReviewOptions.builder()
            .target(new ReviewTargetSelection.Repository("owner/repo"))
            .agents(new ReviewAgentSelection.All())
            .outputDirectory(Path.of("./reports"))
            .additionalAgentDirs(additionalAgentDirs)
            .parallelism(4)
            .noSummary(false)
            .reviewModel(reviewModel)
            .rubberDuck(rubberDuck)
            .dialogueRounds(dialogueRounds)
            .peerModel(peerModel)
            .trustTarget(false)
            .build();
    }

    private static AgentConfig agentConfig(String name, String model) {
        return new AgentConfig(name, name, model, "prompt", "instruction", "", List.of(), List.of());
    }

    private static final class StubLoadAgentPort implements LoadAgentPort {
        private final List<AgentConfig> agents;
        private List<AgentSourceDirectory> loadedDirectories = List.of();

        StubLoadAgentPort(List<AgentConfig> agents) {
            this.agents = List.copyOf(agents);
        }

        @Override
        public List<AgentConfig> loadAll(List<AgentSourceDirectory> directories) {
            loadedDirectories = List.copyOf(directories);
            return agents;
        }

        @Override
        public Optional<AgentConfig> loadByName(String name, List<AgentSourceDirectory> directories) {
            return agents.stream().filter(agent -> agent.name().equals(name)).findFirst();
        }

        List<AgentSourceDirectory> loadedDirectories() {
            return loadedDirectories;
        }
    }
}
