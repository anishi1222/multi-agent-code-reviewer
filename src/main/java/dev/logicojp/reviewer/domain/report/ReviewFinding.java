package dev.logicojp.reviewer.domain.report;

/// A single extracted finding from an agent review result.
///
/// Promoted to a top-level type from the former {@code FindingsExtractor.Finding} inner record
/// to break the cycle-9 mutual import between {@code FindingsExtractor} and
/// {@code FindingsSummaryFormatter}.
///
/// Invariant: all fields are non-null; prefer empty-string over null.
public record ReviewFinding(
    String title,
    String priority,
    String agent,
    String category,
    String summary,
    String location
) {

    /// Backward-compatible 4-arg form for callers that carry no structured detail.
    public ReviewFinding(String title, String priority, String agent, String category) {
        this(title, priority, agent, category, "", "");
    }

    public ReviewFinding {
        title    = title    != null ? title    : "";
        priority = priority != null ? priority : "";
        agent    = agent    != null ? agent    : "";
        category = category != null ? category : "";
        summary  = summary  != null ? summary  : "";
        location = location != null ? location : "";
    }
}
