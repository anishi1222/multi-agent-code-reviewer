package dev.logicojp.reviewer.infrastructure.parsing;

import dev.logicojp.reviewer.application.port.outbound.LoadAgentDefinitionsPort;
import dev.logicojp.reviewer.application.port.outbound.ManageSkillCatalogPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.AgentSourceDirectory;
import dev.logicojp.reviewer.infrastructure.config.AgentPathConfig;
import dev.logicojp.reviewer.infrastructure.config.SkillConfig;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Provenance-aware outbound adapter over agent-definition filesystem loading.
@Singleton
public final class AgentDefinitionLoaderAdapter implements LoadAgentDefinitionsPort {

    private final List<AgentSourceDirectory> configuredDirectories;
    private final SkillConfig skillConfig;
    private final ManageSkillCatalogPort skillCatalog;

    public AgentDefinitionLoaderAdapter(
            AgentPathConfig agentPathConfig,
            SkillConfig skillConfig,
            ManageSkillCatalogPort skillCatalog) {
        List<String> configured = agentPathConfig.directories() != null
            ? agentPathConfig.directories()
            : List.of();
        this.configuredDirectories = configured.stream()
            .map(Path::of)
            .map(AgentSourceDirectory::repositorySupplied)
            .toList();
        this.skillConfig = skillConfig;
        this.skillCatalog = skillCatalog;
    }

    @Override
    public List<AgentConfig> load(List<AgentSourceDirectory> additionalDirectories) {
        List<AgentSourceDirectory> merged = new ArrayList<>(configuredDirectories);
        if (additionalDirectories != null) {
            merged.addAll(additionalDirectories);
        }
        try {
            var loader = AgentConfigLoader.builder(merged)
                .skillConfig(skillConfig)
                .build();
            AgentConfigLoader.AgentLoadReport report = loader.loadAllAgentsWithReport();
            skillCatalog.replaceAll(report.discoveredSkills());
            return List.copyOf(report.agents().values());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load agent definitions", e);
        }
    }
}
