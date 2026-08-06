package dev.logicojp.reviewer.domain.agent;

import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import dev.logicojp.reviewer.shared.SkillBudget;

import java.util.List;

/// Configuration model for a review agent.
/// Loaded from YAML files in the agents/ directory.
///
/// This record is a pure data carrier. Prompt construction logic is
/// in {@code AgentPromptBuilder}. No Micronaut, SLF4J, or SDK imports.
///
/// @param skillBudget budget governing how much of [#skills()] may be rendered into the
///                    instruction prompt. Carried here — rather than read as a static constant
///                    by `AgentPromptBuilder` — because the budget bounds exactly these skills,
///                    and `domain` may not read the configured value itself (ADR-0006 Rule 1).
///                    Never null; a null argument normalises to [SkillBudget#SkillBudget()].
public record AgentConfig(
    String name,
    String displayName,
    String model,
    String systemPrompt,
    String instruction,
    String outputFormat,
    List<String> focusAreas,
    List<SkillDefinition> skills,
    String peerModel,
    boolean rubberDuckEnabled,
    int dialogueRounds,
    String language,
    SkillBudget skillBudget
) {

    /// Hardcoded last-resort default model — mirrors {@code ModelConfig.DEFAULT_MODEL}.
    public static final String DEFAULT_MODEL = "claude-sonnet-4.5";
    public static final String DEFAULT_LANGUAGE = "ja";
    public static final int DEFAULT_DIALOGUE_ROUNDS = 0;

    public AgentConfig(
        String name,
        String displayName,
        String model,
        String systemPrompt,
        String instruction,
        String outputFormat,
        List<String> focusAreas,
        List<SkillDefinition> skills
    ) {
        this(name, displayName, model, systemPrompt, instruction, outputFormat,
            focusAreas, skills, null, false, DEFAULT_DIALOGUE_ROUNDS, DEFAULT_LANGUAGE, null);
    }

    public AgentConfig {
        name = name == null ? "" : name;
        displayName = (displayName == null || displayName.isBlank()) ? name : displayName;
        model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        outputFormat = normalizeOutputFormat(outputFormat);
        focusAreas = focusAreas == null ? List.of() : List.copyOf(focusAreas);
        skills = skills == null ? List.of() : List.copyOf(skills);
        peerModel = (peerModel != null && peerModel.isBlank()) ? null : peerModel;
        language = (language == null || language.isBlank()) ? DEFAULT_LANGUAGE : language;
        skillBudget = skillBudget == null ? new SkillBudget() : skillBudget;
    }

    public AgentConfig withModel(String overrideModel) {
        return Builder.from(this).model(overrideModel).build();
    }

    /// Validates that required fields are non-blank.
    ///
    /// Delegates to [AgentConfigValidator], which also reports *all* missing fields at once and
    /// warns about incomplete `outputFormat` sections.
    ///
    /// T013: the rewrite replaced this delegation with a weaker inline check that validated only
    /// `name`/`model` and threw [IllegalStateException]. That silently dropped the `systemPrompt`
    /// and `instruction` checks — malformed agent markdown loaded successfully and failed later
    /// with an unrelated error — and left [AgentConfigValidator] as dead code.
    ///
    /// @throws IllegalArgumentException if any required field is missing
    public void validateRequired() {
        AgentConfigValidator.validateRequired(this);
    }

    public AgentConfig withSkills(List<SkillDefinition> newSkills) {
        return Builder.from(this).skills(newSkills).build();
    }

    /// Returns a copy carrying the given rendered-skill-section budget.
    ///
    /// Called by `infrastructure.parsing.AgentConfigLoader` so that the configured
    /// `reviewer.skills.max-parameter-value-length` reaches `AgentPromptBuilder` as a value
    /// rather than being read there as a compile-time constant (F4).
    public AgentConfig withSkillBudget(SkillBudget newSkillBudget) {
        return Builder.from(this).skillBudget(newSkillBudget).build();
    }

    public AgentConfig withPeerModel(String overridePeerModel) {
        return Builder.from(this).peerModel(overridePeerModel).build();
    }

    public AgentConfig withRubberDuckEnabled(boolean enabled) {
        return Builder.from(this).rubberDuckEnabled(enabled).build();
    }

    public AgentConfig withDialogueRounds(int rounds) {
        return Builder.from(this).dialogueRounds(rounds).build();
    }

    /// Resolves the effective dialogue rounds.
    /// Uses the agent-level override if positive, otherwise falls back to {@code defaultRounds}.
    ///
    /// @param defaultRounds global default dialogue rounds (from rubber-duck config)
    /// @return effective number of dialogue rounds to use
    public int effectiveDialogueRounds(int defaultRounds) {
        return dialogueRounds > 0 ? dialogueRounds : defaultRounds;
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
        private SkillBudget skillBudget;

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
                .language(source.language)
                .skillBudget(source.skillBudget);
        }

        public Builder name(String name) { this.name = name; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder instruction(String instruction) { this.instruction = instruction; return this; }
        public Builder outputFormat(String outputFormat) { this.outputFormat = outputFormat; return this; }
        public Builder focusAreas(List<String> focusAreas) { this.focusAreas = focusAreas; return this; }
        public Builder skills(List<SkillDefinition> skills) { this.skills = skills; return this; }
        public Builder peerModel(String peerModel) { this.peerModel = peerModel; return this; }
        public Builder rubberDuckEnabled(boolean rubberDuckEnabled) { this.rubberDuckEnabled = rubberDuckEnabled; return this; }
        public Builder dialogueRounds(int dialogueRounds) { this.dialogueRounds = dialogueRounds; return this; }
        public Builder language(String language) { this.language = language; return this; }
        public Builder skillBudget(SkillBudget skillBudget) { this.skillBudget = skillBudget; return this; }

        public AgentConfig build() {
            return new AgentConfig(name, displayName, model, systemPrompt, instruction, outputFormat,
                focusAreas, skills, peerModel, rubberDuckEnabled, dialogueRounds, language, skillBudget);
        }
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
