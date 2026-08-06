package dev.logicojp.reviewer.application.port.inbound;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.review.LocalFileSelectionConfig;
import dev.logicojp.reviewer.domain.review.ReviewTarget;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// Domain DTO that carries all parameters needed to run a code review.
///
/// Presentation layer (CLI commands) populates this from flags/config and
/// passes it to {@link RunReviewPort}. Credentials (tokens) must be resolved
/// before construction and stored as opaque strings — they are not exposed as
/// typed domain objects.
///
/// @param target             what to review (GitHub repo or local dir)
/// @param agents             agents to run (in order)
/// @param parallelism        max concurrent agent executions (≥ 1)
/// @param outputDir          directory for report output
/// @param focusAreas         optional focus area hints (may be empty)
/// @param localFileConfig    configuration for local file selection (null if not a local review)
/// @param rubberDuck         whether to run rubber-duck dialogue after review
/// @param githubToken        resolved GitHub token (empty string for local reviews)
/// @param invocationTimestamp timestamp string set at CLI startup for session correlation
/// @param reasoningEffort    optional reasoning effort override (null = use configured default)
/// @param noSharedSession    whether to disable shared Copilot sessions
/// @param noSummary          whether to skip AI executive summary generation
public record ReviewRequest(
    ReviewTarget target,
    List<AgentConfig> agents,
    int parallelism,
    Path outputDir,
    List<String> focusAreas,
    LocalFileSelectionConfig localFileConfig,
    boolean rubberDuck,
    String githubToken,
    String invocationTimestamp,
    String reasoningEffort,
    boolean noSharedSession,
    boolean noSummary
) {

    public ReviewRequest {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(agents, "agents must not be null");
        if (agents.isEmpty()) {
            throw new IllegalArgumentException("at least one agent is required");
        }
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be at least 1, got: " + parallelism);
        }
        Objects.requireNonNull(outputDir, "outputDir must not be null");
        agents = List.copyOf(agents);
        focusAreas = focusAreas != null ? List.copyOf(focusAreas) : List.of();
        githubToken = githubToken != null ? githubToken : "";
        invocationTimestamp = invocationTimestamp != null ? invocationTimestamp : "unknown";
    }

    /// Creates a minimal review request with default options.
    public static ReviewRequest of(ReviewTarget target, List<AgentConfig> agents, Path outputDir) {
        return new ReviewRequest(target, agents, 1, outputDir, List.of(), null, false,
            "", "unknown", null, false, false);
    }
}
