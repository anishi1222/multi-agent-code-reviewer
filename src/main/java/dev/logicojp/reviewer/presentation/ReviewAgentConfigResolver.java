package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.LoadAgentPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// Resolves agent configs from CLI options using the {@link LoadAgentPort}.
///
/// Default agent directories are managed inside the port adapter.
/// Only additional dirs from CLI are passed here.
@Singleton
public class ReviewAgentConfigResolver {

    public record AgentResolution(List<Path> agentDirectories, Map<String, AgentConfig> agentConfigs) {}

    private final LoadAgentPort loadAgentPort;

    @Inject
    public ReviewAgentConfigResolver(LoadAgentPort loadAgentPort) {
        this.loadAgentPort = loadAgentPort;
    }

    public AgentResolution resolve(ReviewOptions options) {
        List<Path> additionalDirs = options.additionalAgentDirs();
        List<AgentConfig> agents = loadAgentPort.loadAll(additionalDirs);

        List<AgentConfig> filtered = filterBySelection(agents, options.agents());
        Map<String, AgentConfig> adjusted = applyReviewModelOverride(toMap(filtered), options.reviewModel());
        adjusted = applyRubberDuckOverrides(adjusted, options);
        return new AgentResolution(List.copyOf(additionalDirs), adjusted);
    }

    private List<AgentConfig> filterBySelection(List<AgentConfig> agents, ReviewAgentSelection selection) {
        return switch (selection) {
            case ReviewAgentSelection.All() -> agents;
            case ReviewAgentSelection.Named(List<String> names) -> agents.stream()
                .filter(a -> names.contains(a.name()))
                .toList();
        };
    }

    private Map<String, AgentConfig> toMap(List<AgentConfig> agents) {
        return agents.stream().collect(
            Collectors.toMap(AgentConfig::name, a -> a, (a, b) -> b, LinkedHashMap::new));
    }

    private Map<String, AgentConfig> applyReviewModelOverride(Map<String, AgentConfig> agentConfigs, String reviewModel) {
        if (reviewModel == null) {
            return agentConfigs;
        }
        Map<String, AgentConfig> adjusted = new LinkedHashMap<>();
        for (Map.Entry<String, AgentConfig> entry : agentConfigs.entrySet()) {
            adjusted.put(entry.getKey(), entry.getValue().withModel(reviewModel));
        }
        return adjusted;
    }

    private Map<String, AgentConfig> applyRubberDuckOverrides(
            Map<String, AgentConfig> agentConfigs, ReviewOptions options) {
        boolean rubberDuck = options.rubberDuck();
        String peerModel = options.peerModel();
        int dialogueRounds = options.dialogueRounds();
        if (!rubberDuck && peerModel == null && dialogueRounds <= 0) {
            return agentConfigs;
        }
        Map<String, AgentConfig> adjusted = new LinkedHashMap<>();
        for (Map.Entry<String, AgentConfig> entry : agentConfigs.entrySet()) {
            AgentConfig config = entry.getValue();
            if (rubberDuck) {
                config = config.withRubberDuckEnabled(true);
            }
            if (peerModel != null) {
                config = config.withPeerModel(peerModel);
            }
            if (dialogueRounds > 0) {
                config = config.withDialogueRounds(dialogueRounds);
            }
            adjusted.put(entry.getKey(), config);
        }
        return adjusted;
    }
}
