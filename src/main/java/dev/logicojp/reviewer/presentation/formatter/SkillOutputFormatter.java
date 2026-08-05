package dev.logicojp.reviewer.presentation.formatter;

import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import dev.logicojp.reviewer.domain.skill.SkillParameter;
import dev.logicojp.reviewer.presentation.CliOutput;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;

@Singleton
public class SkillOutputFormatter {

    private final CliOutput output;

    @Inject
    public SkillOutputFormatter(CliOutput output) {
        this.output = output;
    }

    public void printAvailableSkills(Collection<SkillDefinition> skills) {
        output.println("Available Skills:\n");
        for (SkillDefinition skill : skills) {
            printSkill(skill);
        }
        if (skills.isEmpty()) {
            output.println("  No skills found.");
        }
    }

    private void printSkill(SkillDefinition skill) {
        output.println("  " + skill.id());
        output.println("    Name: " + skill.name());
        output.println("    Description: " + skill.description());
        printParameters(skill);
        output.println("");
    }

    private void printParameters(SkillDefinition skill) {
        if (skill.parameters().isEmpty()) {
            return;
        }
        output.println("    Parameters:");
        for (var param : skill.parameters()) {
            output.println("      - " + formatParameter(param));
        }
    }

    private String formatParameter(SkillParameter param) {
        String required = param.required() ? " (required)" : "";
        return param.name() + ": " + param.description() + required;
    }
}
