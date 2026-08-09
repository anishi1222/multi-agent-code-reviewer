package dev.logicojp.reviewer.infrastructure.parsing;

import dev.logicojp.reviewer.application.port.outbound.ManageSkillCatalogPort;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/// Infrastructure adapter holding the canonical discovered-skill snapshot.
@Singleton
public class SkillRegistry implements ManageSkillCatalogPort {

    private static final Logger logger = LoggerFactory.getLogger(SkillRegistry.class);

    private final AtomicReference<Map<String, SkillDefinition>> skills =
        new AtomicReference<>(Map.of());

    @Override
    public void replaceAll(Collection<SkillDefinition> discoveredSkills) {
        Map<String, SkillDefinition> replacement = snapshot(discoveredSkills);
        skills.set(replacement);
        logger.info("Replaced skill catalog with {} discovered skill(s)", replacement.size());
    }

    @Override
    public Optional<SkillDefinition> findById(String skillId) {
        return Optional.ofNullable(skills.get().get(skillId));
    }

    @Override
    public List<SkillDefinition> listAll() {
        return List.copyOf(skills.get().values());
    }

    /// Registers a skill definition.
    public void register(SkillDefinition skill) {
        Objects.requireNonNull(skill, "skill");
        skills.updateAndGet(current -> {
            Map<String, SkillDefinition> updated = new LinkedHashMap<>(current);
            updated.put(skill.id(), skill);
            return immutable(updated);
        });
        logger.info("Registered skill: {} ({})", skill.id(), skill.name());
    }

    /// Registers multiple skill definitions.
    public void registerAll(Collection<SkillDefinition> skillDefinitions) {
        Objects.requireNonNull(skillDefinitions, "skillDefinitions");
        skills.updateAndGet(current -> {
            Map<String, SkillDefinition> updated = new LinkedHashMap<>(current);
            for (SkillDefinition skill : skillDefinitions) {
                Objects.requireNonNull(skill, "skill");
                updated.put(skill.id(), skill);
            }
            return immutable(updated);
        });
        logger.info("Registered {} skill(s)", skillDefinitions.size());
    }

    /// Gets a skill by ID.
    public Optional<SkillDefinition> get(String skillId) {
        return findById(skillId);
    }

    /// Gets all registered skills.
    public List<SkillDefinition> getAll() {
        return listAll();
    }

    /// Gets all skill IDs.
    Set<String> getSkillIds() {
        return Set.copyOf(skills.get().keySet());
    }

    /// Checks if a skill is registered.
    boolean hasSkill(String skillId) {
        return skills.get().containsKey(skillId);
    }

    /// Removes a skill by ID.
    void unregister(String skillId) {
        skills.updateAndGet(current -> {
            Map<String, SkillDefinition> updated = new LinkedHashMap<>(current);
            updated.remove(skillId);
            return immutable(updated);
        });
        logger.info("Unregistered skill: {}", skillId);
    }

    /// Clears all registered skills.
    void clearAll() {
        skills.set(Map.of());
        logger.info("Cleared all skills from registry");
    }

    private static Map<String, SkillDefinition> snapshot(
            Collection<SkillDefinition> skillDefinitions) {
        Objects.requireNonNull(skillDefinitions, "skillDefinitions");
        Map<String, SkillDefinition> discovered = new LinkedHashMap<>();
        for (SkillDefinition skill : skillDefinitions) {
            Objects.requireNonNull(skill, "skill");
            discovered.put(skill.id(), skill);
        }
        return immutable(discovered);
    }

    private static Map<String, SkillDefinition> immutable(
            Map<String, SkillDefinition> skillDefinitions) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(skillDefinitions));
    }
}
