package dev.logicojp.reviewer.application.port.inbound;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.AgentSourceDirectory;

import java.util.List;
import java.util.Optional;

/// Inbound port: load and validate agent definitions from disk.
///
/// Implementer: {@code application.agent.LoadAgentUseCase}
/// Callers:     {@code presentation.ReviewAgentConfigResolver},
///              {@code presentation.SkillExecutionPreparation},
///              {@code presentation.command.ListAgentsCommand}
///
/// Covers behaviors: AGT-01–AGT-13
///
/// ## Why the parameter is not `List<Path>` (ADR-0007 D1)
///
/// Until ADR-0007 these methods took a bare `List<Path>`. The composition root merged
/// two lineages with different trust properties — operator-named directories from
/// `--agents-dir`, and directories discovered under the repository being reviewed —
/// into that single list. Provenance was destroyed at the merge, so no validator
/// downstream could apply a different limit to repository-controlled content. That is
/// the structural cause of SEC-H2, and the reason the stricter untrusted limits
/// declared in the codebase were never reachable (SEC-H1).
///
/// {@link AgentSourceDirectory} makes the distinction survive the call.
public interface LoadAgentPort {

    /// Load and validate all agents found in the given directories.
    ///
    /// Definitions that violate the policy for their directory's trust level are
    /// rejected individually; the remaining agents still load (ADR-0007 D4).
    ///
    /// @param directories directories to search, each paired with the provenance of its contents
    /// @return all successfully loaded and validated agent configs
    List<AgentConfig> loadAll(List<AgentSourceDirectory> directories);

    /// Load a single agent by name from the given directories.
    ///
    /// @param name        agent name (case-insensitive, without extension)
    /// @param directories directories to search, each paired with the provenance of its contents
    /// @return the agent config, or empty if not found
    Optional<AgentConfig> loadByName(String name, List<AgentSourceDirectory> directories);
}
