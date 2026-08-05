package dev.logicojp.reviewer.domain.skill;

import dev.logicojp.reviewer.domain.instruction.CustomInstructionSafetyValidator;
import dev.logicojp.reviewer.shared.PlaceholderUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Defines a skill that an agent can perform.
/// Skills are discrete capabilities that can be invoked by name.
///
/// This is a pure domain type — only {@code java.*}, {@code shared.*}, and
/// {@code domain.*} imports are used.
public record SkillDefinition(
    String id,
    String name,
    String description,
    String prompt,
    List<SkillParameter> parameters,
    Map<String, String> metadata
) {

    public SkillDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Skill id is required");
        }
        if (name == null || name.isBlank()) {
            name = id;
        }
        if (description == null) {
            description = "";
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Skill prompt is required");
        }
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /// Creates a skill with minimal required fields.
    public static SkillDefinition of(String id, String name, String description, String prompt) {
        return new SkillDefinition(id, name, description, prompt, List.of(), Map.of());
    }

    /// Builds the prompt with parameter substitution in a single pass.
    /// Parameter values exceeding {@code maxParameterValueLength} characters are rejected
    /// to mitigate prompt injection and resource exhaustion.
    public String buildPrompt(Map<String, String> parameterValues, int maxParameterValueLength) {
        Map<String, String> resolvedValues = new HashMap<>();
        for (SkillParameter param : parameters) {
            String value = parameterValues.getOrDefault(param.name(), param.defaultValue());
            if (value != null) {
                if (value.length() > maxParameterValueLength) {
                    throw new IllegalArgumentException(
                        "Parameter value too long for: " + param.name());
                }
                if (CustomInstructionSafetyValidator.containsSuspiciousPattern(value)) {
                    throw new IllegalArgumentException(
                        "Parameter value contains suspicious pattern for: " + param.name());
                }
                resolvedValues.put(param.name(), value);
            }
        }
        return PlaceholderUtils.replaceDollarPlaceholders(prompt, resolvedValues);
    }

    /// Validates that all required parameters are provided.
    public void validateParameters(Map<String, String> parameterValues) {
        for (SkillParameter param : parameters) {
            if (param.required() && !parameterValues.containsKey(param.name())) {
                throw new IllegalArgumentException("Missing required parameter: " + param.name());
            }
        }
    }
}
