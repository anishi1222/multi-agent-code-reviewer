package dev.logicojp.reviewer.application.port.outbound;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.AgentSourceDirectory;

import java.util.List;

/// Outbound port for reading and validating agent definitions.
///
/// The directory provenance is part of the contract and must reach the leaf validator unchanged.
@FunctionalInterface
public interface LoadAgentDefinitionsPort {

    /// Loads all accepted definitions from the supplied directories.
    List<AgentConfig> load(List<AgentSourceDirectory> directories);
}
