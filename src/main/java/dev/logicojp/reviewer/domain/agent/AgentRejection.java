package dev.logicojp.reviewer.domain.agent;

import java.util.Objects;

/// One agent definition that was refused by [AgentDefinitionPolicy], recorded so the run
/// can report *what* it dropped rather than silently loading fewer agents (ADR-0007 D4).
///
/// ## Why rejections are a value and not just a log call
///
/// Before ADR-0007 a refused definition produced a `logger.warn` and nothing else. Nobody
/// counted them, so "the review used 3 agents instead of 4" was indistinguishable from
/// "this repository only defines 3 agents" — the failure was invisible at exactly the
/// moment it mattered, namely when a hostile repository got a definition dropped. Making
/// the rejection a value lets the loader count them, lets the run print a summary that is
/// always visible, and lets tests assert on the outcome instead of on log text.
///
/// @param filename name of the definition file that was refused
/// @param source   provenance of the directory it was found in; part of the reason text
///                 because the same file is legal under one profile and illegal under the
///                 other, and an operator reading the message needs to know which applied
/// @param ruleId   identifier of the rule that fired, e.g. `AGENT-FIELD-SIZE`
/// @param reason   human-readable explanation, including the observed and permitted values
public record AgentRejection(String filename, AgentSource source, String ruleId, String reason) {

    public AgentRejection {
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(source, "source");
        ruleId = (ruleId == null || ruleId.isBlank()) ? "AGENT-UNSPECIFIED" : ruleId;
        reason = reason == null ? "" : reason;
    }

    /// Single-line rendering used by the loader's per-file warning and by the end-of-run
    /// summary. Always names both the rule and the provenance, as ADR-0007 D4 requires.
    ///
    /// @return `[RULE-ID] filename (SOURCE): reason`
    public String describe() {
        return "[%s] %s (%s): %s".formatted(ruleId, filename, source, reason);
    }
}
