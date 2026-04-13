package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.service.AgentService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
class ReviewAgentConfigResolver {

    public record AgentResolution(List<Path> agentDirectories, Map<String, AgentConfig> agentConfigs) {}

    @FunctionalInterface
    interface AgentDirectoryBuilder {
        List<Path> build(List<Path> additionalDirs);
    }

    @FunctionalInterface
    interface AgentConfigLoader {
        Map<String, AgentConfig> load(ReviewCommand.AgentSelection selection, List<Path> agentDirs) throws IOException;
    }

    private final AgentDirectoryBuilder directoryBuilder;
    private final AgentConfigLoader configLoader;

    @Inject
    public ReviewAgentConfigResolver(AgentService agentService) {
        this(
            agentService::buildAgentDirectories,
            (selection, agentDirs) -> switch (selection) {
                case ReviewCommand.AgentSelection.All() -> agentService.loadAllAgents(agentDirs);
                case ReviewCommand.AgentSelection.Named(List<String> names) -> agentService.loadAgents(agentDirs, names);
            }
        );
    }

    ReviewAgentConfigResolver(AgentDirectoryBuilder directoryBuilder, AgentConfigLoader configLoader) {
        this.directoryBuilder = directoryBuilder;
        this.configLoader = configLoader;
    }

    public AgentResolution resolve(ReviewCommand.ParsedOptions options) {
        List<Path> agentDirs = resolveAgentDirectories(options);
        Map<String, AgentConfig> loadedConfigs = loadAgentConfigs(options.agents(), agentDirs);
        Map<String, AgentConfig> adjustedConfigs = applyReviewModelOverride(loadedConfigs, options.reviewModel());
        adjustedConfigs = applyRubberDuckOverrides(adjustedConfigs, options);
        return new AgentResolution(List.copyOf(agentDirs), adjustedConfigs);
    }

    private List<Path> resolveAgentDirectories(ReviewCommand.ParsedOptions options) {
        return directoryBuilder.build(options.additionalAgentDirs());
    }

    private Map<String, AgentConfig> loadAgentConfigs(ReviewCommand.AgentSelection selection, List<Path> agentDirs) {
        try {
            return configLoader.load(selection, agentDirs);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load agent configurations", e);
        }
    }

    private Map<String, AgentConfig> applyReviewModelOverride(Map<String, AgentConfig> agentConfigs, String reviewModel) {
        if (!hasReviewModelOverride(reviewModel)) {
            return agentConfigs;
        }

        Map<String, AgentConfig> adjusted = new LinkedHashMap<>();
        for (Map.Entry<String, AgentConfig> entry : agentConfigs.entrySet()) {
            adjusted.put(entry.getKey(), entry.getValue().withModel(reviewModel));
        }
        return adjusted;
    }

    private boolean hasReviewModelOverride(String reviewModel) {
        return reviewModel != null;
    }

    private Map<String, AgentConfig> applyRubberDuckOverrides(
            Map<String, AgentConfig> agentConfigs, ReviewCommand.ParsedOptions options) {
        boolean rubberDuck = options.rubberDuck();
        String peerModel = options.peerModel();
        int dialogueRounds = options.dialogueRounds();
        boolean enableFromCli = rubberDuck || peerModel != null || dialogueRounds > 0;
        if (!rubberDuck && peerModel == null && dialogueRounds <= 0) {
            return agentConfigs;
        }
        Map<String, AgentConfig> adjusted = new LinkedHashMap<>();
        for (Map.Entry<String, AgentConfig> entry : agentConfigs.entrySet()) {
            AgentConfig config = entry.getValue();
            if (enableFromCli) {
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
