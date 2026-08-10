package dev.logicojp.reviewer.domain.agent;

import java.util.Objects;

/// The set of limits that apply to one agent definition, selected by where the definition
/// came from (ADR-0007 D2).
///
/// ## Why this is a value rather than a set of `if (source == ...)` branches
///
/// Before ADR-0007 the strict limits existed as constants in
/// `CustomInstructionSafetyValidator` and the permissive ones in [AgentDefinitionPolicy].
/// Nothing selected between them, so the strict set was dead code (SEC-H1) and every
/// definition — including one written by the repository under review — was measured against
/// the permissive set (SEC-H2). Two homes for one decision is what let them drift apart
/// unnoticed.
///
/// Making the profile a value has three consequences that matter:
///
/// 1. **The choice happens once.** [#forSource(AgentSource)] is the only mapping from
///    provenance to limits. A validator that needs a bound reads it from the profile it was
///    handed; it never re-derives trust, so it cannot disagree with its callers.
/// 2. **The difference is testable directly.** A test can assert the repository profile is
///    strictly tighter than the user profile without going through a parser, which is how
///    the "both limits are actually different" invariant stays pinned.
/// ## No initialisation cycle
///
/// This record reads constants from [AgentDefinitionPolicy], which in turn calls
/// [#forSource(AgentSource)]. That is not a static-initialisation cycle: the limits are
/// `static final int` initialised with constant expressions, so they are JLS §4.12.4
/// *constant variables*. `javac` inlines their values and emits no field access, meaning
/// `AgentTrustProfile` never triggers `AgentDefinitionPolicy`'s class initialiser.
///
/// The reason to reference them anyway rather than repeat the literals is that duplicated
/// numbers are how a limit dies: the two copies drift, and the one nobody reads becomes
/// decoration. `AgentPolicyConstantsAreLiveTest` fails if any of these constants stops being
/// referenced, which is the check that would have caught SEC-H1.
///
/// 3. **A new limit has one obvious home.** Adding a bound to this record forces every
///    profile to state a value for it, instead of the bound quietly defaulting to whatever
///    the permissive path already did.
///
/// All bounds are expressed in **UTF-16 characters** (`String.length()`), not bytes. The
/// pre-existing checks already measured characters, and this repository's own definitions are
/// Japanese-heavy: re-expressing the same numbers as bytes would silently tighten them by
/// roughly 3× for CJK text and reject definitions that are legal today. Characters are also
/// the better proxy for the thing these limits actually protect — prompt length sent to a
/// model. [AgentTrustProfileTest] pins this choice with a multi-byte fixture so it cannot be
/// changed by accident.
///
/// @param maxFileChars                 ceiling on the whole definition file
/// @param maxInstructionChars          ceiling on each free-text field (system prompt,
///                                     instruction, output format)
/// @param maxInstructionLines          ceiling on line count of those same fields
/// @param enforcesCharset              whether free-text fields must consist only of
///                                     characters in the allowed ranges
/// @param rejectsUnknownFrontmatterKeys whether an unrecognised frontmatter key is a
///                                     rejection (closed schema) rather than a warning
public record AgentTrustProfile(
    int maxFileChars,
    int maxInstructionChars,
    int maxInstructionLines,
    boolean enforcesCharset,
    boolean rejectsUnknownFrontmatterKeys
) {

    /// Limits for definitions the operator supplied on the command line (boundary B1).
    ///
    /// Permissive, because the operator is the person running the tool: they can already do
    /// anything the tool can do, so constraining them buys no security and only breaks
    /// legitimate long prompts.
    public static final AgentTrustProfile USER_SUPPLIED_PROFILE = new AgentTrustProfile(
        AgentDefinitionPolicy.MAX_AGENT_FILE_SIZE,
        AgentDefinitionPolicy.MAX_INSTRUCTION_SIZE,
        AgentDefinitionPolicy.MAX_INSTRUCTION_LINES,
        false,
        false);

    /// Limits for definitions found inside the repository under review (boundary B3).
    ///
    /// Strict, because this content is attacker-controlled whenever the reviewed repository
    /// is. These are the values that existed as dead constants before ADR-0007; activating
    /// them here is what closes SEC-H1.
    ///
    /// Headroom check: the 18 `.agent.md` files in this repository peak at 4,291 characters
    /// and 97 lines, so the 16 KiB / 8 KiB / 300-line bounds leave at least 1.8× headroom and
    /// no existing definition is affected.
    public static final AgentTrustProfile REPOSITORY_SUPPLIED_PROFILE = new AgentTrustProfile(
        AgentDefinitionPolicy.MAX_UNTRUSTED_AGENT_FILE_SIZE,
        AgentDefinitionPolicy.MAX_UNTRUSTED_INSTRUCTION_SIZE,
        AgentDefinitionPolicy.MAX_INSTRUCTION_LINES,
        true,
        true);

    /// Selects the profile for a provenance.
    ///
    /// This is deliberately the only mapping from [AgentSource] to limits in the codebase.
    ///
    /// @param source provenance of the directory the definition was found in; null is
    ///               treated as untrusted, matching [AgentSource#defaultWhenUnknown()]
    /// @return the profile to validate against
    public static AgentTrustProfile forSource(AgentSource source) {
        AgentSource resolved = source == null ? AgentSource.defaultWhenUnknown() : source;
        return resolved == AgentSource.USER_SUPPLIED ? USER_SUPPLIED_PROFILE : REPOSITORY_SUPPLIED_PROFILE;
    }

    public AgentTrustProfile {
        if (maxFileChars <= 0 || maxInstructionChars <= 0 || maxInstructionLines <= 0) {
            throw new IllegalArgumentException("trust profile limits must be positive");
        }
    }

    /// Describes this profile for inclusion in a rejection reason, so an operator reading
    /// "rejected" can tell which of the two rule sets was applied.
    ///
    /// @param source the provenance this profile was selected for
    /// @return short human-readable label
    public String describe(AgentSource source) {
        return Objects.toString(source, "UNKNOWN");
    }
}
