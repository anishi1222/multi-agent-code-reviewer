package dev.logicojp.reviewer.application.port.outbound;

import dev.logicojp.reviewer.domain.skill.SkillDefinition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/// Outbound port for the canonical catalog of skills discovered by infrastructure.
///
/// Agent loading publishes one complete discovery snapshot. Skill listing and execution query
/// that same snapshot, so parsed skills cannot diverge from the executable inventory.
public interface ManageSkillCatalogPort {

    /// Atomically replaces the catalog with one complete discovery snapshot.
    void replaceAll(Collection<SkillDefinition> discoveredSkills);

    /// Finds a discovered skill by its stable ID.
    Optional<SkillDefinition> findById(String skillId);

    /// Lists the current discovery snapshot in discovery order.
    List<SkillDefinition> listAll();
}
