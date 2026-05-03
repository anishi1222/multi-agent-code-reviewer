package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.skill.SkillDefinition;
import io.micronaut.core.annotation.Nullable;

import java.util.List;

/// Configuration model for a review agent.
/// Loaded from YAML files in the agents/ directory.
///
/// This record is a pure data carrier. Prompt construction logic is
/// in {@link AgentPromptBuilder}.
public record AgentConfig(
    String name,
    String displayName,
    String model,
    @Nullable String systemPrompt,
    @Nullable String instruction,
    @Nullable String outputFormat,
    List<String> focusAreas,
    List<SkillDefinition> skills,
    @Nullable String peerModel,
    boolean rubberDuckEnabled,
    int dialogueRounds,
    String language
) {

    public static final String DEFAULT_LANGUAGE = "ja";
    public static final int DEFAULT_DIALOGUE_ROUNDS = 0;

    public AgentConfig(
        String name,
        String displayName,
        String model,
        @Nullable String systemPrompt,
        @Nullable String instruction,
        @Nullable String outputFormat,
        List<String> focusAreas,
        List<SkillDefinition> skills
    ) {
        this(name, displayName, model, systemPrompt, instruction, outputFormat,
            focusAreas, skills, null, false, DEFAULT_DIALOGUE_ROUNDS, DEFAULT_LANGUAGE);
    }

    public AgentConfig {
        name = name == null ? "" : name;
        displayName = (displayName == null || displayName.isBlank()) ? name : displayName;
        model = (model == null || model.isBlank()) ? ModelConfig.DEFAULT_MODEL : model;
        outputFormat = normalizeOutputFormat(outputFormat);
        focusAreas = focusAreas == null ? List.of() : List.copyOf(focusAreas);
        skills = skills == null ? List.of() : List.copyOf(skills);
        peerModel = (peerModel != null && peerModel.isBlank()) ? null : peerModel;
        language = (language == null || language.isBlank()) ? DEFAULT_LANGUAGE : language;
    }

    public AgentConfig withModel(String overrideModel) {
        return Builder.from(this)
            .model(overrideModel)
            .build();
    }

    public AgentConfig withSkills(List<SkillDefinition> newSkills) {
        return Builder.from(this)
            .skills(newSkills)
            .build();
    }

    public AgentConfig withPeerModel(String overridePeerModel) {
        return Builder.from(this)
            .peerModel(overridePeerModel)
            .build();
    }

    public AgentConfig withRubberDuckEnabled(boolean enabled) {
        return Builder.from(this)
            .rubberDuckEnabled(enabled)
            .build();
    }

    public AgentConfig withDialogueRounds(int rounds) {
        return Builder.from(this)
            .dialogueRounds(rounds)
            .build();
    }

    /// Resolves the effective dialogue rounds, using the agent-level override if positive,
    /// otherwise falling back to the global RubberDuckConfig default.
    public int effectiveDialogueRounds(dev.logicojp.reviewer.config.RubberDuckConfig rubberDuckConfig) {
        if (dialogueRounds > 0) {
            return dialogueRounds;
        }
        return rubberDuckConfig.dialogueRounds();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String displayName;
        private String model;
        private String systemPrompt;
        private String instruction;
        private String outputFormat;
        private List<String> focusAreas;
        private List<SkillDefinition> skills;
        private String peerModel;
        private boolean rubberDuckEnabled;
        private int dialogueRounds;
        private String language = DEFAULT_LANGUAGE;

        private Builder() {
        }

        public static Builder from(AgentConfig source) {
            return new Builder()
                .name(source.name)
                .displayName(source.displayName)
                .model(source.model)
                .systemPrompt(source.systemPrompt)
                .instruction(source.instruction)
                .outputFormat(source.outputFormat)
                .focusAreas(source.focusAreas)
                .skills(source.skills)
                .peerModel(source.peerModel)
                .rubberDuckEnabled(source.rubberDuckEnabled)
                .dialogueRounds(source.dialogueRounds)
                .language(source.language);
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder instruction(String instruction) {
            this.instruction = instruction;
            return this;
        }

        public Builder outputFormat(String outputFormat) {
            this.outputFormat = outputFormat;
            return this;
        }

        public Builder focusAreas(List<String> focusAreas) {
            this.focusAreas = focusAreas;
            return this;
        }

        public Builder skills(List<SkillDefinition> skills) {
            this.skills = skills;
            return this;
        }

        public Builder peerModel(String peerModel) {
            this.peerModel = peerModel;
            return this;
        }

        public Builder rubberDuckEnabled(boolean rubberDuckEnabled) {
            this.rubberDuckEnabled = rubberDuckEnabled;
            return this;
        }

        public Builder dialogueRounds(int dialogueRounds) {
            this.dialogueRounds = dialogueRounds;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public AgentConfig build() {
            return new AgentConfig(name, displayName, model, systemPrompt, instruction, outputFormat,
                focusAreas, skills, peerModel, rubberDuckEnabled, dialogueRounds, language);
        }
    }

    /// Validates required fields. Delegates to {@link AgentConfigValidator}.
    public void validateRequired() {
        AgentConfigValidator.validateRequired(this);
    }

    private static String normalizeOutputFormat(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("##")) {
            return trimmed;
        }
        return "## Output Format\n\n" + trimmed;
    }

    @Override
    public String toString() {
        return "AgentConfig{name='" + name + "', displayName='" + displayName + "'}";
    }
}
