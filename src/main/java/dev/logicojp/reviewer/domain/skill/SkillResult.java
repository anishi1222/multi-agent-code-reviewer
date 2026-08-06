package dev.logicojp.reviewer.domain.skill;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/// Result of a skill execution.
public record SkillResult(
    String skillId,
    boolean success,
    String content,
    String errorMessage,
    Instant timestamp
) {

    public SkillResult {
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    /// Creates a successful result.
    public static SkillResult success(String skillId, String content) {
        return success(skillId, content, Clock.systemUTC());
    }

    /// Creates a successful result with a custom clock.
    public static SkillResult success(String skillId, String content, Clock clock) {
        return new SkillResult(skillId, true, content, null, Instant.now(clock));
    }

    /// Creates a failure result.
    public static SkillResult failure(String skillId, String errorMessage) {
        return failure(skillId, errorMessage, Clock.systemUTC());
    }

    /// Creates a failure result with a custom clock.
    public static SkillResult failure(String skillId, String errorMessage, Clock clock) {
        return new SkillResult(skillId, false, null, errorMessage, Instant.now(clock));
    }
}
