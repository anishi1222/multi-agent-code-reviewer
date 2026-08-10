package dev.logicojp.reviewer.application.agent;

import dev.logicojp.reviewer.application.port.inbound.LoadAgentPort;
import dev.logicojp.reviewer.application.port.outbound.LoadAgentDefinitionsPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.AgentSourceDirectory;

import java.util.List;
import java.util.Optional;

/// Application use-case: load and validate agent definitions from disk.
///
/// Implements {@link LoadAgentPort}. Actual file I/O is fully delegated to the
/// injected {@link LoadAgentDefinitionsPort} — this class contains no filesystem or
/// YAML-parsing logic.
///
/// No framework annotations — DI is handled by the infrastructure configuration layer.
///
/// Application layer: imports only {@code application.port.*}, {@code domain.*},
/// {@code java.*} — no {@code infrastructure.*}, no brownfield packages.
public final class LoadAgentUseCase implements LoadAgentPort {

    private final LoadAgentDefinitionsPort agentLoader;

    public LoadAgentUseCase(LoadAgentDefinitionsPort agentLoader) {
        this.agentLoader = agentLoader;
    }

    /// {@inheritDoc}
    @Override
    public List<AgentConfig> loadAll(List<AgentSourceDirectory> directories) {
        return agentLoader.load(normalizeAdditionalDirectories(directories));
    }

    /// {@inheritDoc}
    @Override
    public Optional<AgentConfig> loadByName(String name, List<AgentSourceDirectory> directories) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return loadAll(directories)
            .stream()
            .filter(cfg -> name.equalsIgnoreCase(cfg.name()))
            .findFirst();
    }

    private List<AgentSourceDirectory> normalizeAdditionalDirectories(
            List<AgentSourceDirectory> directories) {
        return directories == null ? List.of() : List.copyOf(directories);
    }
}
