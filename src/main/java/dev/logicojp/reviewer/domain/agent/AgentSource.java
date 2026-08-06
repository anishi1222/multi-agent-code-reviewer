package dev.logicojp.reviewer.domain.agent;

/// Provenance of an agent definition — *where the bytes came from*, and therefore
/// how much the loader is allowed to trust them.
///
/// ## Why this is a type and not a boolean or a string
///
/// Before ADR-0007 D1, [dev.logicojp.reviewer.application.port.inbound.LoadAgentPort]
/// accepted a bare `List<Path>`. The composition root merged two lineages with very
/// different trust properties into that one list, and from that moment on **no
/// downstream component could tell them apart**. Every validator therefore had to
/// apply a single uniform limit, which is exactly why the stricter untrusted limits
/// declared in `CustomInstructionSafetyValidator` were never wired to anything
/// (SEC-H1) and why repository-controlled definitions were validated as leniently as
/// operator-controlled ones (SEC-H2).
///
/// Carrying provenance in a type makes "which limit applies here?" answerable at
/// every point in the chain, and makes the omission of the question a compile error
/// rather than a silent default.
///
/// ## The two lineages
///
/// - [#USER_SUPPLIED] — the operator named the directory explicitly on the command
///   line (`--agents-dir`). The value can only originate from `argv`; the repository
///   under review cannot influence it. This is trust boundary **B1**.
/// - [#REPOSITORY_SUPPLIED] — the directory was discovered relative to the current
///   working directory via `AgentPathConfig.DEFAULT_DIRECTORIES` (`./agents`,
///   `./.github/agents`). Its contents are part of the repository being reviewed and
///   are therefore **attacker-controlled** whenever the tool is pointed at a
///   repository the operator does not own. This is trust boundary **B3**.
///
/// ## Invariants
///
/// - Provenance is assigned once, at the composition root, and is never recomputed
///   or overridden downstream.
/// - There is deliberately **no** CLI option, configuration key, or API that promotes
///   [#REPOSITORY_SUPPLIED] to [#USER_SUPPLIED]. ADR-0007 rejects that as
///   Alternative 1: it would reintroduce SEC-H2 through a supported switch.
/// - When provenance is unknown, callers must default to [#REPOSITORY_SUPPLIED].
///   See [#defaultWhenUnknown()].
///
/// @see AgentSourceDirectory
/// @see AgentTrustProfile
public enum AgentSource {

    /// Directory named explicitly by the operator on the command line (`--agents-dir`).
    /// Trust boundary B1. Validated against the lenient profile.
    USER_SUPPLIED,

    /// Directory discovered under the current working directory, i.e. content of the
    /// repository under review. Trust boundary B3. Validated against the strict profile.
    REPOSITORY_SUPPLIED;

    /// The provenance to assume when a construction path does not state one.
    ///
    /// Deliberately the **more restrictive** of the two. A new call site that forgets
    /// to thread provenance therefore gets the strict profile and fails closed; the
    /// failure mode is a rejected definition, never a silently widened limit.
    ///
    /// @return [#REPOSITORY_SUPPLIED]
    public static AgentSource defaultWhenUnknown() {
        return REPOSITORY_SUPPLIED;
    }

    /// Whether this provenance is attacker-controlled when the tool is pointed at a
    /// repository the operator does not own.
    ///
    /// @return `true` for [#REPOSITORY_SUPPLIED]
    public boolean isUntrusted() {
        return this == REPOSITORY_SUPPLIED;
    }
}
