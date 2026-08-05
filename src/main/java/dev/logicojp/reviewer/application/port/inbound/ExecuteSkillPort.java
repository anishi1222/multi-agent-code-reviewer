package dev.logicojp.reviewer.application.port.inbound;

import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import dev.logicojp.reviewer.domain.skill.SkillResult;

import java.util.List;
import java.util.Map;

/// Inbound port: execute a named skill with given parameters.
///
/// Implementer: {@code application.skill.ExecuteSkillUseCase}
/// Callers:     {@code presentation.command.SkillCommand}
///
/// Covers behaviors: SKL-01–SKL-08
public interface ExecuteSkillPort {

    /// Execute a skill by ID with the given parameter map.
    ///
    /// @param skillId   the unique skill identifier
    /// @param parameters name-to-value map of skill parameters
    /// @return the skill execution result
    SkillResult execute(String skillId, Map<String, String> parameters);

    /// List all available skills.
    ///
    /// @return skill definitions in an unspecified order
    List<SkillDefinition> listSkills();
}
