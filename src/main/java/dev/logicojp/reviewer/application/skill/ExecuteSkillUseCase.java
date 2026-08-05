package dev.logicojp.reviewer.application.skill;

import dev.logicojp.reviewer.application.port.inbound.ExecuteSkillPort;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import dev.logicojp.reviewer.domain.skill.SkillResult;

import java.util.List;
import java.util.Map;

/// Application use-case: execute a named skill with given parameters.
///
/// Implements {@link ExecuteSkillPort}. This implementation is a thin shell for the
/// Phase 3 milestone:
///
/// <ul>
///   <li>{@link #listSkills()} returns an empty list — skills are registered
///       per-agent when the orchestrator loads agents (T009).</li>
///   <li>{@link #execute(String, Map)} returns a failure result with a clear
///       {@code TODO} message — actual execution via {@code RunCopilotSessionPort}
///       is deferred to T010.</li>
/// </ul>
///
/// No framework annotations — DI is handled by the infrastructure configuration layer.
///
/// Application layer: imports only {@code application.port.*}, {@code domain.*},
/// {@code java.*} — no {@code infrastructure.*}.
public final class ExecuteSkillUseCase implements ExecuteSkillPort {

    /// {@inheritDoc}
    ///
    /// @implNote Stub — skill execution via {@code RunCopilotSessionPort}
    ///           will be implemented in T010.
    @Override
    public SkillResult execute(String skillId, Map<String, String> parameters) {
        // TODO T010: implement skill execution via RunCopilotSessionPort
        return SkillResult.failure(
            skillId,
            "Skill execution not yet implemented — pending T010"
        );
    }

    /// {@inheritDoc}
    ///
    /// @implNote Returns an empty list — skills are populated per-agent
    ///           by the review orchestrator (T009).
    @Override
    public List<SkillDefinition> listSkills() {
        return List.of();
    }
}
