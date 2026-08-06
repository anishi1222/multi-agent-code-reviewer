package dev.logicojp.reviewer.domain.skill;

/// Defines a parameter for a skill.
public record SkillParameter(
    String name,
    String description,
    String type,
    boolean required,
    String defaultValue
) {

    public SkillParameter {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Parameter name is required");
        }
        if (type == null || type.isBlank()) {
            type = "string";
        }
        if (description == null) {
            description = "";
        }
    }

    /// Creates a required parameter.
    public static SkillParameter required(String name, String description) {
        return new SkillParameter(name, description, "string", true, null);
    }

    /// Creates an optional parameter with a default value.
    public static SkillParameter optional(String name, String description, String defaultValue) {
        return new SkillParameter(name, description, "string", false, defaultValue);
    }
}
