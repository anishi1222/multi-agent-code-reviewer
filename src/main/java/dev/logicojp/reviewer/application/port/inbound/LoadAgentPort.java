package dev.logicojp.reviewer.application.port.inbound;

import dev.logicojp.reviewer.domain.agent.AgentConfig;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/// Inbound port: load and validate agent definitions from disk.
///
/// Implementer: {@code application.agent.LoadAgentUseCase}
/// Callers:     {@code presentation.command.ReviewCommand},
///              {@code presentation.command.ListAgentsCommand}
///
/// Covers behaviors: AGT-01–AGT-13
public interface LoadAgentPort {

    /// Load and validate all agents found in the given directories.
    ///
    /// @param directories directories to search for YAML agent definitions
    /// @return all successfully loaded and validated agent configs
    List<AgentConfig> loadAll(List<Path> directories);

    /// Load a single agent by name from the given directories.
    ///
    /// @param name        agent name (case-insensitive, without extension)
    /// @param directories directories to search
    /// @return the agent config, or empty if not found
    Optional<AgentConfig> loadByName(String name, List<Path> directories);
}
