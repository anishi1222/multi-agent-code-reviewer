package dev.logicojp.reviewer.application.agent;

import dev.logicojp.reviewer.application.port.inbound.LoadAgentPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.AgentSourceDirectory;

import java.util.List;
import java.util.Optional;

/// Application use-case: load and validate agent definitions from disk.
///
/// Implements {@link LoadAgentPort}. Actual file I/O is fully delegated to the
/// injected {@link AgentLoader} strategy — this class contains no filesystem or
/// YAML-parsing logic. The infrastructure layer provides the strategy as a lambda
/// that wraps the brownfield {@code AgentConfigLoader} during the coexistence period.
///
/// No framework annotations — DI is handled by the infrastructure configuration layer.
///
/// Application layer: imports only {@code application.port.*}, {@code domain.*},
/// {@code java.*} — no {@code infrastructure.*}, no brownfield packages.
public final class LoadAgentUseCase implements LoadAgentPort {

    /// Functional strategy for loading agents from a list of directories.
    ///
    /// Implemented by the infrastructure layer (e.g. as a lambda wrapping
    /// the brownfield {@code AgentConfigLoader}).
    @FunctionalInterface
    public interface AgentLoader {
        /// Loads all agents found in the given directories.
        ///
        /// @param directories directories to search, each paired with the provenance of its
        ///                    contents (ADR-0007 D1)
        /// @return list of validated domain {@link AgentConfig} instances
        List<AgentConfig> load(List<AgentSourceDirectory> directories);
    }

    private final AgentLoader agentLoader;

    public LoadAgentUseCase(AgentLoader agentLoader) {
        this.agentLoader = agentLoader;
    }

    /// {@inheritDoc}
    @Override
    public List<AgentConfig> loadAll(List<AgentSourceDirectory> directories) {
        if (directories == null || directories.isEmpty()) {
            return List.of();
        }
        return agentLoader.load(directories);
    }

    /// {@inheritDoc}
    @Override
    public Optional<AgentConfig> loadByName(String name, List<AgentSourceDirectory> directories) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        if (directories == null || directories.isEmpty()) {
            return Optional.empty();
        }
        return agentLoader.load(directories)
            .stream()
            .filter(cfg -> name.equalsIgnoreCase(cfg.name()))
            .findFirst();
    }
}
