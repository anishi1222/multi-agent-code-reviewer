package dev.logicojp.reviewer.application.skill;

import dev.logicojp.reviewer.application.port.outbound.ManageSkillCatalogPort;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/// Mutable hand-written catalog fake shared by application skill tests.
final class TestSkillCatalog implements ManageSkillCatalogPort {

    private List<SkillDefinition> skills;

    private TestSkillCatalog(List<SkillDefinition> skills) {
        this.skills = List.copyOf(skills);
    }

    static TestSkillCatalog of(SkillDefinition... skills) {
        return new TestSkillCatalog(List.of(skills));
    }

    @Override
    public void replaceAll(Collection<SkillDefinition> discoveredSkills) {
        skills = List.copyOf(discoveredSkills);
    }

    @Override
    public Optional<SkillDefinition> findById(String skillId) {
        return skills.stream().filter(skill -> skill.id().equals(skillId)).findFirst();
    }

    @Override
    public List<SkillDefinition> listAll() {
        return skills;
    }
}
