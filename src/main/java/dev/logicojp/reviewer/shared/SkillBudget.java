package dev.logicojp.reviewer.shared;

/// Character budget governing how much assigned-skill guidance may be rendered into an
/// agent's instruction prompt.
///
/// This is the **pure** carrier of the budget value. It is read by `domain` prompt builders,
/// so per ADR-0006 Rule 1 (domain purity) it carries no framework annotations. The configured
/// value reaches it through `infrastructure.parsing.AgentConfigLoader`, which maps
/// `reviewer.skills.max-parameter-value-length` onto it and attaches it to the
/// [dev.logicojp.reviewer.domain.agent.AgentConfig] it produces.
///
/// ## Why this type exists (F4)
///
/// `AgentPromptBuilder` previously compared the rendered skill section against
/// [ConfigDefaults#SKILL_MAX_PARAMETER_VALUE_LENGTH] **directly**. That was a *static limit
/// read* from `domain`, which meant:
///
/// - raising the configured `reviewer.skills.max-parameter-value-length` moved the loader's
///   admission gates but **not** the builder's ceiling, so the documented remedy for a
///   "skipping skill" warning turned a graceful skip into an `IllegalStateException`; and
/// - `domain` depended on a `shared` constant rather than on an injected value.
///
/// Carrying the budget as a value instance fixes both. It is the same move that introduced
/// [PromptBudget] for the prompt-compaction budgets — this is that problem one layer over.
///
/// ## Naming
///
/// The field is named for **what it measures and in what unit**, deliberately: the *rendered*
/// "Assigned Review Skills" section, counted in UTF-16 characters. It is **not** interchangeable
/// with the loader's per-file byte gate, its per-skill content gate, or its cumulative
/// assigned-content gate, even though all four currently derive from the same configured knob.
/// See [ConfigDefaults#SKILL_MAX_PARAMETER_VALUE_LENGTH] for the full consumer table.
///
/// @param renderedSkillSectionMaxChars maximum length, in UTF-16 characters, of the rendered
///                                     "Assigned Review Skills" section appended to an agent's
///                                     instruction
public record SkillBudget(int renderedSkillSectionMaxChars) {

    /// Default ceiling for the rendered assigned-skill section.
    ///
    /// Sourced from [ConfigDefaults#SKILL_MAX_PARAMETER_VALUE_LENGTH] so that an unconfigured
    /// deployment behaves exactly as it did before this type existed.
    public static final int DEFAULT_RENDERED_SKILL_SECTION_MAX_CHARS =
        ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH;

    /// Normalises the budget: a non-positive value falls back to the default.
    /// Mirrors [PromptBudget]'s compact-constructor behaviour.
    public SkillBudget {
        renderedSkillSectionMaxChars = ConfigDefaults.defaultIfNonPositive(
            renderedSkillSectionMaxChars, DEFAULT_RENDERED_SKILL_SECTION_MAX_CHARS);
    }

    /// All-defaults budget, used when no configuration has been supplied.
    public SkillBudget() {
        this(DEFAULT_RENDERED_SKILL_SECTION_MAX_CHARS);
    }
}
